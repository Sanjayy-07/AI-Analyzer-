package com.archanalyzer.research.adaptation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Phase3aPilotRunner
 * ==================
 * Java-first pilot benchmark orchestrator for Phase 3A Prevalence Adaptation.
 * Evaluates the 3 pilot projects:
 * - org.apache:batik (medium prevalence: 7.01%, high velocity)
 * - org.apache:digester (low prevalence: 5.11%)
 * - org.apache:httpclient (high prevalence: 28.12%)
 *
 * Implements:
 * - Baselines A0, A1, A2
 * - Zero-label estimators: A3-Soft, A3-BBSE, A3-EM
 * - Burn-in estimators: B1 (25), B2 (50), B3 (100), B4 (200), B5 (500) under both
 *   Retrospective maturation and strict 90-day causal delay.
 * - Burn-in calibration: B-Platt (K=100) and B-Thresh (K=100).
 * - Generates research/reports/phase3a_pilot_results.json with atomic writing.
 */
public class Phase3aPilotRunner {

    public static final double PI_SOURCE = 0.111394;
    public static final double FROZEN_TAU = 0.170;
    public static final double SOURCE_TPR = 0.6618;
    public static final double SOURCE_FPR = 0.1650;
    public static final double DEFAULT_M = 10.0;

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("PHASE 3A: JAVA-FIRST ADAPTATION PILOT BENCHMARK (Gate 3)");
        System.out.println("Pilot Projects: batik, digester, httpclient");
        System.out.println("================================================================================");

        long tStart = System.currentTimeMillis();
        String csvPath = "research/data/pilot_raw_predictions.csv";
        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            System.err.println("Error: Pilot predictions CSV not found at: " + csvPath);
            System.exit(1);
        }

        // 1. Load CSV data
        Map<String, List<ChronologicalBurnInQueue.CommitRecord>> projectCommits = loadPredictionsCSV(csvFile);
        System.out.println("Loaded predictions for " + projectCommits.size() + " pilot projects.");

        Map<String, Object> pilotResults = new LinkedHashMap<>();
        pilotResults.put("benchmark", "Phase 3A Prevalence Adaptation Pilot (Gate 3)");
        pilotResults.put("timestamp", new Date().toString());
        pilotResults.put("source_training_prevalence", PI_SOURCE);
        pilotResults.put("frozen_source_threshold", FROZEN_TAU);

        Map<String, Object> projectEvaluations = new LinkedHashMap<>();

        // 2. Run Pilot Evaluation per project
        for (Map.Entry<String, List<ChronologicalBurnInQueue.CommitRecord>> entry : projectCommits.entrySet()) {
            String projectId = entry.getKey();
            List<ChronologicalBurnInQueue.CommitRecord> rawCommits = entry.getValue();

            // Strict chronological sorting
            List<ChronologicalBurnInQueue.CommitRecord> sortedCommits = ChronologicalBurnInQueue.sortChronologically(rawCommits);
            int n = sortedCommits.size();

            int[] yAll = new int[n];
            double[] pAll = new double[n];
            for (int i = 0; i < n; i++) {
                yAll[i] = sortedCommits.get(i).isFaultInducing;
                pAll[i] = sortedCommits.get(i).baselineProbability;
            }

            int totalFaults = 0;
            for (int y : yAll) if (y == 1) totalFaults++;
            double truePrevalence = (double) totalFaults / n;

            System.out.printf("\n--- Project: %s (N=%d, True Prevalence=%.2f%%) ---\n",
                    projectId, n, truePrevalence * 100.0);

            Map<String, Object> projReport = new LinkedHashMap<>();
            projReport.put("total_commits", n);
            projReport.put("total_defects", totalFaults);
            projReport.put("true_prevalence", truePrevalence);

            Map<String, Object> methodOutputs = new LinkedHashMap<>();

            // -------------------------------------------------------------
            // A. Full Project Baselines (Zero Labels)
            // -------------------------------------------------------------
            // A0: Frozen Source at 0.50
            var m_A0 = AdaptationMetrics.evaluate(yAll, pAll, 0.50);
            methodOutputs.put("A0_Frozen_050", wrapResult("A0", 0.50, PI_SOURCE, m_A0, n, totalFaults));

            // A1: Frozen Source at tau* = 0.170
            var m_A1 = AdaptationMetrics.evaluate(yAll, pAll, FROZEN_TAU);
            methodOutputs.put("A1_Frozen_TauStar", wrapResult("A1", FROZEN_TAU, PI_SOURCE, m_A1, n, totalFaults));
            System.out.printf("  A1 Baseline (Frozen Tau*=%.3f): F1=%.4f, P=%.4f, R=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    FROZEN_TAU, m_A1.f1, m_A1.precision, m_A1.recall, m_A1.prAuc, m_A1.brierScore, m_A1.ece);

            // A3-Soft: Unlabeled Soft Mean Adaptation
            double piSoft = PrevalenceEstimator.estimateSoftMean(pAll);
            double[] pAdaptedSoft = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piSoft);
            var m_A3Soft = AdaptationMetrics.evaluate(yAll, pAdaptedSoft, FROZEN_TAU);
            methodOutputs.put("A3_SoftMean", wrapResult("A3-Soft", FROZEN_TAU, piSoft, m_A3Soft, n, totalFaults));
            System.out.printf("  A3-Soft Mean (Pi_hat=%.4f):   F1=%.4f, P=%.4f, R=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    piSoft, m_A3Soft.f1, m_A3Soft.precision, m_A3Soft.recall, m_A3Soft.prAuc, m_A3Soft.brierScore, m_A3Soft.ece);

            // A3-BBSE: Black Box Shift Estimator
            double piBBSE = PrevalenceEstimator.estimateBBSE(pAll, FROZEN_TAU, SOURCE_TPR, SOURCE_FPR);
            double[] pAdaptedBBSE = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piBBSE);
            var m_A3BBSE = AdaptationMetrics.evaluate(yAll, pAdaptedBBSE, FROZEN_TAU);
            methodOutputs.put("A3_BBSE", wrapResult("A3-BBSE", FROZEN_TAU, piBBSE, m_A3BBSE, n, totalFaults));
            System.out.printf("  A3-BBSE      (Pi_hat=%.4f):   F1=%.4f, P=%.4f, R=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    piBBSE, m_A3BBSE.f1, m_A3BBSE.precision, m_A3BBSE.recall, m_A3BBSE.prAuc, m_A3BBSE.brierScore, m_A3BBSE.ece);

            // A3-EM: Saerens Expectation-Maximization
            double piEM = PrevalenceEstimator.estimateSaerensEM(pAll, PI_SOURCE, 50, 1e-5);
            double[] pAdaptedEM = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piEM);
            var m_A3EM = AdaptationMetrics.evaluate(yAll, pAdaptedEM, FROZEN_TAU);
            methodOutputs.put("A3_SaerensEM", wrapResult("A3-EM", FROZEN_TAU, piEM, m_A3EM, n, totalFaults));
            System.out.printf("  A3-Saerens EM(Pi_hat=%.4f):   F1=%.4f, P=%.4f, R=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    piEM, m_A3EM.f1, m_A3EM.precision, m_A3EM.recall, m_A3EM.prAuc, m_A3EM.brierScore, m_A3EM.ece);

            // -------------------------------------------------------------
            // B. Burn-in Sample Efficiency (K in [25, 50, 100, 200, 500])
            // -------------------------------------------------------------
            int[] burnInSizes = {25, 50, 100, 200, 500};
            for (int K : burnInSizes) {
                if (K >= n - 50) continue; // Skip if too close to project total

                // Setting B.1: Retrospective Burn-in (matured prior to deployment)
                var partRetro = ChronologicalBurnInQueue.partitionRetrospective(sortedCommits, K);
                int evalSize = partRetro.evaluationCommits.size();
                int[] yEval = new int[evalSize];
                double[] pBaseEval = new double[evalSize];
                for (int i = 0; i < evalSize; i++) {
                    yEval[i] = partRetro.evaluationCommits.get(i).isFaultInducing;
                    pBaseEval[i] = partRetro.evaluationCommits.get(i).baselineProbability;
                }

                int evalFaults = 0;
                for (int y : yEval) if (y == 1) evalFaults++;

                // Matured labels from burn-in
                int[] burnInLabels = new int[K];
                for (int i = 0; i < K; i++) burnInLabels[i] = partRetro.burnInCommits.get(i).isFaultInducing;

                double piBayesRetro = PrevalenceEstimator.estimateBayesianBetaBinomial(
                        burnInLabels, partRetro.burnInMaturityMask, PI_SOURCE, DEFAULT_M);
                double[] pAdaptedRetro = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piBayesRetro);

                // Baseline evaluated on EXACT SAME evaluation window [K, N)
                var m_BaseOnEval = AdaptationMetrics.evaluate(yEval, pBaseEval, FROZEN_TAU);
                var m_AdaptedRetro = AdaptationMetrics.evaluate(yEval, pAdaptedRetro, FROZEN_TAU);

                Map<String, Object> retroEntry = new LinkedHashMap<>();
                retroEntry.put("burn_in_K", K);
                retroEntry.put("burn_in_matured_labels", partRetro.maturedTotalCount);
                retroEntry.put("burn_in_matured_faults", partRetro.maturedDefectCount);
                retroEntry.put("eval_commits", evalSize);
                retroEntry.put("eval_defects", evalFaults);
                retroEntry.put("eval_true_prevalence", (double) evalFaults / evalSize);
                retroEntry.put("estimated_prevalence", piBayesRetro);
                retroEntry.put("baseline_metrics_on_eval_window", m_BaseOnEval.toMap());
                retroEntry.put("adapted_metrics_on_eval_window", m_AdaptedRetro.toMap());
                methodOutputs.put("B_Bayes_Retro_K" + K, retroEntry);

                // Setting B.2: Strict 90-Day Delay Queue
                var partDelay = ChronologicalBurnInQueue.partitionWithDelay(sortedCommits, K, ChronologicalBurnInQueue.DEFAULT_DELAY_NANOS);
                double piBayesDelay = PrevalenceEstimator.estimateBayesianBetaBinomial(
                        burnInLabels, partDelay.burnInMaturityMask, PI_SOURCE, DEFAULT_M);
                double[] pAdaptedDelay = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piBayesDelay);
                var m_AdaptedDelay = AdaptationMetrics.evaluate(yEval, pAdaptedDelay, FROZEN_TAU);

                Map<String, Object> delayEntry = new LinkedHashMap<>();
                delayEntry.put("burn_in_K", K);
                delayEntry.put("burn_in_matured_labels", partDelay.maturedTotalCount);
                delayEntry.put("burn_in_matured_faults", partDelay.maturedDefectCount);
                delayEntry.put("eval_commits", evalSize);
                delayEntry.put("eval_defects", evalFaults);
                delayEntry.put("eval_true_prevalence", (double) evalFaults / evalSize);
                delayEntry.put("estimated_prevalence", piBayesDelay);
                delayEntry.put("baseline_metrics_on_eval_window", m_BaseOnEval.toMap());
                delayEntry.put("adapted_metrics_on_eval_window", m_AdaptedDelay.toMap());
                methodOutputs.put("B_Bayes_Delay90d_K" + K, delayEntry);

                System.out.printf("  Burn-in K=%3d: RetroPi=%.4f (F1: Base=%.4f -> Adap=%.4f) | Delay90d (Matured=%d/%d, Pi=%.4f, F1=%.4f)\n",
                        K, piBayesRetro, m_BaseOnEval.f1, m_AdaptedRetro.f1,
                        partDelay.maturedTotalCount, K, piBayesDelay, m_AdaptedDelay.f1);

                // For K = 100: Also evaluate Platt scaling and Threshold adaptation
                if (K == 100 && partRetro.maturedDefectCount > 0) {
                    double[] burnInProbas = new double[K];
                    for (int i = 0; i < K; i++) burnInProbas[i] = partRetro.burnInCommits.get(i).baselineProbability;

                    // Platt Scaling
                    double[] plattParams = ProbabilityAdapter.fitPlattScaling(burnInProbas, burnInLabels, 100);
                    double[] pPlattEval = ProbabilityAdapter.applyPlattScaling(pBaseEval, plattParams[0], plattParams[1]);
                    var m_Platt = AdaptationMetrics.evaluate(yEval, pPlattEval, FROZEN_TAU);

                    Map<String, Object> plattEntry = new LinkedHashMap<>();
                    plattEntry.put("platt_a", plattParams[0]);
                    plattEntry.put("platt_b", plattParams[1]);
                    plattEntry.put("baseline_metrics", m_BaseOnEval.toMap());
                    plattEntry.put("platt_metrics", m_Platt.toMap());
                    methodOutputs.put("B_Platt_K100", plattEntry);
                    System.out.printf("    -> B-Platt K=100 (a=%.3f, b=%.3f): F1=%.4f, Brier=%.4f, ECE=%.4f\n",
                            plattParams[0], plattParams[1], m_Platt.f1, m_Platt.brierScore, m_Platt.ece);

                    // Threshold adaptation via quantile matching
                    double adaptedTau = ProbabilityAdapter.computeQuantileThreshold(pBaseEval, piBayesRetro);
                    var m_Thresh = AdaptationMetrics.evaluate(yEval, pBaseEval, adaptedTau);
                    Map<String, Object> threshEntry = new LinkedHashMap<>();
                    threshEntry.put("adapted_threshold", adaptedTau);
                    threshEntry.put("metrics_at_adapted_tau", m_Thresh.toMap());
                    methodOutputs.put("B_Thresh_K100", threshEntry);
                    System.out.printf("    -> B-Thresh K=100 (Tau=%.3f): F1=%.4f, Precision=%.4f, Recall=%.4f\n",
                            adaptedTau, m_Thresh.f1, m_Thresh.precision, m_Thresh.recall);
                }
            }

            projReport.put("methods", methodOutputs);
            projectEvaluations.put(projectId, projReport);
        }

        pilotResults.put("project_evaluations", projectEvaluations);
        long elapsedMs = System.currentTimeMillis() - tStart;
        pilotResults.put("execution_time_ms", elapsedMs);

        // 3. Serialize Results atomically
        String reportJsonPath = "research/reports/phase3a_pilot_results.json";
        saveJsonAtomically(reportJsonPath, pilotResults);
        System.out.printf("\n[OK] Phase 3A Java Pilot Benchmark Completed in %.2fs. Saved to: %s\n",
                elapsedMs / 1000.0, reportJsonPath);
    }

    private static Map<String, Object> wrapResult(
            String methodId, double threshold, double estPi,
            AdaptationMetrics.MetricsResult m, int totalCommits, int totalDefects
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method_id", methodId);
        out.put("operating_threshold", threshold);
        out.put("estimated_prevalence", estPi);
        out.put("total_commits", totalCommits);
        out.put("total_defects", totalDefects);
        out.put("true_prevalence", (double) totalDefects / totalCommits);
        out.put("metrics", m.toMap());
        return out;
    }

    private static Map<String, List<ChronologicalBurnInQueue.CommitRecord>> loadPredictionsCSV(File file) throws IOException {
        Map<String, List<ChronologicalBurnInQueue.CommitRecord>> map = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 7) continue;
                String proj = tokens[0].trim();
                int origIdx = Integer.parseInt(tokens[1].trim());
                String commitId = tokens[2].trim();
                long tsNanos = Long.parseLong(tokens[3].trim());
                int isFault = Integer.parseInt(tokens[5].trim());
                double proba = Double.parseDouble(tokens[6].trim());

                map.computeIfAbsent(proj, k -> new ArrayList<>())
                        .add(new ChronologicalBurnInQueue.CommitRecord(origIdx, commitId, tsNanos, isFault, proba));
            }
        }
        return map;
    }

    private static void saveJsonAtomically(String filepath, Map<String, Object> data) throws IOException {
        File targetFile = new File(filepath);
        targetFile.getParentFile().mkdirs();
        File tempFile = File.createTempFile("pilot_", ".tmp", targetFile.getParentFile());

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writeSimpleJson(writer, data);
            writer.flush();
        }

        if (targetFile.exists()) {
            targetFile.delete();
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("Failed to atomically rename " + tempFile + " to " + targetFile);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeSimpleJson(Writer w, Object obj) throws IOException {
        if (obj == null) {
            w.write("null");
        } else if (obj instanceof String) {
            w.write("\"" + escapeJson((String) obj) + "\"");
        } else if (obj instanceof Number || obj instanceof Boolean) {
            w.write(obj.toString());
        } else if (obj instanceof Map) {
            w.write("{\n");
            Map<String, Object> map = (Map<String, Object>) obj;
            int count = 0;
            int total = map.size();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                w.write("\"" + escapeJson(e.getKey()) + "\": ");
                writeSimpleJson(w, e.getValue());
                count++;
                if (count < total) w.write(",\n");
                else w.write("\n");
            }
            w.write("}");
        } else if (obj instanceof List) {
            w.write("[\n");
            List<Object> list = (List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                writeSimpleJson(w, list.get(i));
                if (i < list.size() - 1) w.write(",\n");
                else w.write("\n");
            }
            w.write("]");
        } else {
            w.write("\"" + escapeJson(obj.toString()) + "\"");
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
