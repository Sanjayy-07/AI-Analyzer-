package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.refactor.CycleRefactoringService;
import com.archanalyzer.refactor.CycleRefactoringService.CycleAnalysisDto;
import com.archanalyzer.refactor.CycleRefactoringService.CycleExecutionDto;
import com.archanalyzer.refactor.CycleRefactoringService.CyclePreviewDto;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.web.config.AppContextListener;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST Servlet providing API endpoints for Novelty 1:
 * Automated Cycle-Breaking Refactoring with Verified Dependency Inversion.
 *
 * Routes:
 *   GET  /api/refactor/cycle/analyze  → analyze cyclic SCCs and rank candidate inversion edges
 *   POST /api/refactor/cycle/preview  → non-destructive preview of AST interface and caller rewrites
 *   POST /api/refactor/cycle/apply    → apply verified cycle breaking with atomic rollback protection
 */
@WebServlet(name = "CycleRefactoringServlet", urlPatterns = {"/api/refactor/cycle/*", "/api/refactor/cycle"})
public class CycleRefactoringServlet extends HttpServlet {

    private static final String SESSION_RESULT_ATTR = "lastPipelineResult";
    private static final Set<String> ACTIVE_PROJECT_LOCKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final CycleRefactoringService refactoringService = new CycleRefactoringService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null
                ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.projectRoot == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "No active project analysis found in session. Please upload and analyze a project first."
            )));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isBlank()) {
            pathInfo = "/analyze";
        }

        try {
            if (pathInfo.endsWith("/analyze")) {
                handleAnalyze(baseline, resp);
            } else if (pathInfo.endsWith("/preview")) {
                handlePreview(req, baseline, resp);
            } else if (pathInfo.endsWith("/apply")) {
                handleApply(req, session, baseline, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonUtil.toJson(Map.of(
                        "error", "Unknown cycle refactoring operation: " + pathInfo
                )));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "Cycle refactoring operation failed: " + sanitize(e.getMessage())
            )));
        }
    }

    private void handleAnalyze(PipelineResult baseline, HttpServletResponse resp) throws IOException {
        CycleAnalysisDto analysis = refactoringService.analyzeCycles(
                baseline.projectRoot, baseline.model, baseline.graph);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("totalCyclicSccs", analysis.totalCyclicSccs);
        response.put("cyclicSccs", analysis.cyclicSccs);

        resp.getWriter().write(JsonUtil.toJson(response));
    }

    private void handlePreview(HttpServletRequest req, PipelineResult baseline, HttpServletResponse resp) throws IOException {
        String from = req.getParameter("from");
        String to = req.getParameter("to");

        CyclePreviewDto preview = refactoringService.previewCycleBreaking(
                baseline.projectRoot, baseline.model, baseline.graph, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("preview", preview);

        resp.getWriter().write(JsonUtil.toJson(response));
    }

    private void handleApply(HttpServletRequest req, HttpSession session, PipelineResult baseline, HttpServletResponse resp) throws IOException {
        String projectKey = baseline.projectRoot.toAbsolutePath().normalize().toString();

        // Prevent concurrent execution against the same project root
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

            CycleExecutionDto result = refactoringService.applyCycleBreaking(baseline.projectRoot, baseline, options);

            // Synchronize updated baseline in HTTP session
            session.setAttribute(SESSION_RESULT_ATTR, baseline);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", result.success ? "ok" : "failed");
            response.put("result", result);

            resp.getWriter().write(JsonUtil.toJson(response));

        } finally {
            ACTIVE_PROJECT_LOCKS.remove(projectKey);
        }
    }

    private String sanitize(String s) {
        if (s == null) return "Unknown error";
        return s.replace('\n', ' ').replace('\r', ' ');
    }
}
