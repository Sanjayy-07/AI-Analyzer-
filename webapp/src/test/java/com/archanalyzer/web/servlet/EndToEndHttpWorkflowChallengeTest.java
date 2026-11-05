package com.archanalyzer.web.servlet;

import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.web.config.AppContextListener;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 10: Independent End-to-End HTTP Workflow Validation.
 * Validates the full HTTP API / Servlet lifecycle across:
 * 1. ZIP Upload (UploadServlet)
 * 2. Analysis API (AnalysisApiServlet /current/cycles)
 * 3. Cycle Analysis API (CycleRefactoringServlet /api/refactor/cycle/analyze)
 * 4. Cycle Refactoring Preview API (CycleRefactoringServlet /api/refactor/cycle/preview)
 * 5. Cycle Refactoring Apply API (CycleRefactoringServlet /api/refactor/cycle/apply)
 * 6. Deliverable ZIP Download (DownloadServlet /projects/download/zip)
 * 7. Independent extraction & compiler verification of downloaded ZIP
 */
public class EndToEndHttpWorkflowChallengeTest {

    @Test
    @DisplayName("Complete HTTP API lifecycle: Upload -> Analyze -> Preview -> Apply -> Download ZIP -> Verify")
    void testCompleteHttpLifecycle(@TempDir Path testDir) throws Exception {
        // 1. Setup mock Servlet container environment
        Map<String, Object> contextAttrs = new HashMap<>();
        AnalyzerPipeline pipeline = new AnalyzerPipeline();
        contextAttrs.put(AppContextListener.PIPELINE_ATTR, pipeline);

        ServletContext servletContext = createMockServletContext(contextAttrs);
        Map<String, Object> sessionAttrs = new HashMap<>();
        HttpSession session = createMockHttpSession(servletContext, sessionAttrs);

        // 2. Prepare synthetic project archive with a cyclic dependency
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

        Path zipUploadPath = testDir.resolve("demo-project.zip");
        createZip(projectSrc, zipUploadPath);
        assertTrue(Files.exists(zipUploadPath));

        // -------------------------------------------------------------
        // STEP 1: UploadServlet.doPost
        // -------------------------------------------------------------
        UploadServlet uploadServlet = new UploadServlet() {
            @Override
            public ServletContext getServletContext() { return servletContext; }
        };

        MockResponseState uploadRespState = new MockResponseState();
        HttpServletResponse uploadResp = createMockResponse(uploadRespState);

        Map<String, Part> uploadParts = new HashMap<>();
        uploadParts.put("projectArchive", new MockPart("projectArchive", "demo-project.zip", Files.readAllBytes(zipUploadPath)));

        HttpServletRequest uploadReq = createMockRequest(servletContext, session, "POST", "/projects/upload", null, Map.of(), uploadParts);

        uploadServlet.doPost(uploadReq, uploadResp);

        assertEquals(302, uploadRespState.status, "UploadServlet should redirect on success");
        AnalyzerPipeline.PipelineResult resultAfterUpload = (AnalyzerPipeline.PipelineResult) sessionAttrs.get("lastPipelineResult");
        assertNotNull(resultAfterUpload, "Session must contain lastPipelineResult after upload");
        assertTrue(resultAfterUpload.metricsBefore.cycleCount >= 1, "Baseline must detect the cyclic dependency");

        // -------------------------------------------------------------
        // STEP 2: AnalysisApiServlet.doGet (/current/cycles)
        // -------------------------------------------------------------
        AnalysisApiServlet analysisApiServlet = new AnalysisApiServlet() {
            @Override
            public ServletContext getServletContext() { return servletContext; }
        };

        MockResponseState analysisRespState = new MockResponseState();
        HttpServletResponse analysisResp = createMockResponse(analysisRespState);
        HttpServletRequest analysisReq = createMockRequest(servletContext, session, "GET", "/api/projects/current/cycles", "/current/cycles", Map.of(), Map.of());

        analysisApiServlet.doGet(analysisReq, analysisResp);

        assertEquals(200, analysisRespState.status);
        String analysisJson = analysisRespState.getContentAsString();
        // DEFECT-S1-03 REMEDIATED: JsonUtil serializes CycleAnalysisDto into structured JSON
        assertTrue(analysisJson.startsWith("{") && analysisJson.contains("\"totalCyclicSccs\":"),
                "Analysis response must be a structured JSON object: " + analysisJson);
        assertFalse(analysisJson.contains("CycleAnalysisDto@"),
                "Analysis response must not contain opaque ClassName@hash string");

        // -------------------------------------------------------------
        // STEP 3: CycleRefactoringServlet.doGet (/api/refactor/cycle/analyze)
        // -------------------------------------------------------------
        CycleRefactoringServlet cycleServlet = new CycleRefactoringServlet() {
            @Override
            public ServletContext getServletContext() { return servletContext; }
        };

        MockResponseState cycleAnalyzeRespState = new MockResponseState();
        HttpServletResponse cycleAnalyzeResp = createMockResponse(cycleAnalyzeRespState);
        HttpServletRequest cycleAnalyzeReq = createMockRequest(servletContext, session, "GET", "/api/refactor/cycle/analyze", "/analyze", Map.of(), Map.of());

        cycleServlet.doGet(cycleAnalyzeReq, cycleAnalyzeResp);

        assertEquals(200, cycleAnalyzeRespState.status);
        String cycleAnalyzeJson = cycleAnalyzeRespState.getContentAsString();
        assertTrue(cycleAnalyzeJson.contains("\"status\":\"ok\""), "Cycle analyze API must return status ok: " + cycleAnalyzeJson);
        assertTrue(cycleAnalyzeJson.contains("\"totalCyclicSccs\":1"), "Cycle analyze API must report 1 cyclic SCC");

        // -------------------------------------------------------------
        // STEP 4: CycleRefactoringServlet.doPost (/api/refactor/cycle/preview)
        // -------------------------------------------------------------
        MockResponseState previewRespState = new MockResponseState();
        HttpServletResponse previewResp = createMockResponse(previewRespState);
        HttpServletRequest previewReq = createMockRequest(servletContext, session, "POST", "/api/refactor/cycle/preview", "/preview",
                Map.of("from", "com.demo.ServiceA", "to", "com.demo.ServiceB"), Map.of());

        cycleServlet.doPost(previewReq, previewResp);

        assertEquals(200, previewRespState.status);
        String previewJson = previewRespState.getContentAsString();
        assertTrue(previewJson.contains("\"status\":\"ok\""), "Preview API must return status ok: " + previewJson);
        assertTrue(previewJson.contains("preview"), "Preview JSON must contain preview payload");

        // -------------------------------------------------------------
        // STEP 5: CycleRefactoringServlet.doPost (/api/refactor/cycle/apply)
        // -------------------------------------------------------------
        MockResponseState applyRespState = new MockResponseState();
        HttpServletResponse applyResp = createMockResponse(applyRespState);
        HttpServletRequest applyReq = createMockRequest(servletContext, session, "POST", "/api/refactor/cycle/apply", "/apply",
                Map.of("runMavenTests", "false"), Map.of());

        cycleServlet.doPost(applyReq, applyResp);

        String applyJson = applyRespState.getContentAsString();
        AnalyzerPipeline.PipelineResult updatedResult = (AnalyzerPipeline.PipelineResult) sessionAttrs.get("lastPipelineResult");
        assertEquals(200, applyRespState.status);
        assertTrue(applyJson.contains("\"status\":\"ok\""), "Apply API must return status ok: " + applyJson);
        // DEFECT-S1-03 REMEDIATED: result must be a structured JSON object, not an opaque DTO toString()
        assertTrue(applyJson.contains("\"result\":{") && applyJson.contains("\"success\":true"),
                "Apply result must be structured JSON object with fields: " + applyJson);
        assertFalse(applyJson.contains("CycleExecutionDto@"),
                "Apply result must not contain opaque ClassName@hash string");
        assertNotNull(updatedResult, "Updated pipeline result must be stored in session");
        assertNotNull(updatedResult.finalZipPath, "Updated pipeline result must produce finalZipPath");
        assertTrue(Files.exists(updatedResult.finalZipPath), "Final zip file must exist on disk: " + updatedResult.finalZipPath);

        // -------------------------------------------------------------
        // STEP 6: DownloadServlet.doGet (/projects/download/zip)
        // -------------------------------------------------------------
        DownloadServlet downloadServlet = new DownloadServlet() {
            @Override
            public ServletContext getServletContext() { return servletContext; }
        };

        MockResponseState downloadRespState = new MockResponseState();
        HttpServletResponse downloadResp = createMockResponse(downloadRespState);
        HttpServletRequest downloadReq = createMockRequest(servletContext, session, "GET", "/projects/download/zip", "/zip", Map.of(), Map.of());

        downloadServlet.doGet(downloadReq, downloadResp);

        assertEquals(200, downloadRespState.status);
        assertEquals("application/zip", downloadRespState.contentType);
        byte[] downloadedZipBytes = downloadRespState.getBinaryContent();
        assertTrue(downloadedZipBytes.length > 0, "Downloaded ZIP bytes must be non-empty");

        // -------------------------------------------------------------
        // STEP 7: Independent Extraction and Compiler Verification
        // -------------------------------------------------------------
        Path extractedDir = testDir.resolve("extracted-download");
        Files.createDirectories(extractedDir);
        extractZip(downloadedZipBytes, extractedDir);

        List<Path> javaFiles = new ArrayList<>();
        try (var walk = Files.walk(extractedDir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        assertTrue(javaFiles.size() >= 3, "Extracted project must contain ServiceA, ServiceB, and generated interface. Found: " + javaFiles);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler must be available");

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        var compilationUnits = fileManager.getJavaFileObjectsFromPaths(javaFiles);
        StringWriter compilerErrors = new StringWriter();
        boolean compileSuccess = compiler.getTask(compilerErrors, fileManager, null, List.of("-source", "17", "-target", "17"), null, compilationUnits).call();
        fileManager.close();

        assertTrue(compileSuccess, "Extracted downloaded project must compile cleanly without errors: " + compilerErrors);
    }

    @Test
    @DisplayName("Challenge & Defect Evidence: Pre-existing setter causes duplicate method collision (DEFECT-S1-04)")
    void testPreExistingSetterCollisionDefect(@TempDir Path testDir) throws Exception {
        Path projectSrc = testDir.resolve("preexisting-setter");
        Path pkgDir = projectSrc.resolve("src/main/java/com/demo");
        Files.createDirectories(pkgDir);

        Files.writeString(pkgDir.resolve("ServiceA.java"),
                "package com.demo;\n" +
                "public class ServiceA {\n" +
                "    private final ServiceB b;\n" +
                "    public ServiceA(ServiceB b) { this.b = b; }\n" +
                "    public int calculate(int x) { return b.compute(x) + 1; }\n" +
                "}\n");

        Files.writeString(pkgDir.resolve("ServiceB.java"),
                "package com.demo;\n" +
                "public class ServiceB {\n" +
                "    private ServiceA a;\n" +
                "    public ServiceB() {}\n" +
                "    public void setA(ServiceA a) { this.a = a; }\n" +
                "    public int compute(int x) { return x * 2; }\n" +
                "}\n");

        com.archanalyzer.refactor.cycle.CycleRefactoringExecutor executor = new com.archanalyzer.refactor.cycle.CycleRefactoringExecutor();
        com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions options = new com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions();
        options.setRunMavenTests(false);

        var execRes = executor.execute(projectSrc, options);

        // DEFECT-S1-04 REMEDIATED:
        // AstCallerRewriter detects the pre-existing setter and reuses it instead of generating
        // a duplicate setter method. Refactoring compiles cleanly and breaks the cycle!
        assertEquals(com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus.VERIFIED, execRes.getStatus(),
                "DEFECT-S1-04 Remediated: Pre-existing setter is reused, compilation passes");
        assertEquals(0, execRes.getFinalCyclicSccCount(), "Cycle count must be reduced to 0");
        assertTrue(execRes.getErrors().isEmpty(), "No compilation errors should occur: " + execRes.getErrors());
    }

    private static void createZip(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath));
             var walk = Files.walk(sourceDir)) {
            for (Path path : walk.filter(p -> !Files.isDirectory(p)).toList()) {
                String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    private static void extractZip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = targetDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    // --- Dynamic Proxy Mocks ---

    private static ServletContext createMockServletContext(Map<String, Object> attrs) {
        return (ServletContext) Proxy.newProxyInstance(
                ServletContext.class.getClassLoader(),
                new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attrs.get(args[0]);
                    case "setAttribute" -> { attrs.put((String) args[0], args[1]); yield null; }
                    case "removeAttribute" -> { attrs.remove(args[0]); yield null; }
                    case "getContextPath" -> "/archanalyzer";
                    case "getServletContextName" -> "archanalyzer";
                    case "log" -> null;
                    case "toString" -> "MockServletContext";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static HttpSession createMockHttpSession(ServletContext ctx, Map<String, Object> attrs) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> "mock-session-id";
                    case "getServletContext" -> ctx;
                    case "getAttribute" -> attrs.get(args[0]);
                    case "setAttribute" -> { attrs.put((String) args[0], args[1]); yield null; }
                    case "removeAttribute" -> { attrs.remove(args[0]); yield null; }
                    case "invalidate" -> { attrs.clear(); yield null; }
                    case "isNew" -> false;
                    case "toString" -> "MockHttpSession";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static HttpServletRequest createMockRequest(ServletContext ctx, HttpSession session,
                                                        String method, String uri, String pathInfo,
                                                        Map<String, String> params, Map<String, Part> parts) {
        Map<String, Object> reqAttrs = new HashMap<>();
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "/archanalyzer";
                    case "getPathInfo" -> pathInfo;
                    case "getParameter" -> params.get(args[0]);
                    case "getParameterMap" -> {
                        Map<String, String[]> map = new HashMap<>();
                        params.forEach((k, v) -> map.put(k, new String[]{v}));
                        yield map;
                    }
                    case "getSession" -> session;
                    case "getServletContext" -> ctx;
                    case "getAttribute" -> reqAttrs.get(args[0]);
                    case "setAttribute" -> { reqAttrs.put((String) args[0], args[1]); yield null; }
                    case "getPart" -> parts.get(args[0]);
                    case "getParts" -> parts.values();
                    case "getRequestURL" -> new StringBuffer("http://localhost:8085" + uri);
                    case "getServletPath" -> uri;
                    case "toString" -> "MockHttpServletRequest[" + method + " " + uri + "]";
                    default -> defaultValue(m.getReturnType());
                }
        );
    }

    static class MockResponseState {
        int status = 200;
        String contentType;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintWriter writer;

        String getContentAsString() {
            if (writer != null) writer.flush();
            return bytes.toString(StandardCharsets.UTF_8);
        }

        byte[] getBinaryContent() {
            if (writer != null) writer.flush();
            return bytes.toByteArray();
        }
    }

    private static HttpServletResponse createMockResponse(MockResponseState state) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> { state.status = (int) args[0]; yield null; }
                    case "getStatus" -> state.status;
                    case "setContentType" -> { state.contentType = (String) args[0]; yield null; }
                    case "getContentType" -> state.contentType;
                    case "sendRedirect" -> { state.status = 302; yield null; }
                    case "sendError" -> { state.status = (int) args[0]; yield null; }
                    case "getWriter" -> {
                        if (state.writer == null) {
                            state.writer = new PrintWriter(new OutputStreamWriter(state.bytes, StandardCharsets.UTF_8), true);
                        }
                        yield state.writer;
                    }
                    case "getOutputStream" -> new ServletOutputStream() {
                        @Override public boolean isReady() { return true; }
                        @Override public void setWriteListener(WriteListener writeListener) {}
                        @Override public void write(int b) { state.bytes.write(b); }
                        @Override public void write(byte[] b, int off, int len) { state.bytes.write(b, off, len); }
                    };
                    case "setHeader", "addHeader" -> null;
                    case "toString" -> "MockHttpServletResponse[status=" + state.status + "]";
                    default -> defaultValue(m.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }

    static class MockPart implements Part {
        private final String name;
        private final String filename;
        private final byte[] data;

        public MockPart(String name, String filename, byte[] data) {
            this.name = name;
            this.filename = filename;
            this.data = data;
        }

        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public String getContentType() { return "application/zip"; }
        @Override public String getName() { return name; }
        @Override public String getSubmittedFileName() { return filename; }
        @Override public long getSize() { return data.length; }
        @Override public void write(String fileName) {}
        @Override public void delete() {}
        @Override public String getHeader(String name) { return null; }
        @Override public Collection<String> getHeaders(String name) { return List.of(); }
        @Override public Collection<String> getHeaderNames() { return List.of(); }
    }
}
