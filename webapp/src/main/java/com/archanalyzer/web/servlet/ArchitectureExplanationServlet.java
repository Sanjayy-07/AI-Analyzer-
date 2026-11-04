package com.archanalyzer.web.servlet;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.service.ArchitectureExplanationService;
import com.archanalyzer.explanation.verification.VerifiedExplanation;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.ProjectRiskOverview;
import com.archanalyzer.web.dto.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

/**
 * Controller for Novelty 4: LLM-Based Architecture Explanations with Automatic Claim Verification.
 *
 * GET  /explanation              -> renders interactive explanation & claim verification dashboard (explanation.jsp)
 * GET  /api/explanation/summary  -> returns JSON summary of verified explanations and accuracy scores
 * GET  /api/explanation/claims   -> returns JSON list of all verified claims with evidence links
 * GET  /api/explanation/evidence -> returns JSON list of authoritative ground-truth evidence register items
 * POST /api/explanation/ask      -> answers architectural questions with verified claims
 * POST /api/explanation/component-> generates verified explanation for a targeted component
 */
@WebServlet(name = "ArchitectureExplanationServlet", urlPatterns = {"/explanation", "/api/explanation/*", "/api/explanation"})
public class ArchitectureExplanationServlet extends HttpServlet {

    private static final String SESSION_RESULT_ATTR = "lastPipelineResult";
    private static final String SESSION_EXPLANATION_ATTR = "lastVerifiedExplanation";

    private final ArchitectureExplanationService explanationService = new ArchitectureExplanationService();
    private final ArchitectureRiskService riskService = new ArchitectureRiskService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();

        if ("/explanation".equals(servletPath)) {
            handleExplanationPage(req, resp);
            return;
        }

        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.model == null || baseline.graph == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "No active project analysis in session. Please upload and analyze a project first."
            )));
            return;
        }

        VerifiedExplanation explanation = getOrComputeExplanation(session, baseline);

        if (pathInfo == null || pathInfo.equals("") || pathInfo.equals("/")) {
            handleSummary(resp, explanation);
            return;
        }

        switch (pathInfo.toLowerCase()) {
            case "/summary" -> handleSummary(resp, explanation);
            case "/claims" -> handleClaims(resp, explanation);
            case "/evidence" -> handleEvidence(resp, baseline);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Unknown API endpoint: " + pathInfo)));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline == null || baseline.model == null || baseline.graph == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.toJson(Map.of(
                    "error", "No active project analysis in session. Please upload and analyze a project first."
            )));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        ProjectRiskOverview riskOverview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);

        if ("/ask".equalsIgnoreCase(pathInfo)) {
            String question = req.getParameter("question");
            if (question == null || question.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Question parameter is required.")));
                return;
            }
            VerifiedExplanation result = explanationService.answerArchitectQuestion(
                    baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components(), question);
            resp.getWriter().write(JsonUtil.toJson(result));
        } else if ("/component".equalsIgnoreCase(pathInfo)) {
            String componentId = req.getParameter("componentId");
            if (componentId == null || componentId.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "componentId parameter is required.")));
                return;
            }
            VerifiedExplanation result = explanationService.explainComponent(
                    baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components(), componentId);
            resp.getWriter().write(JsonUtil.toJson(result));
        } else {
            // Recompute explanation
            VerifiedExplanation fresh = explanationService.explainProject(
                    baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components());
            session.setAttribute(SESSION_EXPLANATION_ATTR, fresh);
            resp.getWriter().write(JsonUtil.toJson(fresh));
        }
    }

    private void handleExplanationPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        PipelineResult baseline = session != null ? (PipelineResult) session.getAttribute(SESSION_RESULT_ATTR) : null;

        if (baseline != null && baseline.model != null && baseline.graph != null) {
            VerifiedExplanation explanation = getOrComputeExplanation(session, baseline);
            ProjectRiskOverview riskOverview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);
            ArchitectureEvidence evidence = explanationService.collectEvidence(
                    baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components());

            req.setAttribute("explanation", explanation);
            req.setAttribute("evidence", evidence);
            req.setAttribute("baseline", baseline);
            req.setAttribute("riskOverview", riskOverview);
        }

        req.getRequestDispatcher("/WEB-INF/jsp/explanation.jsp").forward(req, resp);
    }

    private VerifiedExplanation getOrComputeExplanation(HttpSession session, PipelineResult baseline) {
        VerifiedExplanation cached = (VerifiedExplanation) session.getAttribute(SESSION_EXPLANATION_ATTR);
        if (cached != null) return cached;

        ProjectRiskOverview riskOverview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);
        VerifiedExplanation computed = explanationService.explainProject(
                baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components());
        session.setAttribute(SESSION_EXPLANATION_ATTR, computed);
        return computed;
    }

    private void handleSummary(HttpServletResponse resp, VerifiedExplanation exp) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("overview", exp.overview());
        map.put("verificationRate", exp.verificationRate());
        map.put("contradictionRate", exp.contradictionRate());
        map.put("totalClaims", exp.totalClaims());
        map.put("verifiedCount", exp.verifiedCount());
        map.put("partiallyVerifiedCount", exp.partiallyVerifiedCount());
        map.put("unsupportedCount", exp.unsupportedCount());
        map.put("contradictedCount", exp.contradictedCount());
        map.put("notVerifiableCount", exp.notVerifiableCount());
        map.put("modelUsed", exp.modelUsed());
        map.put("executionDurationMs", exp.executionDurationMs());
        map.put("publicationReady", exp.isPublicationReady());
        resp.getWriter().write(JsonUtil.toJson(map));
    }

    private void handleClaims(HttpServletResponse resp, VerifiedExplanation exp) throws IOException {
        resp.getWriter().write(JsonUtil.toJson(exp.verifiedClaims()));
    }

    private void handleEvidence(HttpServletResponse resp, PipelineResult baseline) throws IOException {
        ProjectRiskOverview riskOverview = riskService.assessProjectRisk(baseline.projectRoot, baseline.model, baseline.graph);
        ArchitectureEvidence evidence = explanationService.collectEvidence(
                baseline.model, baseline.graph, baseline.metricsBefore, riskOverview.components());
        resp.getWriter().write(JsonUtil.toJson(evidence.getAllItems()));
    }
}
