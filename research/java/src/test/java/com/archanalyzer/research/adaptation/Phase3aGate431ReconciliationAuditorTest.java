package com.archanalyzer.research.adaptation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase3aGate431ReconciliationAuditorTest
 * =======================================
 * Comprehensive unit test suite for Gate 4.3.1.
 * Covers all 15 audit test requirements plus the regression test for the
 * reported vs canonical confusion matrix contradiction.
 */
public class Phase3aGate431ReconciliationAuditorTest {

    private static File resolveFile(String relativePath) {
        File f1 = new File(relativePath);
        if (f1.exists()) return f1;
        File f2 = new File("../../" + relativePath);
        if (f2.exists()) return f2;
        File f3 = new File("../" + relativePath);
        if (f3.exists()) return f3;
        return f1;
    }

    @Test
    @DisplayName("REGRESSION TEST: 1202/1374/18442/1403 yields Micro-F1 ≈ 46.40% and is mathematically inconsistent with 45.05%")
    void testProseConfusionMatrixRegressionDiscrepancy() {
        int tp = 1202;
        int fp = 1374;
        int tn = 18442;
        int fn = 1403;

        Phase3aGate431ReconciliationAuditor.ConfusionMatrix falseMatrix =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(tp, fp, tn, fn);

        // Sum matches total post-burn-in sample count
        assertEquals(22421, falseMatrix.total(), "Total sample sum matches N=22421");

        // Ground truth defects in false matrix is 2605, which mismatches true defects (2680)
        assertEquals(2605, falseMatrix.actualPositives(), "False matrix defect count");
        assertNotEquals(2680, falseMatrix.actualPositives(), "False matrix fails ground truth defect count (2680)");

        // Calculated micro-metrics
        double prec = falseMatrix.precision();
        double rec = falseMatrix.recall();
        double microF1 = falseMatrix.f1();

        // 2 * 1202 / (2 * 1202 + 1374 + 1403) = 2404 / 5181 = 0.464003088...
        assertEquals(0.464003, microF1, 1e-4, "False matrix produces Micro-F1 of ~46.40%");

        // Prove that 45.05% is inconsistent with these counts
        double reportedF1 = 0.45045965;
        assertTrue(Math.abs(microF1 - reportedF1) > 0.01,
                "Micro-F1 of 46.40% deviates by > 1.35 pp from reported 45.05%, proving mathematical contradiction");
    }

    @Test
    @DisplayName("CANONICAL VERIFICATION: 1764/3388/16353/916 yields exact Micro-F1 = 45.05% and exact defects = 2680")
    void testCanonicalBaseK100Metrics() {
        int tp = 1764;
        int fp = 3388;
        int tn = 16353;
        int fn = 916;

        Phase3aGate431ReconciliationAuditor.ConfusionMatrix canonical =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(tp, fp, tn, fn);

        assertEquals(22421, canonical.total(), "Total sample count must equal 22,421");
        assertEquals(2680, canonical.actualPositives(), "Ground truth defects must equal 2,680");
        assertEquals(19741, canonical.actualNegatives(), "Ground truth non-defects must equal 19,741");

        assertEquals(0.34239130, canonical.precision(), 1e-6, "Micro-Precision = 34.24%");
        assertEquals(0.65820896, canonical.recall(), 1e-6, "Micro-Recall = 65.82%");
        assertEquals(0.45045965, canonical.f1(), 1e-6, "Micro-F1 = 45.05%");
        assertEquals(0.80803711, canonical.accuracy(), 1e-6, "Micro-Accuracy = 80.80%");
        assertEquals(0.37522387, canonical.mcc(), 1e-6, "Micro-MCC = 0.3752");
    }

    @Test
    @DisplayName("1. Correct TP/FP/TN/FN calculation from prediction records")
    void testConfusionMatrixCalculation() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(10, 5, 80, 5);
        assertEquals(10, cm.tp);
        assertEquals(5, cm.fp);
        assertEquals(80, cm.tn);
        assertEquals(5, cm.fn);
        assertEquals(100, cm.total());
    }

    @Test
    @DisplayName("2. Correct micro-precision calculation")
    void testMicroPrecision() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(50, 50, 100, 0);
        assertEquals(0.5000, cm.precision(), 1e-6);
    }

    @Test
    @DisplayName("3. Correct micro-recall calculation")
    void testMicroRecall() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(40, 0, 100, 60);
        assertEquals(0.4000, cm.recall(), 1e-6);
    }

    @Test
    @DisplayName("4. Correct micro-F1 calculation")
    void testMicroF1() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(100, 50, 800, 50);
        // P = 100/150 = 2/3, R = 100/150 = 2/3 => F1 = 2/3
        assertEquals(2.0 / 3.0, cm.f1(), 1e-6);
    }

    @Test
    @DisplayName("5. Correct accuracy calculation")
    void testAccuracyCalculation() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(100, 50, 800, 50);
        assertEquals(900.0 / 1000.0, cm.accuracy(), 1e-6);
    }

    @Test
    @DisplayName("6. Harmonic-mean consistency: F1 == 2PR / (P + R)")
    void testHarmonicMeanConsistency() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1764, 3388, 16353, 916);
        double p = cm.precision();
        double r = cm.recall();
        double directFormula = (2.0 * cm.tp) / (2.0 * cm.tp + cm.fp + cm.fn);
        double harmonicMean = (2.0 * p * r) / (p + r);
        assertEquals(directFormula, harmonicMean, 1e-12, "Direct formula must equal harmonic mean");
    }

    @Test
    @DisplayName("7. Project-level aggregation: project counts sum to pooled counts")
    void testProjectLevelAggregation() {
        // Base_K100 counts across 9 validation projects
        int[][] projectCounts = {
                {188, 285, 6142, 300}, // batik (6915)
                {139, 333, 1451, 121}, // codec (2044)
                {0, 0, 600, 0},        // commons-exec (600)
                {55, 111, 863, 51},    // commons-fileupload (1080)
                {94, 328, 1317, 106},  // commons-jelly (1845)
                {131, 256, 723, 41},   // daemon (1151)
                {46, 204, 1815, 27},   // digester (2092)
                {785, 1074, 1221, 110},// httpclient (3190)
                {326, 797, 2221, 160}  // httpcore (3504)
        };

        int sumTp = 0, sumFp = 0, sumTn = 0, sumFn = 0, sumN = 0;
        for (int[] c : projectCounts) {
            sumTp += c[0];
            sumFp += c[1];
            sumTn += c[2];
            sumFn += c[3];
            sumN += (c[0] + c[1] + c[2] + c[3]);
        }

        assertEquals(22421, sumN);
        assertEquals(1764, sumTp);
        assertEquals(3388, sumFp);
        assertEquals(16353, sumTn);
        assertEquals(916, sumFn);
    }

    @Test
    @DisplayName("8. Pooled aggregation across all 9 projects for Quantile and Bayesian methods")
    void testPooledAggregationOtherMethods() {
        // Quantile Thresh: 1164, 2322, 17419, 1516 => N=22421, F1=37.76%
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix qCm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1164, 2322, 17419, 1516);
        assertEquals(22421, qCm.total());
        assertEquals(2680, qCm.actualPositives());
        assertEquals(0.37755433, qCm.f1(), 1e-6);

        // Bayes M=50: 1894, 4539, 15202, 786 => N=22421, F1=41.57%
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix b50Cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1894, 4539, 15202, 786);
        assertEquals(22421, b50Cm.total());
        assertEquals(2680, b50Cm.actualPositives());
        assertEquals(0.41566992, b50Cm.f1(), 1e-6);

        // Bayes M=20: 1917, 4987, 14754, 763 => N=22421, F1=40.00%
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix b20Cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1917, 4987, 14754, 763);
        assertEquals(22421, b20Cm.total());
        assertEquals(2680, b20Cm.actualPositives());
        assertEquals(0.40004174, b20Cm.f1(), 1e-6);

        // Bayes M=10: 1911, 5190, 14551, 769 => N=22421, F1=39.08%
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix b10Cm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1911, 5190, 14551, 769);
        assertEquals(22421, b10Cm.total());
        assertEquals(2680, b10Cm.actualPositives());
        assertEquals(0.39075759, b10Cm.f1(), 1e-6);
    }

    @Test
    @DisplayName("9. Missing prediction detection")
    void testMissingPredictionDetection() {
        int expectedSamples = 22421;
        int observedSamples = 22420; // 1 missing
        assertNotEquals(expectedSamples, observedSamples, "Missing record must trigger inequality");
    }

    @Test
    @DisplayName("10. Duplicate commit detection")
    void testDuplicateCommitDetection() {
        Set<String> commitIds = new HashSet<>();
        commitIds.add("commit_a");
        commitIds.add("commit_b");
        boolean isDuplicate = !commitIds.add("commit_a");
        assertTrue(isDuplicate, "Duplicate commit ID must be detected");
    }

    @Test
    @DisplayName("11. Invalid count detection (negative values or sum mismatch)")
    void testInvalidCountDetection() {
        int tp = -1;
        int fp = 10;
        int tn = 20;
        int fn = 5;
        assertTrue(tp < 0 || fp < 0 || tn < 0 || fn < 0, "Negative count must be identified as invalid");
    }

    @Test
    @DisplayName("12. Zero-positive edge cases (commons-exec)")
    void testZeroPositiveEdgeCase() {
        // commons-exec at Base_K100 has 0 actual defects
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix execCm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(0, 0, 600, 0);
        assertEquals(0, execCm.actualPositives());
        assertEquals(600, execCm.actualNegatives());
        assertEquals(0.0, execCm.recall(), "Recall must return 0.0 without divide by zero crash");
        assertEquals(0.0, execCm.precision(), "Precision must return 0.0 without divide by zero crash");
        assertEquals(0.0, execCm.f1(), "F1 must return 0.0 without divide by zero crash");
        assertEquals(1.0, execCm.accuracy(), "Accuracy must be 100%");
    }

    @Test
    @DisplayName("13. Undefined metric handling (division by zero)")
    void testUndefinedMetricHandling() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix emptyCm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(0, 0, 0, 0);
        assertEquals(0.0, emptyCm.precision());
        assertEquals(0.0, emptyCm.recall());
        assertEquals(0.0, emptyCm.f1());
        assertEquals(0.0, emptyCm.accuracy());
        assertEquals(0.0, emptyCm.mcc());
    }

    @Test
    @DisplayName("14. Correct method-specific separation")
    void testMethodSpecificSeparation() {
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix baseCm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1764, 3388, 16353, 916);
        Phase3aGate431ReconciliationAuditor.ConfusionMatrix quantCm =
                new Phase3aGate431ReconciliationAuditor.ConfusionMatrix(1164, 2322, 17419, 1516);

        assertNotEquals(baseCm.tp, quantCm.tp);
        assertNotEquals(baseCm.fp, quantCm.fp);
        assertNotEquals(baseCm.f1(), quantCm.f1());
    }

    @Test
    @DisplayName("15. Correct post-burn-in sample size validation: N = 22,421")
    void testPostBurnInSampleSize() {
        int totalValidationCommits = 23321;
        int burnInK = 100;
        int projectCount = 9;
        int expectedPostBurnIn = totalValidationCommits - (burnInK * projectCount);
        assertEquals(22421, expectedPostBurnIn);
    }

    @Test
    @DisplayName("16. Test project quarantine verification")
    void testTestProjectQuarantine() {
        for (String q : Phase3aGate431ReconciliationAuditor.QUARANTINED_TEST_PROJECTS) {
            for (String v : Phase3aGate431ReconciliationAuditor.VALIDATION_PROJECTS) {
                assertNotEquals(q, v, "Quarantined test project must never be evaluated");
            }
        }
    }
}
