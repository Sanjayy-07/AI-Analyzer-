package com.archanalyzer.pipeline;

import com.archanalyzer.archive.ZipExtractor;
import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.refactor.CycleRefactoringService;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.CycleGoal;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.validate.BuildValidator;
import com.archanalyzer.validate.BuildValidator.BuildResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S6: End-to-End Workflow Verification on an Isolated Synthetic Project.
 * Validates:
 * 1. Project ZIP creation with empty directories.
 * 2. Unzipping and discovery of 2 independent cycles.
 * 3. Iterative cycle breaking under BREAK_ALL.
 * 4. Validation via BuildValidator with accurate stage metrics.
 * 5. Packaging to modified ZIP and preservation of empty directories.
 * 6. Extraction of deliverable ZIP and verification of business side effects.
 */
public class PhaseSEndToEndWorkflowTest {

    @TempDir
    Path tempDir;

    private Path writeText(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("Phase S: Full E2E ZIP Upload -> Discovery -> Multi-Cycle Refactor -> BuildValidator -> ZIP Export -> Re-test")
    public void testPhaseSEndToEndWorkflow() throws Exception {
        Path fixtureRoot = tempDir.resolve("e2e-synthetic-source");
        Files.createDirectories(fixtureRoot);

        // 1. Create pom.xml
        String pomXml = """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.e2e</groupId>
                <artifactId>synthetic-e2e</artifactId>
                <version>1.0.0</version>
                <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                    <maven.compiler.target>17</maven.compiler.target>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-api</artifactId>
                        <version>5.10.2</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-engine</artifactId>
                        <version>5.10.2</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """;
        writeText(fixtureRoot, "pom.xml", pomXml);

        // 2. Cycle 1: OrderService <-> InventoryService
        String orderService = """
            package com.e2e.order;
            import com.e2e.inventory.InventoryService;
            public class OrderService {
                private InventoryService inventory;
                public static int ordersPlaced = 0;
                public void placeOrder(String item, int qty) {
                    ordersPlaced++;
                    if (inventory != null) {
                        inventory.checkStock(item);
                    }
                }
                public boolean isAvailable(String item) {
                    return true;
                }
            }
            """;
        writeText(fixtureRoot, "src/main/java/com/e2e/order/OrderService.java", orderService);

        String inventoryService = """
            package com.e2e.inventory;
            import com.e2e.order.OrderService;
            public class InventoryService {
                private OrderService orderService;
                public static int stockChecked = 0;
                public void checkStock(String item) {
                    stockChecked++;
                    if (orderService != null) {
                        orderService.isAvailable(item);
                    }
                }
            }
            """;
        writeText(fixtureRoot, "src/main/java/com/e2e/inventory/InventoryService.java", inventoryService);

        // 3. Cycle 2: PaymentService <-> NotificationService
        String paymentService = """
            package com.e2e.billing;
            import com.e2e.notify.NotificationService;
            public class PaymentService {
                private NotificationService notifier;
                public static int paymentsProcessed = 0;
                public void processPayment(double amt) {
                    paymentsProcessed++;
                    if (notifier != null) {
                        notifier.sendReceipt("Invoice", amt);
                    }
                }
                public boolean verifyCard(String card) {
                    return true;
                }
            }
            """;
        writeText(fixtureRoot, "src/main/java/com/e2e/billing/PaymentService.java", paymentService);

        String notificationService = """
            package com.e2e.notify;
            import com.e2e.billing.PaymentService;
            public class NotificationService {
                private PaymentService paymentService;
                public static int receiptsSent = 0;
                public void sendReceipt(String inv, double amt) {
                    receiptsSent++;
                    if (paymentService != null) {
                        paymentService.verifyCard("4111");
                    }
                }
            }
            """;
        writeText(fixtureRoot, "src/main/java/com/e2e/notify/NotificationService.java", notificationService);

        // 4. Test verifying runtime business behavior and side-effects
        String businessTest = """
            package com.e2e;
            import com.e2e.order.OrderService;
            import com.e2e.billing.PaymentService;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            public class E2eBusinessLogicTest {
                @Test
                public void testBusinessOperations() {
                    OrderService orderSvc = new OrderService();
                    orderSvc.placeOrder("Widget", 5);
                    assertEquals(1, OrderService.ordersPlaced, "Order must be counted");

                    PaymentService paySvc = new PaymentService();
                    paySvc.processPayment(99.50);
                    assertEquals(1, PaymentService.paymentsProcessed, "Payment must be counted");
                }
            }
            """;
        writeText(fixtureRoot, "src/test/java/com/e2e/E2eBusinessLogicTest.java", businessTest);

        // 5. Empty directories (DEF-04 validation)
        Path emptyMainResources = fixtureRoot.resolve("src/main/resources");
        Path emptyTestResources = fixtureRoot.resolve("src/test/resources");
        Path emptyDocsSpecs = fixtureRoot.resolve("docs/specs");
        Files.createDirectories(emptyMainResources);
        Files.createDirectories(emptyTestResources);
        Files.createDirectories(emptyDocsSpecs);

        // 6. Step A: Package original project into input ZIP
        Path inputZip = tempDir.resolve("input-project.zip");
        ProjectPackager packager = new ProjectPackager();
        packager.packageToZip(fixtureRoot, inputZip);
        assertTrue(Files.exists(inputZip));

        // 7. Step B: Extract into analysis workspace
        Path workspace = tempDir.resolve("analysis-workspace");
        ZipExtractor extractor = new ZipExtractor();
        extractor.extract(inputZip, workspace);

        // 8. Step C: Parse & analyze baseline dependency graph
        ProjectDiscovery discovery = new ProjectDiscovery();
        ProjectModel initialModel = discovery.discoverAndParse(workspace, "SyntheticE2E");
        DependencyGraph initialGraph = DependencyGraph.buildFrom(initialModel);
        List<Set<String>> initialSccs = GraphAlgorithms.stronglyConnectedComponents(initialGraph);
        List<Set<String>> initialCyclic = initialSccs.stream().filter(s -> s.size() > 1).toList();
        assertEquals(2, initialCyclic.size(), "Baseline project must have exactly 2 cyclic SCCs");

        // 9. Step D: Execute iterative cycle refactoring with BREAK_ALL
        RefactoringOptions opts = new RefactoringOptions();
        opts.setGoal(CycleGoal.BREAK_ALL);
        opts.setMaxIterations(10);
        opts.setRunMavenTests(false); // In-memory compiler for iterative loop

        CycleRefactoringService service = new CycleRefactoringService();
        CycleRefactoringService.CycleExecutionDto execDto = service.applyCycleBreaking(workspace, null, opts);

        assertTrue(execDto.success, "Cycle breaking must succeed: " + execDto.errors);
        assertEquals(2, execDto.initialCycleCount);
        assertEquals(0, execDto.finalCycleCount, "All cycles must be eliminated under BREAK_ALL");

        // 10. Step E: Validate using BuildValidator with accurate stage metrics (DEF-02)
        BuildValidator validator = new BuildValidator();
        BuildResult buildResult = validator.compile(workspace, List.of());

        assertTrue(buildResult.compilePassed, "Refactored code must compile cleanly");
        assertEquals(BuildValidator.Status.PASS, buildResult.status);

        // 11. Step F: Package deliverable modified ZIP (DEF-04)
        Path deliverableZip = tempDir.resolve("deliverable-project.zip");
        packager.packageToZip(workspace, deliverableZip);
        assertTrue(Files.exists(deliverableZip));

        // 12. Step G: Independently extract deliverable ZIP into fresh folder
        Path independentExtraction = tempDir.resolve("independent-deliverable");
        extractor.extract(deliverableZip, independentExtraction);

        // Verify empty directories were preserved across the entire round-trip
        assertTrue(Files.isDirectory(independentExtraction.resolve("src/main/resources")),
                "src/main/resources must be preserved");
        assertTrue(Files.isDirectory(independentExtraction.resolve("src/test/resources")),
                "src/test/resources must be preserved");
        assertTrue(Files.isDirectory(independentExtraction.resolve("docs/specs")),
                "docs/specs must be preserved");

        // Verify extracted project has 0 cycles
        ProjectModel extractedModel = discovery.discoverAndParse(independentExtraction, "SyntheticE2EDeliverable");
        DependencyGraph extractedGraph = DependencyGraph.buildFrom(extractedModel);
        List<Set<String>> extractedSccs = GraphAlgorithms.stronglyConnectedComponents(extractedGraph);
        List<Set<String>> extractedCyclic = extractedSccs.stream().filter(s -> s.size() > 1).toList();
        assertEquals(0, extractedCyclic.size(), "Extracted project must have 0 cyclic SCCs");

        // Verify compilation of extracted project
        BuildResult extractedBuild = validator.compile(independentExtraction, List.of());
        assertEquals(BuildValidator.Status.PASS, extractedBuild.status, "Extracted project must compile without errors");
    }
}
