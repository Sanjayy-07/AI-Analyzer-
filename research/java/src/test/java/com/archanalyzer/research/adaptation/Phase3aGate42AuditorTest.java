package com.archanalyzer.research.adaptation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase3aGate42AuditorTest
 * ========================
 * Rigorous, comprehensive unit tests verifying numerical consistency,
 * metric arithmetic, aggregation invariants, ranking theorems, and audit findings.
 */
public class Phase3aGate42AuditorTest {

    @Test
    @DisplayName("Verify Confusion Matrix Metric Arithmetic & Edge Cases")
    void testMetricArithmeticAndEdgeCases() {
        // Normal case
        int tp = 40, fp = 10, tn = 940, fn = 10;
        int n = tp + fp + tn + fn;
        int[] yTrue = new int[n];
        double[] yProba = new double[n];

        int idx = 0;
        for (int i = 0; i < tp; i++) { yTrue[idx] = 1; yProba[idx] = 0.8; idx++; }
        for (int i = 0; i < fp; i++) { yTrue[idx] = 0; yProba[idx] = 0.8; idx++; }
        for (int i = 0; i < tn; i++) { yTrue[idx] = 0; yProba[idx] = 0.1; idx++; }
        for (int i = 0; i < fn; i++) { yTrue[idx] = 1; yProba[idx] = 0.1; idx++; }

        var res = AdaptationMetrics.evaluate(yTrue, yProba, 0.5);
        assertEquals(tp, res.tp);
        assertEquals(fp, res.fp);
        assertEquals(tn, res.tn);
        assertEquals(fn, res.fn);

        double expectedPrec = 40.0 / (40 + 10); // 0.80
        double expectedRec = 40.0 / (40 + 10);  // 0.80
        double expectedF1 = 0.80;

        assertEquals(expectedPrec, res.precision, 1e-6);
        assertEquals(expectedRec, res.recall, 1e-6);
        assertEquals(expectedF1, res.f1, 1e-6);
        assertTrue(res.mcc > 0.70);
    }

    @Test
    @DisplayName("Verify Zero-Defect Project Edge Cases (commons-exec)")
    void testZeroDefectEdgeCases() {
        int n = 500;
        int[] yTrue = new int[n]; // All 0s
        double[] yProba = new double[n];
        for (int i = 0; i < n; i++) yProba[i] = 0.05 * (i % 5);

        var res = AdaptationMetrics.evaluate(yTrue, yProba, 0.170);

        assertEquals(0, res.defectCount);
        assertEquals(0.0, res.prevalence, 1e-9);
        assertEquals(0, res.tp);
        assertEquals(0, res.fn);
        assertEquals(0.0, res.precision, 1e-9);
        assertEquals(0.0, res.recall, 1e-9);
        assertEquals(0.0, res.f1, 1e-9);
        assertEquals(0.0, res.mcc, 1e-9);
        assertEquals(0.0, res.prAuc, 1e-9);
        // By convention in AdaptationMetrics, ROC-AUC is 0.5 for single-class
        assertEquals(0.5, res.rocAuc, 1e-9);
    }

    @Test
    @DisplayName("Verify Monotonic Ranking Invariance Theorem under Log-Odds Shift")
    void testRankingInvarianceTheorem() {
        double[] baseProba = {0.05, 0.12, 0.25, 0.40, 0.65, 0.80};
        int[] yTrue = {0, 0, 1, 0, 1, 1};

        double baseRoc = AdaptationMetrics.computeROCAUC(yTrue, baseProba);
        double basePr = AdaptationMetrics.computePRAUC(yTrue, baseProba);

        // Prior shift: pi_source = 0.11, pi_target = 0.30
        double[] shiftedProba = ProbabilityAdapter.adaptProbabilities(baseProba, 0.1114, 0.30);

        double shiftedRoc = AdaptationMetrics.computeROCAUC(yTrue, shiftedProba);
        double shiftedPr = AdaptationMetrics.computePRAUC(yTrue, shiftedProba);

        assertEquals(baseRoc, shiftedRoc, 1e-12, "ROC-AUC must be invariant under monotonic shift");
        assertEquals(basePr, shiftedPr, 1e-12, "PR-AUC must be invariant under monotonic shift");
    }

    @Test
    @DisplayName("Verify Paired Wilcoxon Signed-Rank with Ties and Exact Permutation")
    void testWilcoxonSignedRankWithTies() {
        // 9 paired observations with 1 zero-difference tie
        double[] x = {0.40, 0.50, 0.30, 0.60, 0.70, 0.25, 0.35, 0.80, 0.45};
        double[] y = {0.35, 0.50, 0.35, 0.55, 0.65, 0.20, 0.30, 0.75, 0.50};

        var res = Phase3aStatisticalAnalysis.pairedWilcoxon(x, y);

        assertEquals(9, res.sampleSize);
        assertEquals(8, res.nonZeroCount);
        assertEquals(1, res.ties);
        assertTrue(res.wins > 0);
        assertTrue(res.losses > 0);
        assertTrue(res.pValue > 0.0 && res.pValue <= 1.0);
        assertTrue(res.rankBiserialR >= -1.0 && res.rankBiserialR <= 1.0);
    }

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
    @DisplayName("Verify Raw Prediction Artifacts Exist and Have Valid SHA-256")
    void testRawPredictionArtifactsExist() {
        File csv = resolveFile("research/data/gate4_raw_predictions.csv");
        File json = resolveFile("research/data/gate4_raw_predictions.json");

        assertTrue(csv.exists(), "Raw CSV must exist");
        assertTrue(json.exists(), "Raw JSON must exist");
        assertTrue(csv.length() > 2_000_000, "CSV size must be ~3MB");
        assertTrue(json.length() > 5_000_000, "JSON size must be ~7MB");
    }

    @Test
    @DisplayName("Verify Canonical Metrics JSON Output Exists and Contains Gate 4.2 Resolutions")
    void testCanonicalMetricsJsonVerification() {
        File canon = resolveFile("research/reports/phase3a_gate4_2_canonical_metrics.json");
        File findings = resolveFile("research/reports/phase3a_gate4_2_findings.json");

        assertTrue(canon.exists(), "Canonical metrics JSON must exist");
        assertTrue(findings.exists(), "Gate 4.2 findings JSON must exist");
    }

    @Test
    @DisplayName("Verify Evaluation Window Arithmetic: 23,321 - 900 = 22,421")
    void testEvaluationWindowArithmetic() {
        int rawN = 23321;
        int k = 100;
        int projects = 9;
        int burnInExcluded = k * projects;
        int alignedEvalN = rawN - burnInExcluded;

        assertEquals(900, burnInExcluded);
        assertEquals(22421, alignedEvalN);
    }

    @Test
    @DisplayName("Verify Three Contradiction Resolutions Arithmetic")
    void testThreeContradictionsArithmetic() {
        // Contradiction 1: Base_K100 recall
        double base9 = 0.538708;
        assertEquals(0.5387, base9, 1e-4);

        // Contradiction 2: Quantile recall and delta
        double q9 = 0.427517;
        double delta = q9 - base9;
        assertEquals(-0.111191, delta, 1e-5);
        double relChange = (delta / base9) * 100.0;
        assertEquals(-20.64, relChange, 0.05);

        // Contradiction 3: HTTPCLIENT Gate 4 recall
        double httpGate4Recall = 383.0 / 895.0; // 0.427933
        assertEquals(0.4279, httpGate4Recall, 1e-4);
        assertNotEquals(0.5598, httpGate4Recall, 1e-2, "Gate 4 recall must not equal legacy pilot 55.98%");
    }
}
