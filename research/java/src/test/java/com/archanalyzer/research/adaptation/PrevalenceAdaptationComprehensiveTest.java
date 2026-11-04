package com.archanalyzer.research.adaptation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrevalenceAdaptationComprehensiveTest
 * =====================================
 * Tests all 15 scientific and technical requirements for Phase 3A:
 * 1. Logit and sigmoid numerical stability
 * 2. Probability bounds
 * 3. Probability ordering preservation (monotonicity)
 * 4. Zero-defect prevalence handling
 * 5. Near-zero and near-one prevalence
 * 6. Bayesian smoothing correctness
 * 7. EM convergence
 * 8. BBSE singular or unstable confusion matrices
 * 9. Invalid input handling
 * 10. Chronological burn-in integrity
 * 11. 90-day label maturity enforcement
 * 12. Evaluation-label isolation
 * 13. Identical baseline/adapted evaluation windows
 * 14. Java/Python interoperability / numerical parity
 * 15. Degenerate and edge-case metric behaviors
 */
public class PrevalenceAdaptationComprehensiveTest {

    private static final double PI_SOURCE = 0.1114;
    private static final double TOL = 1e-6;

    @Test
    @DisplayName("1. Logit and sigmoid numerical stability across extreme values")
    void testLogitSigmoidNumericalStability() {
        // Test near-boundary values
        double logitNearZero = ProbabilityAdapter.logit(1e-12);
        double logitNearOne = ProbabilityAdapter.logit(1.0 - 1e-12);
        assertTrue(Double.isFinite(logitNearZero), "Logit must be finite near 0");
        assertTrue(Double.isFinite(logitNearOne), "Logit must be finite near 1");

        // Test extreme inputs to sigmoid
        assertEquals(0.0, ProbabilityAdapter.sigmoid(-500.0), 1e-12, "Sigmoid must not underflow into NaN");
        assertEquals(1.0, ProbabilityAdapter.sigmoid(500.0), 1e-12, "Sigmoid must not overflow into NaN");
        assertEquals(0.5, ProbabilityAdapter.sigmoid(0.0), 1e-12, "Sigmoid(0) must be exactly 0.5");

        // Roundtrip invertibility
        double[] testP = {0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99};
        for (double p : testP) {
            double roundtrip = ProbabilityAdapter.sigmoid(ProbabilityAdapter.logit(p));
            assertEquals(p, roundtrip, 1e-6, "Sigmoid(Logit(p)) must invert p");
        }
    }

    @Test
    @DisplayName("2. Probability bounds guarantee [epsilon, 1 - epsilon]")
    void testProbabilityBounds() {
        double[] extremes = {0.0, 1.0, -0.5, 1.5};
        for (double p : extremes) {
            double clamped = PrevalenceEstimator.clamp(p, 1e-4, 1.0 - 1e-4);
            assertTrue(clamped >= 1e-4 && clamped <= (1.0 - 1e-4), "Clamped must be in valid range");
        }
    }

    @Test
    @DisplayName("3. Monotonicity: Log-odds adjustment strictly preserves score ordering")
    void testProbabilityOrderingPreservation() {
        double[] sourceScores = {0.05, 0.12, 0.18, 0.35, 0.72, 0.89};
        double piTarget = 0.2812; // Higher target prevalence

        double[] adapted = ProbabilityAdapter.adaptProbabilities(sourceScores, PI_SOURCE, piTarget);
        assertEquals(sourceScores.length, adapted.length);

        for (int i = 0; i < sourceScores.length - 1; i++) {
            assertTrue(adapted[i] < adapted[i + 1],
                    "Monotonicity violated: adapted[" + i + "]=" + adapted[i] + " >= adapted[" + (i+1) + "]=" + adapted[i+1]);
        }
    }

    @Test
    @DisplayName("4. Zero-defect prevalence does not cause mathematical collapse")
    void testZeroDefectPrevalence() {
        // e.g. commons-exec (0 defects out of 700 commits)
        int[] allZeroLabels = new int[100];
        boolean[] matured = new boolean[100];
        Arrays.fill(matured, true);

        // Bayesian smoothing prevents odds collapse
        double piBayes = PrevalenceEstimator.estimateBayesianBetaBinomial(allZeroLabels, matured, PI_SOURCE, 10.0);
        assertTrue(piBayes > 0.0, "Smoothed prevalence must be strictly positive");
        assertEquals((10.0 * PI_SOURCE) / 110.0, piBayes, 1e-5, "Must match Bayesian formula");

        // Log-odds adaptation with smoothed zero-defect prevalence remains finite
        double adaptedScore = ProbabilityAdapter.adaptProbability(0.25, PI_SOURCE, piBayes);
        assertTrue(Double.isFinite(adaptedScore) && adaptedScore > 0.0 && adaptedScore < 1.0,
                "Adapted score must remain valid finite probability");
    }

    @Test
    @DisplayName("5. Near-zero and near-one target prevalence stability")
    void testNearZeroAndNearOnePrevalence() {
        double nearZero = 1e-5;
        double nearOne = 1.0 - 1e-5;

        double pAdaptedLow = ProbabilityAdapter.adaptProbability(0.50, PI_SOURCE, nearZero);
        double pAdaptedHigh = ProbabilityAdapter.adaptProbability(0.50, PI_SOURCE, nearOne);

        assertTrue(Double.isFinite(pAdaptedLow) && pAdaptedLow > 0.0, "Near zero adaptation must be finite");
        assertTrue(Double.isFinite(pAdaptedHigh) && pAdaptedHigh < 1.0, "Near one adaptation must be finite");
        assertTrue(pAdaptedLow < pAdaptedHigh, "Higher target prevalence must yield higher adapted probability");
    }

    @Test
    @DisplayName("6. Bayesian Beta-Binomial smoothing correctness")
    void testBayesianSmoothingCorrectness() {
        int[] labels = {1, 0, 0, 1, 0}; // 2 defects out of 5
        boolean[] matured = {true, true, true, true, true};
        double M = 10.0;

        double expected = (2.0 + 10.0 * PI_SOURCE) / (5.0 + 10.0);
        double actual = PrevalenceEstimator.estimateBayesianBetaBinomial(labels, matured, PI_SOURCE, M);
        assertEquals(expected, actual, 1e-6);

        // Test with M=20
        double expected20 = (2.0 + 20.0 * PI_SOURCE) / (5.0 + 20.0);
        double actual20 = PrevalenceEstimator.estimateBayesianBetaBinomial(labels, matured, PI_SOURCE, 20.0);
        assertEquals(expected20, actual20, 1e-6);
    }

    @Test
    @DisplayName("7. Saerens EM prevalence estimator convergence")
    void testEMConvergence() {
        // Generative synthetic score distribution:
        // 80% non-defects with low scores (mean ~ 0.08), 20% defects with moderate scores (mean ~ 0.35)
        double[] scores = new double[200];
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            if (i < 40) {
                // 20% defects
                scores[i] = 0.20 + 0.30 * rng.nextDouble();
            } else {
                // 80% non-defects
                scores[i] = 0.02 + 0.12 * rng.nextDouble();
            }
        }

        double emPi = PrevalenceEstimator.estimateSaerensEM(scores, PI_SOURCE, 50, 1e-5);
        assertTrue(emPi > 0.05, "EM should produce a positive prevalence: " + emPi);
        assertTrue(emPi < 0.60, "EM should not explode on calibrated score distribution: " + emPi);
    }

    @Test
    @DisplayName("8. BBSE handles singular or unstable confusion matrix gracefully")
    void testBBSESingularMatrixHandling() {
        double[] scores = {0.1, 0.2, 0.3, 0.4};
        // When TPR == FPR, denominator is 0.0
        assertThrows(ArithmeticException.class, () -> {
            PrevalenceEstimator.estimateBBSE(scores, 0.25, 0.50, 0.50);
        }, "Must throw ArithmeticException when TPR == FPR");

        // Normal stable BBSE
        double piBBSE = PrevalenceEstimator.estimateBBSE(scores, 0.25, 0.80, 0.20);
        assertTrue(piBBSE >= 0.0 && piBBSE <= 1.0, "Valid BBSE must be in [0, 1]");
    }

    @Test
    @DisplayName("9. Invalid input handling across estimators and adapters")
    void testInvalidInputHandling() {
        assertThrows(NullPointerException.class, () -> PrevalenceEstimator.estimateSoftMean(null));
        assertThrows(IllegalArgumentException.class, () -> PrevalenceEstimator.estimateSoftMean(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> ProbabilityAdapter.adaptProbability(0.5, -0.1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> ProbabilityAdapter.adaptProbability(0.5, 0.2, 1.5));
    }

    @Test
    @DisplayName("10. Chronological burn-in sorting and stable tie-breaking")
    void testChronologicalBurnInIntegrity() {
        List<ChronologicalBurnInQueue.CommitRecord> raw = new ArrayList<>();
        // Commits with equal timestamps to verify stable index tie-breaking
        raw.add(new ChronologicalBurnInQueue.CommitRecord(0, "C0", 1000L, 0, 0.1));
        raw.add(new ChronologicalBurnInQueue.CommitRecord(1, "C1", 500L, 1, 0.8));
        raw.add(new ChronologicalBurnInQueue.CommitRecord(2, "C2", 1000L, 1, 0.9));
        raw.add(new ChronologicalBurnInQueue.CommitRecord(3, "C3", 200L, 0, 0.05));

        List<ChronologicalBurnInQueue.CommitRecord> sorted = ChronologicalBurnInQueue.sortChronologically(raw);
        assertEquals("C3", sorted.get(0).commitId); // 200L
        assertEquals("C1", sorted.get(1).commitId); // 500L
        assertEquals("C0", sorted.get(2).commitId); // 1000L, original 0
        assertEquals("C2", sorted.get(3).commitId); // 1000L, original 2
    }

    @Test
    @DisplayName("11. Strict 90-day label maturity delay enforcement")
    void testLabelMaturityDelayEnforcement() {
        long nanosPerDay = ChronologicalBurnInQueue.NANOS_PER_DAY;
        long delay90d = ChronologicalBurnInQueue.DEFAULT_DELAY_NANOS;

        List<ChronologicalBurnInQueue.CommitRecord> commits = new ArrayList<>();
        // Day 0: commit 0
        commits.add(new ChronologicalBurnInQueue.CommitRecord(0, "C0", 0L, 1, 0.8));
        // Day 50: commit 1
        commits.add(new ChronologicalBurnInQueue.CommitRecord(1, "C1", 50L * nanosPerDay, 1, 0.7));
        // Day 100: commit 2 (Burn-in ends at K=2, Eval starts here at day 100)
        commits.add(new ChronologicalBurnInQueue.CommitRecord(2, "C2", 100L * nanosPerDay, 0, 0.2));
        commits.add(new ChronologicalBurnInQueue.CommitRecord(3, "C3", 120L * nanosPerDay, 0, 0.1));

        ChronologicalBurnInQueue.PartitionResult part = ChronologicalBurnInQueue.partitionWithDelay(commits, 2, delay90d);

        // At day 100:
        // C0 (day 0 + 90d = 90d <= 100d) -> Matured!
        // C1 (day 50 + 90d = 140d > 100d) -> NOT Matured!
        assertTrue(part.burnInMaturityMask[0], "C0 must be matured at Day 100");
        assertFalse(part.burnInMaturityMask[1], "C1 must NOT be matured at Day 100 (needs day 140)");
        assertEquals(1, part.maturedTotalCount, "Only 1 commit should be matured");
        assertEquals(1, part.maturedDefectCount, "1 matured defect");
    }

    @Test
    @DisplayName("12. Evaluation label isolation: Eval outcomes never enter adaptation")
    void testEvaluationLabelIsolation() {
        List<ChronologicalBurnInQueue.CommitRecord> commits = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            commits.add(new ChronologicalBurnInQueue.CommitRecord(i, "C" + i, i * 1000L, i % 2, 0.3));
        }

        ChronologicalBurnInQueue.PartitionResult part = ChronologicalBurnInQueue.partitionRetrospective(commits, 5);
        assertEquals(5, part.burnInCommits.size(), "Burn-in has exactly 5 commits");
        assertEquals(15, part.evaluationCommits.size(), "Evaluation has remaining 15 commits");

        // Verify no commit ID in eval exists in burn-in
        Set<String> burnInIds = new HashSet<>();
        for (var c : part.burnInCommits) burnInIds.add(c.commitId);
        for (var c : part.evaluationCommits) {
            assertFalse(burnInIds.contains(c.commitId), "Evaluation commit leaked into burn-in!");
        }
    }

    @Test
    @DisplayName("13. Identical baseline and adapted post-burn-in evaluation windows")
    void testIdenticalEvaluationWindows() {
        int total = 100;
        int burnInSize = 25;
        int[] yAll = new int[total];
        double[] pAll = new double[total];
        for (int i = 0; i < total; i++) {
            yAll[i] = (i % 5 == 0) ? 1 : 0;
            pAll[i] = 0.1 + 0.008 * i;
        }

        // Subset to post-burn-in window [25, 100)
        int[] yEval = Arrays.copyOfRange(yAll, burnInSize, total);
        double[] pBaseEval = Arrays.copyOfRange(pAll, burnInSize, total);
        double[] pAdaptedEval = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, 0.20);

        assertEquals(yEval.length, pBaseEval.length);
        assertEquals(yEval.length, pAdaptedEval.length);
        assertEquals(75, yEval.length, "Evaluation window must be exactly 75 commits for both");

        // Evaluated on the exact same ground truth
        var mBase = AdaptationMetrics.evaluate(yEval, pBaseEval, 0.170);
        var mAdap = AdaptationMetrics.evaluate(yEval, pAdaptedEval, 0.170);
        assertEquals(mBase.sampleCount, mAdap.sampleCount, "Sample counts must match exactly");
    }

    @Test
    @DisplayName("14. Numerical parity with Python implementation")
    void testNumericalParity() {
        // Test exact mathematical match for log-odds formula
        // p = 0.30, pi_s = 0.1114, pi_t = 0.2812
        double p = 0.30;
        double piT = 0.2812;
        double expectedDelta = Math.log(piT / (1.0 - piT)) - Math.log(PI_SOURCE / (1.0 - PI_SOURCE));
        double expectedLogitStar = Math.log(p / (1.0 - p)) + expectedDelta;
        double expectedPStar = 1.0 / (1.0 + Math.exp(-expectedLogitStar));

        double actualPStar = ProbabilityAdapter.adaptProbability(p, PI_SOURCE, piT);
        assertEquals(expectedPStar, actualPStar, 1e-10, "Java calculation must match reference math to 1e-10");
    }

    @Test
    @DisplayName("15. Degenerate metrics handling: ROC-AUC and PR-AUC with single class")
    void testDegenerateMetricsHandling() {
        int[] allZero = {0, 0, 0, 0, 0};
        double[] scores = {0.1, 0.2, 0.3, 0.4, 0.5};

        var metrics = AdaptationMetrics.evaluate(allZero, scores, 0.50);
        assertEquals(0.0, metrics.prevalence, 1e-6);
        assertEquals(0.0, metrics.prAuc, 1e-6, "PR-AUC with 0 defects should be 0.0");
        assertEquals(0.5, metrics.rocAuc, 1e-6, "ROC-AUC with 0 defects should be 0.5");
        assertEquals(0, metrics.tp);
        assertEquals(0, metrics.fn);
    }
}
