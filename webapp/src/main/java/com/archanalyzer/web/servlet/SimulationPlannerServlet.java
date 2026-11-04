package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.planner.*;
import com.archanalyzer.planner.PostApplicationVerifier.VerificationReport;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves the Simulation-Guided Refactoring Planner (Novelty 2).
 *
 * GET  /planner                           -> renders planner.jsp (5-step interactive workflow)
 * GET  /api/refactor/simulation/candidates -> lists auto-discovered candidate refactoring operations
 * GET  /api/refactor/simulation/plan       -> gets active plan details and operations
 * GET  /api/refactor/simulation/result     -> gets latest simulation result
 * POST /api/refactor/simulation/plan/operation -> adds an operation to the active plan
 * POST /api/refactor/simulation/plan/remove    -> removes an operation from the plan
 * POST /api/refactor/simulation/plan/reorder   -> reorders operations in the plan
 * POST /api/refactor/simulation/plan/clear     -> clears all operations from the plan
 * POST /api/refactor/simulation/simulate       -> simulates the combined plan on an isolated graph snapshot
 * POST /api/refactor/simulation/approve        -> approves the exact simulated plan version
 * POST /api/refactor/simulation/apply          -> applies approved plan, validates compiler/tests, re-analyzes
 */
@WebServlet(name = "SimulationPlannerServlet", urlPatterns = {"/planner", "/api/refactor/simulation/*"})
public class SimulationPlannerServlet extends HttpServlet {

    private static final String SESSION_RESULT_ATTR = "lastPipelineResult";
    private static final String SESSION_PLAN_ID_ATTR = "activePlannerPlanId";
    private static final Set<String> ACTIVE_PROJECT_LOCKS = ConcurrentHashMap.newKeySet();

    private final SimulationPlannerService plannerService = new SimulationPlannerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();

        if ("/planner".equals(servletPath)) {
            handlePlannerPage(req, resp);
            return;
        }

        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult baseline = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "No active project analysis in session. Please upload a project first.")));
            return;
        }

        String planId = getOrCreateSessionPlanId(session);

        if (pathInfo == null || pathInfo.equals("") || pathInfo.equals("/")) {
            resp.getWriter().write(JsonUtil.toJson(Map.of("status", "ok", "service", "SimulationPlannerService")));
            return;
        }

        switch (pathInfo) {
            case "/candidates" -> handleCandidates(resp, baseline);
            case "/plan" -> handleGetPlan(resp, planId);
            case "/result" -> handleGetResult(resp, planId);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Endpoint not found: " + pathInfo)));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult baseline = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "No active project analysis in session.")));
            return;
        }

        String planId = getOrCreateSessionPlanId(session);

        if (pathInfo == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing action path.")));
            return;
        }

        switch (pathInfo) {
            case "/plan/operation" -> handleAddOperation(req, resp, planId);
            case "/plan/remove" -> handleRemoveOperation(req, resp, planId);
            case "/plan/reorder" -> handleReorderOperations(req, resp, planId);
            case "/plan/clear" -> handleClearPlan(resp, planId);
            case "/simulate" -> handleSimulate(resp, planId, baseline);
            case "/approve" -> handleApprove(req, resp, planId);
            case "/apply" -> handleApply(req, resp, session, planId, baseline);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Endpoint not found: " + pathInfo)));
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Handlers
    // ──────────────────────────────────────────────────────────────────────────

    private void handlePlannerPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        AnalyzerPipeline.PipelineResult result = session != null
                ? (AnalyzerPipeline.PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (result == null) {
            resp.sendRedirect(req.getContextPath() + "/projects/upload?msg=noproject");
            return;
        }

        String planId = getOrCreateSessionPlanId(session);
        req.setAttribute("result", result);
        req.setAttribute("planId", planId);
        req.getRequestDispatcher("/WEB-INF/jsp/planner.jsp").forward(req, resp);
    }

    private void handleCandidates(HttpServletResponse resp, AnalyzerPipeline.PipelineResult baseline) throws IOException {
        List<RefactoringOperation> candidates = plannerService.discoverCandidateOperations(
                baseline.projectRoot, baseline.model, baseline.graph);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("totalCandidates", candidates.size());
        data.put("candidates", candidates);
        resp.getWriter().write(JsonUtil.toJson(data));
    }

    private void handleGetPlan(HttpServletResponse resp, String planId) throws IOException {
        RefactoringPlan plan = plannerService.getPlan(planId);
        if (plan == null) {
            plan = plannerService.getOrCreatePlan(planId, "Composite Plan");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("planId", plan.getPlanId());
        data.put("name", plan.getName());
        data.put("lifecycle", plan.getLifecycle().name());
        data.put("planHash", plan.getPlanHash());
        data.put("isStale", plan.isSimulationStale());
        data.put("operationsCount", plan.size());
        data.put("operations", plan.getOperations());
        resp.getWriter().write(JsonUtil.toJson(data));
    }

    private void handleGetResult(HttpServletResponse resp, String planId) throws IOException {
        PlannerSimulationResult sim = plannerService.getCachedSimulation(planId);
        if (sim == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "No simulation result cached for this plan.")));
            return;
        }
        resp.getWriter().write(JsonUtil.toJson(Map.of("status", "ok", "simulation", sim)));
    }

    private void handleAddOperation(HttpServletRequest req, HttpServletResponse resp, String planId) throws IOException {
        String typeStr = req.getParameter("type");
        String target = req.getParameter("target");
        String secondary = req.getParameter("secondary");
        String newComponent = req.getParameter("newComponent");
        String description = req.getParameter("description");

        if (typeStr == null || typeStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing operation type.")));
            return;
        }

        OperationType type;
        try {
            type = OperationType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Unknown operation type: " + typeStr)));
            return;
        }

        RefactoringOperation op = new RefactoringOperation(null, type, target, secondary, newComponent, description);
        Enumeration<String> paramNames = req.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String pName = paramNames.nextElement();
            if (!Set.of("type", "target", "secondary", "newComponent", "description").contains(pName)) {
                op.setParameter(pName, req.getParameter(pName));
            }
        }

        plannerService.addOperation(planId, op);
        handleGetPlan(resp, planId);
    }

    private void handleRemoveOperation(HttpServletRequest req, HttpServletResponse resp, String planId) throws IOException {
        String opId = req.getParameter("id");
        if (opId == null || opId.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing operation id to remove.")));
            return;
        }

        boolean removed = plannerService.removeOperation(planId, opId);
        handleGetPlan(resp, planId);
    }

    private void handleReorderOperations(HttpServletRequest req, HttpServletResponse resp, String planId) throws IOException {
        String orderParam = req.getParameter("order"); // comma-separated op IDs
        if (orderParam != null && !orderParam.isBlank()) {
            List<String> ids = Arrays.stream(orderParam.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            plannerService.reorderOperations(planId, ids);
        }
        handleGetPlan(resp, planId);
    }

    private void handleClearPlan(HttpServletResponse resp, String planId) throws IOException {
        plannerService.clearPlan(planId);
        handleGetPlan(resp, planId);
    }

    private void handleSimulate(HttpServletResponse resp, String planId, AnalyzerPipeline.PipelineResult baseline) throws IOException {
        RefactoringPlan plan = plannerService.getPlan(planId);
        if (plan == null || plan.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Plan is empty. Please add at least one operation to simulate.")));
            return;
        }

        PlannerSimulationResult result = plannerService.simulatePlan(planId, baseline.model, baseline.graph);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", result.isSuccess() ? "ok" : "blocked");
        data.put("simulation", result);
        resp.getWriter().write(JsonUtil.toJson(data));
    }

    private void handleApprove(HttpServletRequest req, HttpServletResponse resp, String planId) throws IOException {
        String hash = req.getParameter("simulationHash");
        if (hash == null || hash.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing simulationHash for plan approval.")));
            return;
        }

        try {
            plannerService.approvePlan(planId, hash);
            resp.getWriter().write(JsonUtil.toJson(Map.of("status", "ok", "message", "Plan successfully approved.")));
        } catch (IllegalStateException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", e.getMessage())));
        }
    }

    private void handleApply(HttpServletRequest req,
                             HttpServletResponse resp,
                             HttpSession session,
                             String planId,
                             AnalyzerPipeline.PipelineResult baseline) throws IOException {
        String projectKey = baseline.projectRoot.toAbsolutePath().normalize().toString();
        if (!ACTIVE_PROJECT_LOCKS.add(projectKey)) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "Another refactoring execution is currently in progress for this project."
            )));
            return;
        }

        try {
            boolean runMavenTests = "true".equalsIgnoreCase(req.getParameter("runMavenTests"));
            RefactoringOptions options = new RefactoringOptions();
            options.setRunMavenTests(runMavenTests);

            VerificationReport report = plannerService.applyPlan(planId, baseline.projectRoot, baseline, options);

            // Synchronize updated baseline in session
            session.setAttribute(SESSION_RESULT_ATTR, baseline);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", report.verified ? "ok" : "failed");
            response.put("report", report);
            resp.getWriter().write(JsonUtil.toJson(response));

        } finally {
            ACTIVE_PROJECT_LOCKS.remove(projectKey);
        }
    }

    private String getOrCreateSessionPlanId(HttpSession session) {
        String planId = (String) session.getAttribute(SESSION_PLAN_ID_ATTR);
        if (planId == null) {
            planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
            session.setAttribute(SESSION_PLAN_ID_ATTR, planId);
            plannerService.getOrCreatePlan(planId, "Refactoring Plan");
        }
        return planId;
    }
}
