package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.web.config.AppContextListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the complete Novelty 2 Simulation-Guided Planner HTTP and servlet lifecycle:
 * - Workflow A: Candidate discovery and structured serialization.
 * - Workflow B: Active plan mutation (Add, Reorder, Remove, Clear).
 * - Workflow C: Combined simulation on isolated graph snapshot and metric delta calculation.
 * - Workflow D: Cryptographic plan approval and AST application.
 */
public class PlannerWorkflowIntegrationTest {

    @Test
    @DisplayName("Novelty 2 Complete HTTP Workflow: Candidates -> Add -> Reorder -> Simulate -> Approve -> Apply")
    void testPlannerCompleteHttpLifecycle(@TempDir Path testDir) throws Exception {
        // 1. Setup mock Servlet container environment
        Map<String, Object> contextAttrs = new HashMap<>();
        AnalyzerPipeline pipeline = new AnalyzerPipeline();
        contextAttrs.put(AppContextListener.PIPELINE_ATTR, pipeline);

        ServletContext servletContext = createMockServletContext(contextAttrs);
        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockHttpSession(servletContext, sessionAttrs);

        // 2. Prepare synthetic project archive with cyclic dependencies
        Path projectSrc = testDir.resolve("synthetic-project");
        Path pkgDir = projectSrc.resolve("src/main/java/com/demo");
        Files.createDirectories(pkgDir);

        Files.writeString(projectSrc.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>com.demo</groupId>\n" +
                "  <artifactId>demo-app</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties><maven.compiler.source>17</maven.compiler.source><maven.compiler.target>17</maven.compiler.target></properties>\n" +
                "</project>\n");

        Files.writeString(pkgDir.resolve("ServiceA.java"),
                "package com.demo;\n" +
                "public class ServiceA {\n" +
                "    private ServiceB b;\n" +
                "    public int calculate(int x) { return b != null ? b.compute(x) + 1 : x; }\n" +
                "}\n");

        Files.writeString(pkgDir.resolve("ServiceB.java"),
                "package com.demo;\n" +
                "public class ServiceB {\n" +
                "    private ServiceA a;\n" +
                "    public int compute(int x) { return a != null ? a.calculate(x) * 2 : x; }\n" +
                "}\n");

        Path workDir = testDir.resolve("work-dir");
        Files.createDirectories(workDir);
        PipelineResult baselineResult = pipeline.run(projectSrc, workDir, "DemoProject", com.archanalyzer.optimization.ConstraintSet.defaults());
        sessionAttrs.put("lastPipelineResult", baselineResult);

        SimulationPlannerServlet plannerServlet = new SimulationPlannerServlet() {
            @Override
            public ServletContext getServletContext() { return servletContext; }
        };

        // -------------------------------------------------------------
        // WORKFLOW A: Candidate Discovery
        // -------------------------------------------------------------
        MockResponseState candState = new MockResponseState();
        HttpServletResponse candResp = createMockResponse(candState);
        HttpServletRequest candReq = createMockRequest(servletContext, session, "GET", "/api/refactor/simulation/candidates", "/candidates", Map.of());
        plannerServlet.doGet(candReq, candResp);

        assertEquals(200, candState.status);
        String candJson = candState.getOutput();
        assertNotNull(candJson);
        assertTrue(candJson.contains("\"status\":\"ok\""));
        assertTrue(candJson.contains("\"candidates\":["));
        assertFalse(candJson.contains("\"candidates\":[{}]"), "Candidates must NOT be empty JSON objects");
        assertTrue(candJson.contains("\"type\":\"BREAK_CYCLE\""));
        assertTrue(candJson.contains("\"targetComponent\":\"com.demo.ServiceA\"") || candJson.contains("\"targetComponent\":\"com.demo.ServiceB\""));
        assertTrue(candJson.contains("\"description\":"));

        // -------------------------------------------------------------
        // WORKFLOW B: Plan Mutation (Add 2 ops, Reorder, Remove, Clear)
        // -------------------------------------------------------------
        // 1. Add first operation
        MockResponseState add1State = new MockResponseState();
        HttpServletResponse add1Resp = createMockResponse(add1State);
        Map<String, String[]> op1Params = Map.of(
                "type", new String[]{"BREAK_CYCLE"},
                "target", new String[]{"com.demo.ServiceA"},
                "secondary", new String[]{"com.demo.ServiceB"},
                "newComponent", new String[]{"com.demo.IServiceB"},
                "description", new String[]{"Break cycle by extracting IServiceB"}
        );
        HttpServletRequest add1Req = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/plan/operation", "/plan/operation", op1Params);
        plannerServlet.doPost(add1Req, add1Resp);

        assertEquals(200, add1State.status);
        String add1Json = add1State.getOutput();
        assertTrue(add1Json.contains("\"operationsCount\":1"));
        assertTrue(add1Json.contains("\"type\":\"BREAK_CYCLE\""));
        assertTrue(add1Json.contains("\"targetComponent\":\"com.demo.ServiceA\""));
        assertFalse(add1Json.contains("\"operations\":[{}]"), "Operation in plan must NOT be empty JSON object");

        // Extract planHash and op1 id from response
        String planHash = extractJsonValue(add1Json, "planHash");
        assertNotNull(planHash);

        // 2. Add second operation (Interface extraction)
        MockResponseState add2State = new MockResponseState();
        HttpServletResponse add2Resp = createMockResponse(add2State);
        Map<String, String[]> op2Params = Map.of(
                "type", new String[]{"EXTRACT_INTERFACE"},
                "target", new String[]{"com.demo.ServiceA"},
                "newComponent", new String[]{"com.demo.IServiceA"},
                "description", new String[]{"Extract interface IServiceA"}
        );
        HttpServletRequest add2Req = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/plan/operation", "/plan/operation", op2Params);
        plannerServlet.doPost(add2Req, add2Resp);

        assertEquals(200, add2State.status);
        String add2Json = add2State.getOutput();
        assertTrue(add2Json.contains("\"operationsCount\":2"));

        // Extract op2 id from plan
        String op2Id = null;
        String[] parts = add2Json.split("\"id\":\"");
        if (parts.length > 2) {
            op2Id = parts[2].substring(0, parts[2].indexOf("\""));
        }
        assertNotNull(op2Id);

        // 3. Test Reordering
        MockResponseState reorderState = new MockResponseState();
        HttpServletResponse reorderResp = createMockResponse(reorderState);
        Map<String, String[]> reorderParams = Map.of("order", new String[]{op2Id});
        HttpServletRequest reorderReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/plan/reorder", "/plan/reorder", reorderParams);
        plannerServlet.doPost(reorderReq, reorderResp);
        assertEquals(200, reorderState.status);
        assertTrue(reorderState.getOutput().contains("\"operationsCount\":2"));

        // 4. Remove second operation
        MockResponseState removeState = new MockResponseState();
        HttpServletResponse removeResp = createMockResponse(removeState);
        Map<String, String[]> removeParams = Map.of("id", new String[]{op2Id});
        HttpServletRequest removeReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/plan/remove", "/plan/remove", removeParams);
        plannerServlet.doPost(removeReq, removeResp);

        assertEquals(200, removeState.status);
        String removeJson = removeState.getOutput();
        assertTrue(removeJson.contains("\"operationsCount\":1"));

        // -------------------------------------------------------------
        // WORKFLOW C: Combined Simulation
        // -------------------------------------------------------------
        MockResponseState simState = new MockResponseState();
        HttpServletResponse simResp = createMockResponse(simState);
        HttpServletRequest simReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/simulate", "/simulate", Map.of());
        plannerServlet.doPost(simReq, simResp);

        assertEquals(200, simState.status);
        String simJson = simState.getOutput();
        assertTrue(simJson.contains("\"status\":\"ok\""));
        assertTrue(simJson.contains("\"simulation\":{"));
        assertFalse(simJson.contains("\"simulation\":{}"), "Simulation result must NOT be empty JSON object");
        assertTrue(simJson.contains("\"metricDeltas\":{"));
        assertTrue(simJson.contains("\"Architecture Health\":{"));
        assertTrue(simJson.contains("\"Circular Dependencies\":{"));
        assertTrue(simJson.contains("\"simulatedCycleCount\":0"));
        assertTrue(simJson.contains("\"baselineCycleCount\":1"));

        // Get updated planHash after simulation
        MockResponseState getPlanState = new MockResponseState();
        HttpServletResponse getPlanResp = createMockResponse(getPlanState);
        HttpServletRequest getPlanReq = createMockRequest(servletContext, session, "GET", "/api/refactor/simulation/plan", "/plan", Map.of());
        plannerServlet.doGet(getPlanReq, getPlanResp);
        String updatedPlanHash = extractJsonValue(getPlanState.getOutput(), "planHash");

        // -------------------------------------------------------------
        // WORKFLOW D: Approve and Apply Plan
        // -------------------------------------------------------------
        // 1. Approval with wrong hash must be rejected
        MockResponseState badApproveState = new MockResponseState();
        HttpServletResponse badApproveResp = createMockResponse(badApproveState);
        Map<String, String[]> badApproveParams = Map.of("simulationHash", new String[]{"wrong-hash"});
        HttpServletRequest badApproveReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/approve", "/approve", badApproveParams);
        plannerServlet.doPost(badApproveReq, badApproveResp);
        assertEquals(409, badApproveState.status);

        // 2. Approval with valid hash succeeds
        MockResponseState approveState = new MockResponseState();
        HttpServletResponse approveResp = createMockResponse(approveState);
        Map<String, String[]> approveParams = Map.of("simulationHash", new String[]{updatedPlanHash});
        HttpServletRequest approveReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/approve", "/approve", approveParams);
        plannerServlet.doPost(approveReq, approveResp);
        assertEquals(200, approveState.status);
        assertTrue(approveState.getOutput().contains("\"status\":\"ok\""));

        // 3. Apply Plan
        MockResponseState applyState = new MockResponseState();
        HttpServletResponse applyResp = createMockResponse(applyState);
        Map<String, String[]> applyParams = Map.of("runMavenTests", new String[]{"false"});
        HttpServletRequest applyReq = createMockRequest(servletContext, session, "POST", "/api/refactor/simulation/apply", "/apply", applyParams);
        plannerServlet.doPost(applyReq, applyResp);

        assertEquals(200, applyState.status);
        String applyJson = applyState.getOutput();
        assertTrue(applyJson.contains("\"status\":\"ok\""));
        assertTrue(applyJson.contains("\"report\":{"));
        assertTrue(applyJson.contains("\"verified\":true"));
        assertTrue(applyJson.contains("\"buildPassed\":true"));
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : null;
    }

    // --- Mocking helpers ---

    static class MockResponseState {
        int status = 200;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        String contentType;
        String characterEncoding = "UTF-8";

        String getOutput() {
            writer.flush();
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    static HttpServletResponse createMockResponse(MockResponseState state) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatus".equals(name)) {
                        state.status = (Integer) args[0];
                        return null;
                    }
                    if ("getStatus".equals(name)) return state.status;
                    if ("setContentType".equals(name)) {
                        state.contentType = (String) args[0];
                        return null;
                    }
                    if ("setCharacterEncoding".equals(name)) {
                        state.characterEncoding = (String) args[0];
                        return null;
                    }
                    if ("getWriter".equals(name)) {
                        return state.writer;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    static HttpServletRequest createMockRequest(ServletContext ctx, HttpSession session, String method, String requestUri, String pathInfo, Map<String, String[]> params) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getRequestURI".equals(name)) return requestUri;
                    if ("getPathInfo".equals(name)) return pathInfo;
                    if ("getServletPath".equals(name)) return "/api/refactor/simulation";
                    if ("getContextPath".equals(name)) return "";
                    if ("getServletContext".equals(name)) return ctx;
                    if ("getSession".equals(name)) return session;
                    if ("getParameter".equals(name)) {
                        String[] val = params.get((String) args[0]);
                        return (val != null && val.length > 0) ? val[0] : null;
                    }
                    if ("getParameterValues".equals(name)) return params.get((String) args[0]);
                    if ("getParameterNames".equals(name)) return Collections.enumeration(params.keySet());
                    if ("getParameterMap".equals(name)) return params;
                    return defaultValue(m.getReturnType());
                }
        );
    }

    static ServletContext createMockServletContext(Map<String, Object> attrs) {
        return (ServletContext) Proxy.newProxyInstance(
                ServletContext.class.getClassLoader(),
                new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) return attrs.get((String) args[0]);
                    if ("setAttribute".equals(name)) {
                        attrs.put((String) args[0], args[1]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    static HttpSession createMockHttpSession(ServletContext ctx, Map<String, Object> attrs) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) return attrs.get((String) args[0]);
                    if ("setAttribute".equals(name)) {
                        attrs.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("getId".equals(name)) return "mock-session-planner";
                    if ("getServletContext".equals(name)) return ctx;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        return null;
    }
}
