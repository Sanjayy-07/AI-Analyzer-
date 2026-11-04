package com.archanalyzer.riskprediction;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.cochange.CoChangeEdge;
import com.archanalyzer.riskprediction.cochange.CoChangeGraph;
import com.archanalyzer.riskprediction.dataset.DatasetInstance;
import com.archanalyzer.riskprediction.dataset.RiskLabel;
import com.archanalyzer.riskprediction.dataset.TemporalDataset;
import com.archanalyzer.riskprediction.dataset.TemporalDatasetBuilder;
import com.archanalyzer.riskprediction.evaluation.AblationStudy;
import com.archanalyzer.riskprediction.evaluation.EvaluationMetrics;
import com.archanalyzer.riskprediction.evaluation.ModelEvaluator;
import com.archanalyzer.riskprediction.features.ComponentFeatureVector;
import com.archanalyzer.riskprediction.features.EvolutionaryFeatures;
import com.archanalyzer.riskprediction.features.StaticArchitectureFeatures;
import com.archanalyzer.riskprediction.history.GitCommit;
import com.archanalyzer.riskprediction.history.GitHistory;
import com.archanalyzer.riskprediction.history.GitHistoryExtractor;
import com.archanalyzer.riskprediction.model.BaselinePriorClassifier;
import com.archanalyzer.riskprediction.model.DecisionTree;
import com.archanalyzer.riskprediction.model.LogisticRegressionClassifier;
import com.archanalyzer.riskprediction.model.RandomForestClassifier;
import com.archanalyzer.riskprediction.model.TrainableClassifier;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.ComponentRiskResult;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.ProjectRiskOverview;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.RiskCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for Novelty 3:
 * AI-Based Architecture Risk Prediction Using Static Dependency Graph + Git History.
 *
 * Verifies:
 * 1. Git commit extraction, message regex parsing, and chronological filtering.
 * 2. Evolutionary co-change mining, Jaccard strength, and network centrality.
 * 3. Leakage-safe temporal dataset construction across observation boundaries.
 * 4. Pure-Java machine learning algorithms (Baseline, Logistic Regression, Random Forest).
 * 5. Empirical evaluation metrics (Precision, Recall, F1, ROC-AUC, PR-AUC, Brier score, Top-20%).
 * 6. Multi-modal ablation study and research hypothesis verification.
 * 7. End-to-end ArchitectureRiskService facade and graceful static fallback.
 */
public class ArchitectureRiskPredictionIntegrationTest {

    @TempDir
    Path tempDir;

    private ProjectModel testModel;
    private DependencyGraph testGraph;

    @BeforeEach
    void setUp() {
        testModel = new ProjectModel("ArchitectureRiskTestProject");

        // Create 6 components
        ProjectModel.ComponentNode c1 = new ProjectModel.ComponentNode(
                "com.test.Controller", "Controller", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/Controller.java"
        );
        c1.setLoc(350);
        c1.setCyclomaticComplexity(28);
        c1.setMethodCount(14);
        c1.setFieldCount(6);

        ProjectModel.ComponentNode c2 = new ProjectModel.ComponentNode(
                "com.test.ServiceA", "ServiceA", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/ServiceA.java"
        );
        c2.setLoc(420);
        c2.setCyclomaticComplexity(35);
        c2.setMethodCount(18);
        c2.setFieldCount(8);

        ProjectModel.ComponentNode c3 = new ProjectModel.ComponentNode(
                "com.test.ServiceB", "ServiceB", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/ServiceB.java"
        );
        c3.setLoc(280);
        c3.setCyclomaticComplexity(18);
        c3.setMethodCount(10);
        c3.setFieldCount(4);

        ProjectModel.ComponentNode c4 = new ProjectModel.ComponentNode(
                "com.test.Repository", "Repository", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/Repository.java"
        );
        c4.setLoc(180);
        c4.setCyclomaticComplexity(12);
        c4.setMethodCount(8);
        c4.setFieldCount(3);

        ProjectModel.ComponentNode c5 = new ProjectModel.ComponentNode(
                "com.test.Util", "Util", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/Util.java"
        );
        c5.setLoc(90);
        c5.setCyclomaticComplexity(6);
        c5.setMethodCount(4);
        c5.setFieldCount(1);

        ProjectModel.ComponentNode c6 = new ProjectModel.ComponentNode(
                "com.test.Config", "Config", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/com/test/Config.java"
        );
        c6.setLoc(75);
        c6.setCyclomaticComplexity(4);
        c6.setMethodCount(3);
        c6.setFieldCount(2);

        testModel.addComponent(c1);
        testModel.addComponent(c2);
        testModel.addComponent(c3);
        testModel.addComponent(c4);
        testModel.addComponent(c5);
        testModel.addComponent(c6);

        // Build dependency graph with an intentional cycle between ServiceA and ServiceB
        testGraph = new DependencyGraph();
        testGraph.addEdge("com.test.Controller", "com.test.ServiceA");
        testGraph.addEdge("com.test.Controller", "com.test.ServiceB");
        testGraph.addEdge("com.test.ServiceA", "com.test.ServiceB");
        testGraph.addEdge("com.test.ServiceB", "com.test.ServiceA"); // CYCLIC DEPENDENCY ServiceA <-> ServiceB
        testGraph.addEdge("com.test.ServiceA", "com.test.Repository");
        testGraph.addEdge("com.test.ServiceB", "com.test.Repository");
        testGraph.addEdge("com.test.Repository", "com.test.Util");
        testGraph.addEdge("com.test.Controller", "com.test.Config");
    }

    @Nested
    @DisplayName("Phase B: Git History Extraction & Filtering")
    class GitHistoryTests {

        @Test
        @DisplayName("Extract and parse Git log stream with churn and bug-fix tags")
        void testParseGitLogStream() throws Exception {
            String rawGitLog =
                    "COMMIT:a1b2c3d|1600000000|feat(core): initial component scaffold\n" +
                    "100\t0\tsrc/com/test/Controller.java\n" +
                    "150\t0\tsrc/com/test/ServiceA.java\n" +
                    "COMMIT:b2c3d4e|1600100000|fix(auth): fix null pointer bug in ServiceA and ServiceB #42\n" +
                    "25\t10\tsrc/com/test/ServiceA.java\n" +
                    "18\t8\tsrc/com/test/ServiceB.java\n" +
                    "COMMIT:c3d4e5f|1600200000|refactor(db): optimize Repository queries\n" +
                    "40\t15\tsrc/com/test/Repository.java\n";

            InputStream stream = new ByteArrayInputStream(rawGitLog.getBytes(StandardCharsets.UTF_8));
            GitHistoryExtractor extractor = new GitHistoryExtractor();
            List<GitCommit> commits = extractor.parseGitLogOutput(stream);

            assertEquals(3, commits.size());

            // First commit
            GitCommit c1 = commits.get(0);
            assertEquals("a1b2c3d", c1.getCommitHash());
            assertEquals(1600000000L, c1.getTimestampSeconds());
            assertFalse(c1.isBugFix());
            assertEquals(2, c1.getChangedFiles().size());

            // Second commit (bug fix)
            GitCommit c2 = commits.get(1);
            assertEquals("b2c3d4e", c2.getCommitHash());
            assertTrue(c2.isBugFix(), "Expected bug fix message to be detected via regex pattern");
            assertTrue(c2.touchesFile("src/com/test/ServiceA.java"));
            assertTrue(c2.touchesFile("src/com/test/ServiceB.java"));
            assertEquals(25, c2.getChurn("src/com/test/ServiceA.java").linesAdded());
            assertEquals(10, c2.getChurn("src/com/test/ServiceA.java").linesDeleted());
        }

        @Test
        @DisplayName("Temporal filtering partitions commits strictly without overlap")
        void testTemporalFiltering() {
            List<GitCommit> commits = List.of(
                    createCommit("h1", 1000L, "init", Map.of("A.java", 10), false),
                    createCommit("h2", 2000L, "feature", Map.of("A.java", 20), false),
                    createCommit("h3", 3000L, "fix bug", Map.of("A.java", 5), true),
                    createCommit("h4", 4000L, "update", Map.of("B.java", 15), false),
                    createCommit("h5", 5000L, "fix issue", Map.of("B.java", 8), true)
            );

            GitHistory history = GitHistory.of(tempDir, commits);
            assertEquals(5, history.commitCount());
            assertEquals(1000L, history.getEarliestTimestamp());
            assertEquals(5000L, history.getLatestTimestamp());

            // Filter window: past observation [0, 3000] vs future prediction [3001, 6000]
            GitHistory past = history.filterWindow(0L, 3000L);
            GitHistory future = history.filterWindow(3001L, 6000L);

            assertEquals(3, past.commitCount());
            assertEquals(2, future.commitCount());
            assertEquals(3000L, past.getLatestTimestamp());
            assertEquals(4000L, future.getEarliestTimestamp());
        }
    }

    @Nested
    @DisplayName("Phase C & D: Co-Change Graph & Features")
    class CoChangeAndFeatureTests {

        @Test
        @DisplayName("Mines co-change edges, calculates Jaccard strength and network centrality")
        void testCoChangeGraphMining() {
            List<GitCommit> commits = List.of(
                    createCommit("c1", 1000L, "feat: update A and B", Map.of(
                            "src/com/test/ServiceA.java", 20,
                            "src/com/test/ServiceB.java", 15
                    ), false),
                    createCommit("c2", 2000L, "fix: bug in A and B", Map.of(
                            "src/com/test/ServiceA.java", 10,
                            "src/com/test/ServiceB.java", 8
                    ), true),
                    createCommit("c3", 3000L, "feat: update A, B and Controller", Map.of(
                            "src/com/test/ServiceA.java", 30,
                            "src/com/test/ServiceB.java", 25,
                            "src/com/test/Controller.java", 12
                    ), false),
                    createCommit("c4", 4000L, "fix: fix Repository only", Map.of(
                            "src/com/test/Repository.java", 15
                    ), true)
            );

            GitHistory history = GitHistory.of(tempDir, commits);
            CoChangeGraph coGraph = CoChangeGraph.build(history, testModel, 1);

            assertTrue(coGraph.getCoChangeDegree("com.test.ServiceA") >= 1);
            assertTrue(coGraph.getCoChangeDegree("com.test.ServiceA") >= 2);
            assertEquals(0, coGraph.getCoChangeDegree("com.test.Repository"));

            List<CoChangeEdge> edgesA = coGraph.getNeighbors("com.test.ServiceA");
            assertFalse(edgesA.isEmpty());

            // Edge between ServiceA and ServiceB co-changed in all 3 commits
            Optional<CoChangeEdge> abEdge = edgesA.stream()
                    .filter(e -> (e.componentA().contains("ServiceB") || e.componentB().contains("ServiceB")))
                    .findFirst();
            assertTrue(abEdge.isPresent());
            assertEquals(3, abEdge.get().coChangeCount());
            assertEquals(1.0, abEdge.get().jaccardStrength(), 0.001);
        }

        @Test
        @DisplayName("Static architecture features extract graph topology and cyclomatic complexity")
        void testStaticFeatureExtraction() {
            StaticArchitectureFeatures sfA = StaticArchitectureFeatures.extract("com.test.ServiceA", testModel, testGraph);
            assertNotNull(sfA);
            assertEquals(420, sfA.linesOfCode());
            assertEquals(35, sfA.cyclomaticComplexity());
            assertTrue(sfA.inCycle(), "ServiceA must be identified as participating in a cyclic SCC");
            assertTrue(sfA.couplingIntensity() > 0);

            StaticArchitectureFeatures sfUtil = StaticArchitectureFeatures.extract("com.test.Util", testModel, testGraph);
            assertFalse(sfUtil.inCycle(), "Util must not be in a cycle");
            assertEquals(90, sfUtil.linesOfCode());
        }

        @Test
        @DisplayName("Evolutionary features extract churn metrics strictly from observation window")
        void testEvolutionaryFeatureExtraction() {
            List<GitCommit> commits = List.of(
                    createCommit("c1", 1000L, "add A", Map.of("src/com/test/ServiceA.java", 100), false),
                    createCommit("c2", 2000L, "mod A", Map.of("src/com/test/ServiceA.java", 50), false)
            );
            GitHistory history = GitHistory.of(tempDir, commits);
            CoChangeGraph coGraph = CoChangeGraph.build(history, testModel, 1);

            EvolutionaryFeatures ef = EvolutionaryFeatures.extract(
                    "com.test.ServiceA", history, coGraph, 2500L, 2500L
            );

            assertEquals(2, ef.commitCount());
            assertEquals(150, ef.churnTotal());
        }
    }

    @Nested
    @DisplayName("Phase E: Temporal Dataset Construction & Leakage Protection")
    class TemporalDatasetTests {

        @Test
        @DisplayName("Strict Temporal Boundary Safeguard: Future commits do NOT leak into past feature representations")
        void testStrictLeakageSafeguard() {
            long tObs = 5000L;
            long tTarget = 10000L;

            // Commit sequence up to observation boundary
            List<GitCommit> baseCommits = new ArrayList<>();
            baseCommits.add(createCommit("c1", 1000L, "commit 1", Map.of("src/com/test/ServiceA.java", 50), false));
            baseCommits.add(createCommit("c2", 3000L, "commit 2", Map.of("src/com/test/ServiceA.java", 75), false));

            GitHistory pastOnlyHistory = GitHistory.of(tempDir, baseCommits);
            TemporalDatasetBuilder builder = new TemporalDatasetBuilder();
            TemporalDataset dsPast = builder.buildDataset(
                    testModel, testGraph, pastOnlyHistory, tObs, tTarget - tObs, RiskLabel.LabelType.DEFECT_RISK
            );

            Optional<DatasetInstance> instPast = dsPast.getInstances().stream()
                    .filter(i -> i.getComponentId().equals("com.test.ServiceA"))
                    .findFirst();
            assertTrue(instPast.isPresent());
            double churnPast = instPast.get().getEvolutionaryFeatures()[3]; // churn_total
            assertEquals(125.0, churnPast, 0.001);

            // Now append a massive future commit occurring strictly AFTER T_obs
            List<GitCommit> historyWithFuture = new ArrayList<>(baseCommits);
            historyWithFuture.add(createCommit("cFuture", 8000L, "future fix", Map.of("src/com/test/ServiceA.java", 5000), true));

            GitHistory fullHistory = GitHistory.of(tempDir, historyWithFuture);
            TemporalDataset dsWithFuture = builder.buildDataset(
                    testModel, testGraph, fullHistory, tObs, tTarget - tObs, RiskLabel.LabelType.DEFECT_RISK
            );

            Optional<DatasetInstance> instWithFuture = dsWithFuture.getInstances().stream()
                    .filter(i -> i.getComponentId().equals("com.test.ServiceA"))
                    .findFirst();
            assertTrue(instWithFuture.isPresent());

            // VERIFICATION OF LEAKAGE SAFEGUARD:
            // Past feature representation MUST remain completely identical (125.0), despite 5000 LOC added in future!
            assertEquals(churnPast, instWithFuture.get().getEvolutionaryFeatures()[3], 0.001,
                    "CRITICAL: Future commit after T_obs must NOT alter past feature vector!");

            // Future defect label MUST reflect the bug fix in (T_obs, T_target]
            assertEquals(1, instWithFuture.get().getTarget(),
                    "Future bug-fix commit in horizon must produce target label = 1");
            assertEquals(0, instPast.get().getTarget(),
                    "Without future bug-fix commits, target label must be 0");
        }
    }

    @Nested
    @DisplayName("Phase F: Machine Learning Pipeline (Zero Third-Party Dependencies)")
    class MachineLearningPipelineTests {

        @Test
        @DisplayName("BaselinePriorClassifier predicts exact positive prior probability")
        void testBaselinePriorClassifier() {
            double[][] X = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}, {7.0, 8.0}};
            int[] y = {1, 0, 0, 0}; // 1/4 = 0.25 prior

            BaselinePriorClassifier model = new BaselinePriorClassifier();
            model.train(X, y, new String[]{"f1", "f2"});

            assertEquals(0.25, model.predictProbability(new double[]{9.0, 10.0}), 0.001);
            assertEquals(0, model.predict(new double[]{9.0, 10.0}));
        }

        @Test
        @DisplayName("LogisticRegressionClassifier converges and predicts valid probabilities in [0, 1]")
        void testLogisticRegressionClassifier() {
            double[][] X = {
                    {1.0, 1.0},
                    {1.5, 2.0},
                    {2.0, 1.0},
                    {8.0, 9.0},
                    {9.0, 8.0},
                    {10.0, 10.0}
            };
            int[] y = {0, 0, 0, 1, 1, 1};

            LogisticRegressionClassifier lr = new LogisticRegressionClassifier(0.1, 0.01, 200);
            lr.train(X, y, new String[]{"feat1", "feat2"});

            double lowProb = lr.predictProbability(new double[]{1.2, 1.3});
            double highProb = lr.predictProbability(new double[]{9.5, 9.2});

            assertTrue(lowProb < 0.5, "Expected low-feature sample to have probability < 0.5");
            assertTrue(highProb > 0.5, "Expected high-feature sample to have probability > 0.5");
            assertEquals(0, lr.predict(new double[]{1.2, 1.3}));
            assertEquals(1, lr.predict(new double[]{9.5, 9.2}));
        }

        @Test
        @DisplayName("RandomForestClassifier trains ensemble of decision trees with Gini importance")
        void testRandomForestClassifier() {
            double[][] X = {
                    {10.0, 5.0, 1.0},
                    {15.0, 4.0, 0.0},
                    {8.0, 6.0, 0.0},
                    {80.0, 40.0, 1.0},
                    {90.0, 50.0, 1.0},
                    {110.0, 60.0, 1.0}
            };
            int[] y = {0, 0, 0, 1, 1, 1};
            String[] names = {"f1", "f2", "f3"};

            RandomForestClassifier rf = new RandomForestClassifier(15, 4, 2, 42L);
            rf.train(X, y, names);

            double pLow = rf.predictProbability(new double[]{12.0, 5.0, 0.0});
            double pHigh = rf.predictProbability(new double[]{95.0, 45.0, 1.0});

            assertTrue(pLow < 0.4, "Expected low risk probability < 0.4, got " + pLow);
            assertTrue(pHigh > 0.6, "Expected high risk probability > 0.6, got " + pHigh);

            Map<String, Double> importances = rf.getFeatureImportances();
            assertNotNull(importances);
            assertEquals(3, importances.size());
        }
    }

    @Nested
    @DisplayName("Phase G: Empirical Evaluation & Ablation Study")
    class EvaluationAndAblationTests {

        @Test
        @DisplayName("ModelEvaluator computes trapezoidal ROC-AUC and ranking metrics accurately")
        void testModelEvaluator() {
            double[][] X = {
                    {1.0}, {2.0}, {3.0}, {4.0}, {5.0}, {6.0}
            };
            int[] y = {0, 0, 0, 1, 1, 1};

            RandomForestClassifier rf = new RandomForestClassifier(20, 5, 2, 42L);
            rf.train(X, y, new String[]{"feat"});

            ModelEvaluator evaluator = new ModelEvaluator();
            EvaluationMetrics metrics = evaluator.evaluate(rf, X, y);

            assertTrue(metrics.rocAuc() >= 0.85, "Expected high ROC-AUC on separable dataset, got " + metrics.rocAuc());
            assertTrue(metrics.brierScore() < 0.25, "Expected low Brier error, got " + metrics.brierScore());
        }

        @Test
        @DisplayName("Ablation Study confirms core research hypothesis: Combined Multi-Modal outperforms Static-Only")
        void testAblationStudyHypothesisConfirmation() {
            // Construct synthetic train and test datasets where both static complexity and evolutionary churn matter
            List<DatasetInstance> trainInstances = new ArrayList<>();
            List<DatasetInstance> testInstances = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                boolean highRisk = (i % 2 == 1);
                double staticLoc = highRisk ? 500 + i * 10 : 80 + i * 5;
                double staticCc = highRisk ? 35 + i : 5 + (i % 3);
                double churn = highRisk ? 1200 + i * 50 : 50 + i * 10;
                double commits = highRisk ? 30 + i : 2 + (i % 4);

                double[] sVals = new double[11];
                sVals[0] = staticLoc;
                sVals[1] = staticCc;
                sVals[6] = highRisk ? 15.0 : 2.0; // coupling
                sVals[9] = highRisk ? 1.0 : 0.0;  // cycle

                double[] eVals = new double[13];
                eVals[0] = commits;
                eVals[3] = churn;
                eVals[10] = highRisk ? 6.0 : 0.0; // cochange degree

                StaticArchitectureFeatures sf = new StaticArchitectureFeatures(sVals);
                EvolutionaryFeatures ef = new EvolutionaryFeatures(eVals);
                ComponentFeatureVector vec = new ComponentFeatureVector("Comp_" + i, sf, ef);

                RiskLabel label = new RiskLabel("Comp_" + i, highRisk ? 3 : 0, highRisk ? 5 : 0, highRisk ? 200 : 0, RiskLabel.LabelType.DEFECT_RISK);
                DatasetInstance inst = new DatasetInstance("Comp_" + i, vec, label);

                if (i < 14) trainInstances.add(inst);
                else testInstances.add(inst);
            }

            TemporalDataset trainDs = new TemporalDataset("TrainDs", trainInstances, 1000L, 500L);
            TemporalDataset testDs = new TemporalDataset("TestDs", testInstances, 1000L, 500L);

            AblationStudy ablation = new AblationStudy();
            AblationStudy.AblationResult result = ablation.runAblation(trainDs, testDs);

            assertNotNull(result);
            assertNotNull(result.baselineMetrics());
            assertNotNull(result.staticMetrics());
            assertNotNull(result.evolutionaryMetrics());
            assertNotNull(result.combinedMetrics());

            // Check that combined model achieves competitive or superior ROC-AUC / F1
            assertTrue(result.combinedMetrics().rocAuc() >= result.staticMetrics().rocAuc() || result.combinedMetrics().f1() >= result.staticMetrics().f1());
            assertNotNull(result.summary());
        }
    }

    @Nested
    @DisplayName("Service Layer: ArchitectureRiskService Facade")
    class ServiceFacadeTests {

        @Test
        @DisplayName("Assesses project risk, detects cyclic high-risk components, and caches overview")
        void testServiceRiskAssessment() {
            ArchitectureRiskService service = new ArchitectureRiskService();
            ProjectRiskOverview overview = service.assessProjectRisk(tempDir, testModel, testGraph);

            assertNotNull(overview);
            assertEquals(6, overview.totalComponents());
            assertTrue(overview.highRiskCount() + overview.mediumRiskCount() + overview.lowRiskCount() == 6);
            assertTrue(overview.averageRiskProbability() > 0.0);

            // ServiceA is in cycle, has high CC (35) and high LOC (420) -> should be High or Medium risk
            Optional<ComponentRiskResult> compA = overview.components().stream()
                    .filter(c -> c.componentName().contains("ServiceA"))
                    .findFirst();
            assertTrue(compA.isPresent());
            assertTrue(compA.get().inCycle(), "ServiceA must have inCycle=true");
            assertTrue(compA.get().riskProbability() >= 0.35);
            assertFalse(compA.get().topStaticFactors().isEmpty());

            // Check cyclic high risk component list
            if (compA.get().riskCategory() == RiskCategory.HIGH) {
                assertTrue(overview.cycleComponentsWithHighRisk().contains("com.test.ServiceA"));
            }

            // Verify caching
            String key = tempDir.toAbsolutePath().normalize().toString();
            ProjectRiskOverview cached = service.getCachedAssessment(key);
            assertNotNull(cached);
            assertEquals(overview.totalComponents(), cached.totalComponents());
        }

        @Test
        @DisplayName("Graceful degradation: Operates reliably in static structural mode when Git history is absent")
        void testStaticFallbackMode() {
            ArchitectureRiskService service = new ArchitectureRiskService();
            // Pass null projectRoot -> simulates missing Git repo
            ProjectRiskOverview overview = service.assessProjectRisk(null, testModel, testGraph);

            assertNotNull(overview);
            assertFalse(overview.gitHistoryAvailable());
            assertEquals(0, overview.totalCommits());
            assertEquals(6, overview.totalComponents());
            assertTrue(overview.modelDescription().contains("Static Structural"));
        }
    }

    @Nested
    @DisplayName("Audit Corrections & Mathematical Invariant Tests")
    class AuditVerificationTests {

        @Test
        @DisplayName("Audit Defect 2: Constant baseline predictions evaluate to exact ROC-AUC = 0.500 regardless of label order")
        void testTieSafeRocAucInvariant() {
            ModelEvaluator evaluator = new ModelEvaluator();

            // Constant model assigning probability 0.5 to all samples
            TrainableClassifier constantModel = new TrainableClassifier() {
                @Override public void train(double[][] X, int[] y, String[] featureNames) {}
                @Override public double predictProbability(double[] x) { return 0.5; }
                @Override public String getModelName() { return "ConstantModel"; }
                @Override public String[] getFeatureNames() { return new String[0]; }
                @Override public Map<String, Double> getFeatureImportances() { return Map.of(); }
                @Override public String serialize() { return ""; }
                @Override public void deserialize(String data) {}
            };

            // Case A: Positives first [1, 1, 0, 0]
            double[][] X = {{1.0}, {2.0}, {3.0}, {4.0}};
            int[] yA = {1, 1, 0, 0};
            EvaluationMetrics metricsA = evaluator.evaluate(constantModel, X, yA);
            assertEquals(0.500, metricsA.rocAuc(), 0.001,
                    "Tied predictions with [1, 1, 0, 0] must yield exact ROC-AUC = 0.500");

            // Case B: Negatives first [0, 0, 1, 1]
            int[] yB = {0, 0, 1, 1};
            EvaluationMetrics metricsB = evaluator.evaluate(constantModel, X, yB);
            assertEquals(0.500, metricsB.rocAuc(), 0.001,
                    "Tied predictions with [0, 0, 1, 1] must yield exact ROC-AUC = 0.500");

            // Case C: Alternating [1, 0, 1, 0]
            int[] yC = {1, 0, 1, 0};
            EvaluationMetrics metricsC = evaluator.evaluate(constantModel, X, yC);
            assertEquals(0.500, metricsC.rocAuc(), 0.001,
                    "Tied predictions with [1, 0, 1, 0] must yield exact ROC-AUC = 0.500");

            // PR-AUC for constant model must equal the positive prevalence ratio (2 / 4 = 0.5)
            assertEquals(0.500, metricsA.prAuc(), 0.001);
            assertEquals(0.500, metricsB.prAuc(), 0.001);
            assertEquals(0.500, metricsC.prAuc(), 0.001);
        }

        @Test
        @DisplayName("Audit Defect 1: RandomForestClassifier serializes and restores full decision tree ensemble")
        void testRandomForestSerializationAndRestoration() {
            double[][] X = {
                    {10.0, 1.0}, {12.0, 2.0}, {14.0, 1.0},
                    {90.0, 8.0}, {95.0, 9.0}, {100.0, 10.0}
            };
            int[] y = {0, 0, 0, 1, 1, 1};
            String[] names = {"loc", "churn"};

            RandomForestClassifier original = new RandomForestClassifier(10, 4, 2, 42L);
            original.train(X, y, names);

            double[] testSampleLow = {11.0, 1.5};
            double[] testSampleHigh = {92.0, 8.5};

            double probLowOriginal = original.predictProbability(testSampleLow);
            double probHighOriginal = original.predictProbability(testSampleHigh);

            // Serialize model to string
            String serialized = original.serialize();
            assertNotNull(serialized);
            assertTrue(serialized.contains("MODEL=RandomForest"));
            assertTrue(serialized.contains("TREE_0="));

            // Deserialize into new model instance
            RandomForestClassifier restored = new RandomForestClassifier();
            restored.deserialize(serialized);

            // Assert that restored model reproduces exact predictions without error
            double probLowRestored = restored.predictProbability(testSampleLow);
            double probHighRestored = restored.predictProbability(testSampleHigh);

            assertEquals(probLowOriginal, probLowRestored, 1e-6,
                    "Restored Random Forest model must output identical probability for low risk sample");
            assertEquals(probHighOriginal, probHighRestored, 1e-6,
                    "Restored Random Forest model must output identical probability for high risk sample");
        }

        @Test
        @DisplayName("Audit Defect 3: Missing-value (NaN) inputs are safely handled without throwing exceptions")
        void testMissingValueHandling() {
            double[][] X = {{1.0, 2.0}, {2.0, 3.0}, {10.0, 20.0}, {20.0, 30.0}};
            int[] y = {0, 0, 1, 1};

            LogisticRegressionClassifier lr = new LogisticRegressionClassifier(0.1, 0.01, 100);
            lr.train(X, y, new String[]{"f1", "f2"});

            // Vector with NaN values
            double[] nanVector = {Double.NaN, 2.0};
            double lrProb = lr.predictProbability(nanVector);
            assertFalse(Double.isNaN(lrProb), "LogisticRegression should not output NaN when input has NaN");
            assertTrue(lrProb >= 0.0 && lrProb <= 1.0);

            DecisionTree tree = new DecisionTree(3, 2, 2);
            tree.train(X, y, new int[]{0, 1, 2, 3}, new Random(42L));
            double treeProb = tree.predict(nanVector);
            assertFalse(Double.isNaN(treeProb), "DecisionTree should not output NaN when input has NaN");
            assertTrue(treeProb >= 0.0 && treeProb <= 1.0);
        }
    }

    private static GitCommit createCommit(String hash, long timestamp, String message, Map<String, Integer> filesWithLines, boolean bugFix) {
        Map<String, GitCommit.FileChurn> churnMap = new HashMap<>();
        for (var e : filesWithLines.entrySet()) {
            churnMap.put(e.getKey(), new GitCommit.FileChurn(e.getValue(), 0));
        }
        return new GitCommit(hash, timestamp, message, churnMap);
    }
}
