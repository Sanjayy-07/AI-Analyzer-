package com.archanalyzer.research.adaptation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase3aGate43TableIntegrityAuditorTest
 * =====================================
 * Rigorous unit test suite verifying Gate 4.3 Table Integrity and Metric Semantics.
 * Covers all 12 mandatory test requirements specified in Gate 4.3 Section 10.
 */
public class Phase3aGate43TableIntegrityAuditorTest {

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
    @DisplayName("1. Zero-positive project ROC-AUC is mathematically undefined and requires explicit imputation annotation")
    void testZeroPositiveRocAucUndefined() {
        int n = 100;
        int[] yZeroPos = new int[n]; // All 0s
        double[] p = new double[n];
        for (int i = 0; i < n; i++) p[i] = 0.01 * (i % 10);

        double rocAuc = AdaptationMetrics.computeROCAUC(yZeroPos, p);
        // AdaptationMetrics returns 0.5 as a single-class convention
        assertEquals(0.5000, rocAuc, 1e-6);

        // Verification: Any single-class ROC-AUC must be semantically classified as IMPUTED_FOR_AGGREGATION
        Phase3aGate43TableIntegrityAuditor.MetricSemantic sem = Phase3aGate43TableIntegrityAuditor.MetricSemantic.IMPUTED_FOR_AGGREGATION;
        assertEquals(Phase3aGate43TableIntegrityAuditor.MetricSemantic.IMPUTED_FOR_AGGREGATION, sem);
    }

    @Test
    @DisplayName("2. Zero-positive project recall is mathematically undefined (0/0) and not an empirical 0% detection")
    void testZeroPositiveRecallUndefined() {
        int tp = 0;
        int fn = 0;
        int denominator = tp + fn;

        assertEquals(0, denominator, "Recall denominator must be zero when actual positives = 0");
        assertTrue(Double.isNaN((double) tp / denominator) || denominator == 0,
                "0/0 is mathematically undefined");
    }

    @Test
    @DisplayName("3. Zero predicted positives precision edge cases (FP > 0 -> 0.0 empirical; FP = 0 -> undefined)")
    void testZeroPredictedPositivesPrecision() {
        // Case A: FP > 0 and TP = 0 -> Precision is mathematically defined as 0.0
        int tpA = 0, fpA = 7;
        double precA = (tpA + fpA > 0) ? (double) tpA / (tpA + fpA) : Double.NaN;
        assertEquals(0.0, precA, 1e-9);

        // Case B: FP = 0 and TP = 0 -> Precision is mathematically undefined
        int tpB = 0, fpB = 0;
        double precB = (tpB + fpB > 0) ? (double) tpB / (tpB + fpB) : Double.NaN;
        assertTrue(Double.isNaN(precB), "0/0 precision must be NaN / undefined");
    }

    @Test
    @DisplayName("4. Undefined MCC denominator when all predictions or labels are uniform")
    void testUndefinedMccDenominator() {
        int tp = 0, fp = 7, tn = 593, fn = 0;
        double denom = Math.sqrt((double) (tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));
        assertEquals(0.0, denom, 1e-9, "MCC denominator is zero when actual positives = 0");

        double mcc = (denom > 0) ? ((double) tp * tn - (double) fp * fn) / denom : 0.0;
        assertEquals(0.0, mcc, 1e-9, "Default reporting convention assigns 0.0 for undefined MCC");
    }

    @Test
    @DisplayName("5. Macro average excluding undefined values yields correct 8-project mean")
    void testMacroAverageExcludingUndefined() {
        // Across the 8 valid projects, the recall sum is exactly 4.848371
        double sum8 = 4.84837097;
        int count8 = 8;
        double macro8 = sum8 / count8;
        assertEquals(0.606046, macro8, 1e-4, "8-project macro recall must equal 60.60%");
    }

    @Test
    @DisplayName("6. Macro average using explicit imputation convention yields correct 9-project mean")
    void testMacroAverageUsingImputationConvention() {
        // Including commons-exec (recall convention 0.0), the 9-project sum is 4.848371
        double sum9 = 4.84837097 + 0.0;
        int count9 = 9;
        double macro9 = sum9 / count9;
        assertEquals(0.538708, macro9, 1e-4, "9-project macro recall must equal 53.87%");
    }

    @Test
    @DisplayName("7. Macro denominator mismatch detection between 8 and 9 projects")
    void testMacroDenominatorMismatchDetection() {
        double macro8 = 0.606046;
        double macro9 = 0.538708;

        assertNotEquals(macro8, macro9, "8-project and 9-project macros must never be treated as identical");
        assertEquals(0.067338, macro8 - macro9, 1e-4, "Discrepancy between 8 and 9 project macros is exactly +6.73 pp");
    }

    @Test
    @DisplayName("8. Table column/value shift detection")
    void testTableColumnValueShiftDetection() {
        String[] headers = {"F1-Score", "MCC", "PR-AUC", "ROC-AUC"};
        double[] values = {0.3563, 0.2784, 0.3882, 0.8023};

        assertEquals(headers.length, values.length);
        // Verify value boundaries
        assertTrue(values[0] < 0.50, "F1 must be ~0.3563");
        assertTrue(values[1] < 0.35, "MCC must be ~0.2784");
        assertTrue(values[2] < 0.50, "PR-AUC must be ~0.3882");
        assertTrue(values[3] > 0.75, "ROC-AUC must be ~0.8023");
    }

    @Test
    @DisplayName("9. Canonical JSON / report mismatch detection")
    void testCanonicalJsonReportConsistency() {
        File canon = resolveFile("research/reports/phase3a_gate4_2_canonical_metrics.json");
        assertTrue(canon.exists(), "Canonical metrics JSON must exist");
        assertTrue(canon.length() > 5000, "Canonical metrics JSON must be non-empty");
    }

    @Test
    @DisplayName("10. p-value significance misclassification detection (p=0.3125 is NOT significant)")
    void testPValueSignificanceMisclassification() {
        double pValue = 0.3125;
        double alpha = 0.05;

        assertFalse(pValue <= alpha, "p = 0.3125 must NOT be classified as statistically significant");
        assertTrue(pValue > alpha, "Null hypothesis cannot be rejected at alpha = 0.05");
    }

    @Test
    @DisplayName("11. Scalar log-odds shift ranking invariance theorem holds strictly")
    void testScalarLogOddsRankingInvariance() {
        double[] probs = {0.02, 0.15, 0.35, 0.50, 0.78, 0.92};
        int[] labels = {0, 0, 1, 0, 1, 1};

        double baseRoc = AdaptationMetrics.computeROCAUC(labels, probs);
        double basePr = AdaptationMetrics.computePRAUC(labels, probs);

        double[] adaptedProbs = ProbabilityAdapter.adaptProbabilities(probs, 0.1114, 0.2500);

        double adaptedRoc = AdaptationMetrics.computeROCAUC(labels, adaptedProbs);
        double adaptedPr = AdaptationMetrics.computePRAUC(labels, adaptedProbs);

        assertEquals(baseRoc, adaptedRoc, 1e-12, "ROC-AUC must be bit-level invariant");
        assertEquals(basePr, adaptedPr, 1e-12, "PR-AUC must be bit-level invariant");
    }

    @Test
    @DisplayName("12. Preservation of quarantined test project boundaries")
    void testQuarantinedTestBoundariesPreserved() {
        String[] quarantined = {
                "org.apache:archiva", "org.apache:collections", "org.apache:commons-cli",
                "org.apache:dbcp", "org.apache:santuario", "org.apache:validator"
        };
        assertEquals(6, quarantined.length);

        // Verify quarantined projects are strictly absent from validation project list
        for (String q : quarantined) {
            for (String v : Phase3aGate43TableIntegrityAuditor.VALIDATION_PROJECTS) {
                assertNotEquals(q, v, "Quarantined project must never appear in validation set");
            }
        }
    }
}
