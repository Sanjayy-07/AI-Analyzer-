package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.*;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

/**
 * Serves the AI-Based Architecture Risk Prediction (Novelty 3).
 *
 * GET  /risk                     -> renders risk.jsp dashboard
 * GET  /api/risk/overview        -> gets project-level risk summary and statistics
 * GET  /api/risk/components      -> gets ranked list of component risk scores
 * GET  /api/risk/component       -> gets detailed explanation for single component (?id=... or ?name=...)
 * GET  /api/risk/ablation        -> gets empirical ablation evaluation metrics & hypothesis validation
 * POST /api/risk/assess          -> re-assesses architectural risk on active project
 */
@WebServlet(name = "ArchitectureRiskServlet", urlPatterns = {"/risk", "/api/risk/*", "/api/risk"})
public class ArchitectureRiskServlet extends HttpServlet {

    private static final String SESSION_RESULT_ATTR = "lastPipelineResult";
    private final ArchitectureRiskService riskService = new ArchitectureRiskService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();

        if ("/risk".equals(servletPath)) {
            handleRiskPage(req, resp);
            return;
        }

        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null
                ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.model == null || baseline.graph == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "No active project analysis in session. Please upload and analyze a project first."
            )));
            return;
        }

        ProjectRiskOverview overview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);

        if (pathInfo == null || pathInfo.equals("") || pathInfo.equals("/")) {
            handleOverview(resp, overview);
            return;
        }

        switch (pathInfo) {
            case "/overview" -> handleOverview(resp, overview);
            case "/components" -> handleComponents(resp, overview);
            case "/component" -> handleSingleComponent(req, resp, overview);
            case "/ablation" -> handleAblation(resp, overview);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Unknown endpoint: " + pathInfo)));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null
                ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.model == null || baseline.graph == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "No active project analysis in session. Please upload a project first."
            )));
            return;
        }

        long obsTime = 0L;
        long horizon = 0L;
        try {
            String obsParam = req.getParameter("observationTimestamp");
            if (obsParam != null && !obsParam.isBlank()) obsTime = Long.parseLong(obsParam);
            String horParam = req.getParameter("predictionHorizon");
            if (horParam != null && !horParam.isBlank()) horizon = Long.parseLong(horParam);
        } catch (NumberFormatException ignored) {}

        ProjectRiskOverview overview = riskService.assessProjectRisk(
                baseline.projectRoot, baseline.model, baseline.graph, obsTime, horizon
        );

        handleOverview(resp, overview);
    }

    private void handleRiskPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null
                ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.model == null || baseline.graph == null) {
            req.setAttribute("hasProject", false);
            req.setAttribute("projectName", "No Project Selected");
        } else {
            req.setAttribute("hasProject", true);
            req.setAttribute("projectName", baseline.model.getProjectName());
            ProjectRiskOverview overview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);
            req.setAttribute("overview", overview);
        }

        req.getRequestDispatcher("/WEB-INF/jsp/risk.jsp").forward(req, resp);
    }

    private void handleOverview(HttpServletResponse resp, ProjectRiskOverview overview) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectName", overview.projectName());
        map.put("gitHistoryAvailable", overview.gitHistoryAvailable());
        map.put("totalCommits", overview.totalCommits());
        map.put("totalComponents", overview.totalComponents());
        map.put("highRiskCount", overview.highRiskCount());
        map.put("mediumRiskCount", overview.mediumRiskCount());
        map.put("lowRiskCount", overview.lowRiskCount());
        map.put("averageRiskProbability", overview.averageRiskProbability());
        map.put("cycleComponentsWithHighRisk", overview.cycleComponentsWithHighRisk());
        map.put("modelDescription", overview.modelDescription());
        map.put("hasAblation", overview.ablationResult() != null);
        resp.getWriter().write(JsonUtil.toJson(map));
    }

    private void handleComponents(HttpServletResponse resp, ProjectRiskOverview overview) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ComponentRiskResult comp : overview.components()) {
            list.add(componentToMap(comp, false));
        }
        resp.getWriter().write(JsonUtil.toJson(list));
    }

    private void handleSingleComponent(HttpServletRequest req, HttpServletResponse resp, ProjectRiskOverview overview) throws IOException {
        String id = req.getParameter("id");
        if (id == null || id.isBlank()) id = req.getParameter("name");

        if (id == null || id.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Missing 'id' or 'name' parameter.")));
            return;
        }

        String target = id.trim();
        Optional<ComponentRiskResult> found = overview.components().stream()
                .filter(c -> c.componentName().equalsIgnoreCase(target) || c.componentName().endsWith("." + target))
                .findFirst();

        if (found.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Component not found: " + target)));
            return;
        }

        resp.getWriter().write(JsonUtil.toJson(componentToMap(found.get(), true)));
    }

    private void handleAblation(HttpServletResponse resp, ProjectRiskOverview overview) throws IOException {
        if (overview.ablationResult() == null) {
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "available", false,
                    "message", "Ablation study requires a Git repository with temporal variation across components."
            )));
            return;
        }

        var res = overview.ablationResult();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("available", true);
        map.put("hypothesisSupported", res.hypothesisSupported());
        map.put("summary", res.summary());
        map.put("deltaRocAucOverStatic", res.deltaRocAucOverStatic());
        map.put("deltaPrAucOverStatic", res.deltaPrAucOverStatic());
        map.put("deltaF1OverStatic", res.deltaF1OverStatic());
        map.put("deltaBrierOverStatic", res.deltaBrierOverStatic());

        map.put("baseline", metricsToMap(res.baselineMetrics()));
        map.put("staticOnly", metricsToMap(res.staticMetrics()));
        map.put("evolutionaryOnly", metricsToMap(res.evolutionaryMetrics()));
        map.put("combined", metricsToMap(res.combinedMetrics()));

        resp.getWriter().write(JsonUtil.toJson(map));
    }

    private Map<String, Object> componentToMap(ComponentRiskResult comp, boolean includeDetails) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("componentName", comp.componentName());
        m.put("riskProbability", comp.riskProbability());
        m.put("riskPercentage", comp.riskPercentage());
        m.put("riskCategory", comp.riskCategory().name());
        m.put("inCycle", comp.inCycle());
        m.put("topStaticFactors", comp.topStaticFactors());
        m.put("topEvolutionaryFactors", comp.topEvolutionaryFactors());

        List<Map<String, Object>> neighbors = new ArrayList<>();
        for (CoChangeNeighborDto n : comp.coChangeNeighbors()) {
            neighbors.add(Map.of(
                    "neighbor", n.neighborComponent(),
                    "count", n.coChangeCount(),
                    "strength", n.jaccardStrength()
            ));
        }
        m.put("coChangeNeighbors", neighbors);

        if (includeDetails) {
            m.put("staticMetrics", comp.staticMetrics());
            m.put("evolutionaryMetrics", comp.evolutionaryMetrics());
        }
        return m;
    }

    private Map<String, Object> metricsToMap(com.archanalyzer.riskprediction.evaluation.EvaluationMetrics m) {
        if (m == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("modelName", m.modelName());
        map.put("precision", Math.round(m.precision() * 1000.0) / 1000.0);
        map.put("recall", Math.round(m.recall() * 1000.0) / 1000.0);
        map.put("f1", Math.round(m.f1() * 1000.0) / 1000.0);
        map.put("rocAuc", Math.round(m.rocAuc() * 1000.0) / 1000.0);
        map.put("prAuc", Math.round(m.prAuc() * 1000.0) / 1000.0);
        map.put("brierScore", Math.round(m.brierScore() * 1000.0) / 1000.0);
        map.put("precisionAt20", Math.round(m.precisionAt20() * 1000.0) / 1000.0);
        map.put("recallAt20", Math.round(m.recallAt20() * 1000.0) / 1000.0);
        return map;
    }
}
