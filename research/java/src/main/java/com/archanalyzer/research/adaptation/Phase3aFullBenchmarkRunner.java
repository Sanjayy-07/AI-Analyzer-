package com.archanalyzer.research.adaptation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Phase3aFullBenchmarkRunner
 * ==========================
 * Java-first full nine-project benchmark runner for Phase 3A Gate 4.
 * Evaluates all 9 authorized validation projects:
 * - batik, codec, commons-exec (zero-defect), commons-fileupload, commons-jelly,
 *   daemon, digester, httpclient, httpcore.
 *
 * Implements:
 * - A0: Frozen Baseline (tau=0.50)
 * - A1: Frozen Baseline (tau*=0.170)
 * - A1_Soft: Soft Mean adaptation
 * - A2_Bayes: Bayesian Supervised Adaptation across K in [25, 50, 100, 250, 500],
 *   M in [10, 20, 50], under both Delay-90d and Retrospective maturation.
 * - A3_Thresh: Quantile Threshold Adaptation
 * - A4_Combined: Probability + Threshold Adaptation
 * - Diag_BBSE & Diag_EM: Diagnostics
 * - Paired Wilcoxon signed-rank tests across N=9 projects.
 * - Atomic serialization to JSON.
 */
public class Phase3aFullBenchmarkRunner {

    public static final double PI_SOURCE = 0.111394;
    public static final double FROZEN_TAU = 0.170;
    public static final double SOURCE_TPR = 0.6618;
    public static final double SOURCE_FPR = 0.1650;

    public static final int[] BURN_IN_SIZES = {25, 50, 100, 250, 500};
    public static final double[] BAYES_M_VALUES = {10.0, 20.0, 50.0};

    public static final String[] VALIDATION_PROJECTS = {
            "org.apache:batik",
            "org.apache:codec",
            "org.apache:commons-exec",
            "org.apache:commons-fileupload",
            "org.apache:commons-jelly",
            "org.apache:daemon",
            "org.apache:digester",
            "org.apache:httpclient",
            "org.apache:httpcore"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("PHASE 3A GATE 4: FULL NINE-PROJECT VALIDATION BENCHMARK (Java-First Engine)");
        System.out.println("================================================================================");

        long tStart = System.currentTimeMillis();
        String csvPath = "research/data/gate4_raw_predictions.csv";
        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            System.err.println("Error: Gate 4 raw predictions CSV not found: " + csvPath);
            System.exit(1);
        }

        Map<String, List<ChronologicalBurnInQueue.CommitRecord>> projectData = loadCsv(csvFile);
        System.out.printf("Loaded raw predictions for %d validation projects (Total Commits: %d).\n",
                projectData.size(), projectData.values().stream().mapToInt(List::size).sum());

        Map<String, Object> fullBenchmarkOutput = new LinkedHashMap<>();
        fullBenchmarkOutput.put("benchmark", "Phase 3A Gate 4: Full Nine-Project Validation Benchmark");
        fullBenchmarkOutput.put("timestamp", new Date().toString());
        fullBenchmarkOutput.put("source_training_prevalence", PI_SOURCE);
        fullBenchmarkOutput.put("frozen_threshold_tau_star", FROZEN_TAU);

        Map<String, Object> perProjectResults = new LinkedHashMap<>();

        // Vectors for paired statistical significance tests across N=9 projects
        Map<String, List<Double>> f1Vectors = new LinkedHashMap<>();
        Map<String, List<Double>> brierVectors = new LinkedHashMap<>();
        Map<String, List<Double>> eceVectors = new LinkedHashMap<>();
        Map<String, List<Double>> precVectors = new LinkedHashMap<>();
        Map<String, List<Double>> recVectors = new LinkedHashMap<>();

        List<Double> baselineF1Full = new ArrayList<>();
        List<Double> baselineBrierFull = new ArrayList<>();
        List<Double> baselineEceFull = new ArrayList<>();
        List<Double> softMeanF1Full = new ArrayList<>();
        List<Double> softMeanBrierFull = new ArrayList<>();
        List<Double> softMeanEceFull = new ArrayList<>();

        for (String pId : VALIDATION_PROJECTS) {
            List<ChronologicalBurnInQueue.CommitRecord> rawCommits = projectData.get(pId);
            if (rawCommits == null) {
                throw vintageException("Missing validation project in data: " + pId);
            }

            // Strict chronological sort
            List<ChronologicalBurnInQueue.CommitRecord> sortedCommits = ChronologicalBurnInQueue.sortChronologically(rawCommits);
            int n = sortedCommits.size();

            int[] yAll = new int[n];
            double[] pAll = new double[n];
            int totalFaults = 0;
            for (int i = 0; i < n; i++) {
                yAll[i] = sortedCommits.get(i).isFaultInducing;
                pAll[i] = sortedCommits.get(i).baselineProbability;
                if (yAll[i] == 1) totalFaults++;
            }
            double truePrevalence = (double) totalFaults / n;

            System.out.printf("\n[Project] %-32s | N=%5d | Faults=%4d (%6.2f%%)\n",
                    pId, n, totalFaults, truePrevalence * 100.0);

            Map<String, Object> projReport = new LinkedHashMap<>();
            projReport.put("project_id", pId);
            projReport.put("total_commits", n);
            projReport.put("total_defects", totalFaults);
            projReport.put("true_prevalence", truePrevalence);

            Map<String, Object> methodOutputs = new LinkedHashMap<>();

            // 1. Full-Project Baselines
            var m_A0 = AdaptationMetrics.evaluate(yAll, pAll, 0.50);
            methodOutputs.put("A0_Frozen_050", wrapFullResult("A0", 0.50, PI_SOURCE, m_A0));

            var m_A1 = AdaptationMetrics.evaluate(yAll, pAll, FROZEN_TAU);
            methodOutputs.put("A1_Frozen_TauStar", wrapFullResult("A1", FROZEN_TAU, PI_SOURCE, m_A1));
            baselineF1Full.add(m_A1.f1);
            baselineBrierFull.add(m_A1.brierScore);
            baselineEceFull.add(m_A1.ece);

            System.out.printf("  A1 Baseline (Frozen Tau*=%.3f): F1=%.4f, P=%.4f, R=%.4f, MCC=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    FROZEN_TAU, m_A1.f1, m_A1.precision, m_A1.recall, m_A1.mcc, m_A1.prAuc, m_A1.brierScore, m_A1.ece);

            // 2. Unsupervised Soft Mean
            double piSoft = PrevalenceEstimator.estimateSoftMean(pAll);
            double[] pAdaptedSoft = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piSoft);
            var m_A1Soft = AdaptationMetrics.evaluate(yAll, pAdaptedSoft, FROZEN_TAU);
            methodOutputs.put("A1_SoftMean", wrapFullResult("A1-Soft", FROZEN_TAU, piSoft, m_A1Soft));
            softMeanF1Full.add(m_A1Soft.f1);
            softMeanBrierFull.add(m_A1Soft.brierScore);
            softMeanEceFull.add(m_A1Soft.ece);
            System.out.printf("  A1-SoftMean (Pi_hat=%.4f):   F1=%.4f, P=%.4f, R=%.4f, MCC=%.4f, PR-AUC=%.4f, Brier=%.4f, ECE=%.4f\n",
                    piSoft, m_A1Soft.f1, m_A1Soft.precision, m_A1Soft.recall, m_A1Soft.mcc, m_A1Soft.prAuc, m_A1Soft.brierScore, m_A1Soft.ece);

            // 3. Unsupervised Diagnostics (BBSE & EM)
            double piBBSE = PrevalenceEstimator.estimateBBSE(pAll, FROZEN_TAU, SOURCE_TPR, SOURCE_FPR);
            double[] pAdaptedBBSE = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piBBSE);
            var m_BBSE = AdaptationMetrics.evaluate(yAll, pAdaptedBBSE, FROZEN_TAU);
            methodOutputs.put("Diag_BBSE", wrapFullResult("Diag-BBSE", FROZEN_TAU, piBBSE, m_BBSE));

            double piEM = PrevalenceEstimator.estimateSaerensEM(pAll, PI_SOURCE, 50, 1e-5);
            double[] pAdaptedEM = ProbabilityAdapter.adaptProbabilities(pAll, PI_SOURCE, piEM);
            var m_EM = AdaptationMetrics.evaluate(yAll, pAdaptedEM, FROZEN_TAU);
            methodOutputs.put("Diag_SaerensEM", wrapFullResult("Diag-EM", FROZEN_TAU, piEM, m_EM));

            // 4. Chronological Burn-in Experiments
            Map<String, Object> burnInResults = new LinkedHashMap<>();

            for (int K : BURN_IN_SIZES) {
                if (K >= n - 50) {
                    Map<String, Object> unavail = new LinkedHashMap<>();
                    unavail.put("status", "UNAVAILABLE_INSUFFICIENT_COMMITS");
                    unavail.put("reason", "Project total commits (" + n + ") too small for K=" + K);
                    burnInResults.put("K_" + K, unavail);
                    continue;
                }

                // Partition: Setting B.1 Retrospective
                var partRetro = ChronologicalBurnInQueue.partitionRetrospective(sortedCommits, K);
                // Partition: Setting B.2 Strict 90-day Delay Queue
                var partDelay = ChronologicalBurnInQueue.partitionWithDelay(sortedCommits, K, ChronologicalBurnInQueue.DEFAULT_DELAY_NANOS);

                int evalSize = partDelay.evaluationCommits.size();
                int[] yEval = new int[evalSize];
                double[] pBaseEval = new double[evalSize];
                int evalFaults = 0;
                for (int i = 0; i < evalSize; i++) {
                    yEval[i] = partDelay.evaluationCommits.get(i).isFaultInducing;
                    pBaseEval[i] = partDelay.evaluationCommits.get(i).baselineProbability;
                    if (yEval[i] == 1) evalFaults++;
                }

                // Matching baseline evaluated on EXACT SAME evaluation window [K, N)
                var m_BaseOnEval = AdaptationMetrics.evaluate(yEval, pBaseEval, FROZEN_TAU);

                int[] burnInLabels = new int[K];
                for (int i = 0; i < K; i++) {
                    burnInLabels[i] = partDelay.burnInCommits.get(i).isFaultInducing;
                }

                Map<String, Object> kOutput = new LinkedHashMap<>();
                kOutput.put("burn_in_K", K);
                kOutput.put("eval_commits", evalSize);
                kOutput.put("eval_defects", evalFaults);
                kOutput.put("eval_true_prevalence", (double) evalFaults / evalSize);
                kOutput.put("delay90d_matured_labels", partDelay.maturedTotalCount);
                kOutput.put("delay90d_matured_faults", partDelay.maturedDefectCount);
                kOutput.put("delay90d_fallback_used", partDelay.maturedTotalCount == 0);
                kOutput.put("matching_baseline_metrics", m_BaseOnEval.toMap());

                // Evaluate Bayesian smoothing strengths M in {10, 20, 50}
                Map<String, Object> bayesConfigs = new LinkedHashMap<>();
                for (double M : BAYES_M_VALUES) {
                    // Strict 90d Delay
                    double piBayesDelay = PrevalenceEstimator.estimateBayesianBetaBinomial(
                            burnInLabels, partDelay.burnInMaturityMask, PI_SOURCE, M);
                    double[] pAdaptedDelay = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piBayesDelay);
                    var m_BayesDelay = AdaptationMetrics.evaluate(yEval, pAdaptedDelay, FROZEN_TAU);

                    // Retrospective
                    double piBayesRetro = PrevalenceEstimator.estimateBayesianBetaBinomial(
                            burnInLabels, partRetro.burnInMaturityMask, PI_SOURCE, M);
                    double[] pAdaptedRetro = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piBayesRetro);
                    var m_BayesRetro = AdaptationMetrics.evaluate(yEval, pAdaptedRetro, FROZEN_TAU);

                    Map<String, Object> mEntry = new LinkedHashMap<>();
                    mEntry.put("delay90d_pi_bayes", piBayesDelay);
                    mEntry.put("delay90d_metrics", m_BayesDelay.toMap());
                    mEntry.put("retro_pi_bayes", piBayesRetro);
                    mEntry.put("retro_metrics", m_BayesRetro.toMap());
                    bayesConfigs.put("M_" + (int) M, mEntry);

                    // Record vector for K=100 Delay90d
                    if (K == 100) {
                        String key = "Bayes_K100_M" + (int) M + "_Delay90d";
                        f1Vectors.computeIfAbsent(key, k -> new ArrayList<>()).add(m_BayesDelay.f1);
                        brierVectors.computeIfAbsent(key, k -> new ArrayList<>()).add(m_BayesDelay.brierScore);
                        eceVectors.computeIfAbsent(key, k -> new ArrayList<>()).add(m_BayesDelay.ece);
                        precVectors.computeIfAbsent(key, k -> new ArrayList<>()).add(m_BayesDelay.precision);
                        recVectors.computeIfAbsent(key, k -> new ArrayList<>()).add(m_BayesDelay.recall);

                        String keyBase = "Base_K100";
                        if (M == 10.0) {
                            f1Vectors.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(m_BaseOnEval.f1);
                            brierVectors.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(m_BaseOnEval.brierScore);
                            eceVectors.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(m_BaseOnEval.ece);
                            precVectors.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(m_BaseOnEval.precision);
                            recVectors.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(m_BaseOnEval.recall);
                        }
                    }
                }
                kOutput.put("bayesian_prevalence_adaptation", bayesConfigs);

                // 5. Quantile Threshold Adaptation (A3)
                double piForThresh = PrevalenceEstimator.estimateBayesianBetaBinomial(
                        burnInLabels, partDelay.burnInMaturityMask, PI_SOURCE, 20.0);
                double adaptedTau = ProbabilityAdapter.computeQuantileThreshold(pBaseEval, piForThresh);
                var m_Thresh = AdaptationMetrics.evaluate(yEval, pBaseEval, adaptedTau);
                Map<String, Object> threshOutput = new LinkedHashMap<>();
                threshOutput.put("adapted_threshold", adaptedTau);
                threshOutput.put("estimated_prevalence_used", piForThresh);
                threshOutput.put("metrics_at_adapted_threshold", m_Thresh.toMap());
                kOutput.put("quantile_threshold_adaptation", threshOutput);

                if (K == 100) {
                    String keyThresh = "Thresh_K100_Delay90d";
                    f1Vectors.computeIfAbsent(keyThresh, k -> new ArrayList<>()).add(m_Thresh.f1);
                    brierVectors.computeIfAbsent(keyThresh, k -> new ArrayList<>()).add(m_Thresh.brierScore);
                    eceVectors.computeIfAbsent(keyThresh, k -> new ArrayList<>()).add(m_Thresh.ece);
                    precVectors.computeIfAbsent(keyThresh, k -> new ArrayList<>()).add(m_Thresh.precision);
                    recVectors.computeIfAbsent(keyThresh, k -> new ArrayList<>()).add(m_Thresh.recall);
                }

                // 6. Combined Adaptation (A4: Probability + Threshold)
                if (K == 100 || K == 250) {
                    double[] pBayesAdapted = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piForThresh);
                    double combinedTau = ProbabilityAdapter.computeQuantileThreshold(pBayesAdapted, piForThresh);
                    var m_Combined = AdaptationMetrics.evaluate(yEval, pBayesAdapted, combinedTau);
                    Map<String, Object> combOutput = new LinkedHashMap<>();
                    combOutput.put("combined_threshold", combinedTau);
                    combOutput.put("metrics", m_Combined.toMap());
                    kOutput.put("combined_adaptation", combOutput);

                    if (K == 100) {
                        String keyComb = "Combined_K100_Delay90d";
                        f1Vectors.computeIfAbsent(keyComb, k -> new ArrayList<>()).add(m_Combined.f1);
                        brierVectors.computeIfAbsent(keyComb, k -> new ArrayList<>()).add(m_Combined.brierScore);
                        eceVectors.computeIfAbsent(keyComb, k -> new ArrayList<>()).add(m_Combined.ece);
                        precVectors.computeIfAbsent(keyComb, k -> new ArrayList<>()).add(m_Combined.precision);
                        recVectors.computeIfAbsent(keyComb, k -> new ArrayList<>()).add(m_Combined.recall);
                    }
                }

                burnInResults.put("K_" + K, kOutput);

                // Print brief summary
                double piBayesM20 = PrevalenceEstimator.estimateBayesianBetaBinomial(
                        burnInLabels, partDelay.burnInMaturityMask, PI_SOURCE, 20.0);
                double[] pAdM20 = ProbabilityAdapter.adaptProbabilities(pBaseEval, PI_SOURCE, piBayesM20);
                var mAdM20 = AdaptationMetrics.evaluate(yEval, pAdM20, FROZEN_TAU);
                System.out.printf("  Burn-in K=%3d (Matured=%d/%d, Fallback=%b): Base F1=%.4f -> Bayes F1=%.4f | Thresh (Tau=%.3f, F1=%.4f, P=%.4f, R=%.4f)\n",
                        K, partDelay.maturedTotalCount, K, partDelay.maturedTotalCount == 0,
                        m_BaseOnEval.f1, mAdM20.f1, adaptedTau, m_Thresh.f1, m_Thresh.precision, m_Thresh.recall);
            }

            methodOutputs.put("burn_in_experiments", burnInResults);
            projReport.put("methods", methodOutputs);
            perProjectResults.put(pId, projReport);
        }

        fullBenchmarkOutput.put("project_evaluations", perProjectResults);

        // -------------------------------------------------------------
        // STATISTICAL ANALYSIS & MACRO-AVERAGES (N=9 Projects)
        // -------------------------------------------------------------
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPUTING STATISTICAL SIGNIFICANCE & MACRO-AVERAGES (N=9 Projects)");
        System.out.println("=".repeat(80));

        Map<String, Object> statisticalReport = new LinkedHashMap<>();
        statisticalReport.put("sample_size_projects", 9);
        statisticalReport.put("statistical_test", "Paired Wilcoxon Signed-Rank Test (Exact Two-Sided Permutation Distribution)");

        // 1. Full-Project Soft Mean vs Baseline
        double[] baseF1Arr = toDoubleArray(baselineF1Full);
        double[] softF1Arr = toDoubleArray(softMeanF1Full);
        double[] baseBrierArr = toDoubleArray(baselineBrierFull);
        double[] softBrierArr = toDoubleArray(softMeanBrierFull);
        double[] baseEceArr = toDoubleArray(baselineEceFull);
        double[] softEceArr = toDoubleArray(softMeanEceFull);

        var wilc_soft_f1 = Phase3aStatisticalAnalysis.pairedWilcoxon(softF1Arr, baseF1Arr);
        var wilc_soft_brier = Phase3aStatisticalAnalysis.pairedWilcoxon(softBrierArr, baseBrierArr);
        var wilc_soft_ece = Phase3aStatisticalAnalysis.pairedWilcoxon(softEceArr, baseEceArr);

        Map<String, Object> softMeanStats = new LinkedHashMap<>();
        softMeanStats.put("macro_baseline_f1", Arrays.stream(baseF1Arr).average().orElse(0.0));
        softMeanStats.put("macro_adapted_f1", Arrays.stream(softF1Arr).average().orElse(0.0));
        softMeanStats.put("f1_wilcoxon", wilc_soft_f1.toMap());
        softMeanStats.put("brier_wilcoxon", wilc_soft_brier.toMap());
        softMeanStats.put("ece_wilcoxon", wilc_soft_ece.toMap());
        statisticalReport.put("A1_SoftMean_vs_Baseline", softMeanStats);

        System.out.printf("Soft Mean vs Baseline (Full N=9):\n");
        System.out.printf("  Macro F1: Base=%.4f -> Soft=%.4f (MeanDiff=%+.4f, W=%.1f, p=%.4f, r_rb=%+.3f, Wins=%d, Losses=%d, Ties=%d)\n",
                softMeanStats.get("macro_baseline_f1"), softMeanStats.get("macro_adapted_f1"),
                wilc_soft_f1.meanDiff, wilc_soft_f1.wStatistic, wilc_soft_f1.pValue, wilc_soft_f1.rankBiserialR,
                wilc_soft_f1.wins, wilc_soft_f1.losses, wilc_soft_f1.ties);

        // 2. Burn-in K=100 Comparisons vs Matching Base_K100
        double[] baseK100F1 = toDoubleArray(f1Vectors.get("Base_K100"));
        double[] baseK100Prec = toDoubleArray(precVectors.get("Base_K100"));
        double[] baseK100Rec = toDoubleArray(recVectors.get("Base_K100"));

        Map<String, Object> burnIn100Stats = new LinkedHashMap<>();

        // Test each Bayes M
        for (double M : BAYES_M_VALUES) {
            String key = "Bayes_K100_M" + (int) M + "_Delay90d";
            double[] bayesF1 = toDoubleArray(f1Vectors.get(key));
            var w = Phase3aStatisticalAnalysis.pairedWilcoxon(bayesF1, baseK100F1);
            Map<String, Object> bayesStat = new LinkedHashMap<>();
            bayesStat.put("macro_baseline_f1", Arrays.stream(baseK100F1).average().orElse(0.0));
            bayesStat.put("macro_bayes_f1", Arrays.stream(bayesF1).average().orElse(0.0));
            bayesStat.put("wilcoxon", w.toMap());
            burnIn100Stats.put("Bayes_M" + (int) M + "_Delay90d", bayesStat);

            System.out.printf("Bayes M=%d Delay90d vs Base (K=100, N=9):\n", (int) M);
            System.out.printf("  Macro F1: Base=%.4f -> Bayes=%.4f (MeanDiff=%+.4f, W=%.1f, p=%.4f, r_rb=%+.3f, Wins=%d, Losses=%d, Ties=%d)\n",
                    bayesStat.get("macro_baseline_f1"), bayesStat.get("macro_bayes_f1"),
                    w.meanDiff, w.wStatistic, w.pValue, w.rankBiserialR, w.wins, w.losses, w.ties);
        }

        // Test Threshold Adaptation K=100
        double[] threshF1 = toDoubleArray(f1Vectors.get("Thresh_K100_Delay90d"));
        double[] threshPrec = toDoubleArray(precVectors.get("Thresh_K100_Delay90d"));
        double[] threshRec = toDoubleArray(recVectors.get("Thresh_K100_Delay90d"));
        var wThreshF1 = Phase3aStatisticalAnalysis.pairedWilcoxon(threshF1, baseK100F1);
        var wThreshPrec = Phase3aStatisticalAnalysis.pairedWilcoxon(threshPrec, baseK100Prec);
        var wThreshRec = Phase3aStatisticalAnalysis.pairedWilcoxon(threshRec, baseK100Rec);

        Map<String, Object> threshStats = new LinkedHashMap<>();
        threshStats.put("macro_baseline_precision", Arrays.stream(baseK100Prec).average().orElse(0.0));
        threshStats.put("macro_adapted_precision", Arrays.stream(threshPrec).average().orElse(0.0));
        threshStats.put("precision_wilcoxon", wThreshPrec.toMap());
        threshStats.put("macro_baseline_recall", Arrays.stream(baseK100Rec).average().orElse(0.0));
        threshStats.put("macro_adapted_recall", Arrays.stream(threshRec).average().orElse(0.0));
        threshStats.put("recall_wilcoxon", wThreshRec.toMap());
        threshStats.put("macro_baseline_f1", Arrays.stream(baseK100F1).average().orElse(0.0));
        threshStats.put("macro_adapted_f1", Arrays.stream(threshF1).average().orElse(0.0));
        threshStats.put("f1_wilcoxon", wThreshF1.toMap());
        burnIn100Stats.put("Quantile_Threshold_K100", threshStats);

        System.out.printf("Quantile Threshold Adaptation vs Base (K=100, N=9):\n");
        System.out.printf("  Macro Precision: Base=%.4f -> Thresh=%.4f (MeanDiff=%+.4f, W=%.1f, p=%.4f, r_rb=%+.3f, Wins=%d, Losses=%d, Ties=%d)\n",
                threshStats.get("macro_baseline_precision"), threshStats.get("macro_adapted_precision"),
                wThreshPrec.meanDiff, wThreshPrec.wStatistic, wThreshPrec.pValue, wThreshPrec.rankBiserialR,
                wThreshPrec.wins, wThreshPrec.losses, wThreshPrec.ties);
        System.out.printf("  Macro F1:        Base=%.4f -> Thresh=%.4f (MeanDiff=%+.4f, W=%.1f, p=%.4f, r_rb=%+.3f, Wins=%d, Losses=%d, Ties=%d)\n",
                threshStats.get("macro_baseline_f1"), threshStats.get("macro_adapted_f1"),
                wThreshF1.meanDiff, wThreshF1.wStatistic, wThreshF1.pValue, wThreshF1.rankBiserialR,
                wThreshF1.wins, wThreshF1.losses, wThreshF1.ties);

        statisticalReport.put("Burn_In_K100_Comparisons", burnIn100Stats);

        long tElapsedMs = System.currentTimeMillis() - tStart;
        fullBenchmarkOutput.put("execution_time_ms", tElapsedMs);
        statisticalReport.put("execution_time_ms", tElapsedMs);

        // 7. Atomic Serialization of Results
        String resultsPath = "research/reports/phase3a_gate4_full_results.json";
        String statsPath = "research/reports/phase3a_gate4_statistical_analysis.json";
        Phase3aPilotRunner.class.getDeclaredMethod("saveJsonAtomically", String.class, Map.class); // verify availability
        saveJsonAtomically(resultsPath, fullBenchmarkOutput);
        saveJsonAtomically(statsPath, statisticalReport);

        System.out.println("=".repeat(80));
        System.out.printf("[SUCCESS] Phase 3A Gate 4 Benchmark Completed in %.2f seconds.\n", tElapsedMs / 1000.0);
        System.out.printf("  Results JSON:     %s\n", resultsPath);
        System.out.printf("  Statistical JSON: %s\n", statsPath);
        System.out.println("=".repeat(80));
    }

    private static Map<String, Object> wrapFullResult(String methodId, double threshold, double estPi, AdaptationMetrics.MetricsResult m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method_id", methodId);
        out.put("operating_threshold", threshold);
        out.put("estimated_prevalence", estPi);
        out.put("metrics", m.toMap());
        return out;
    }

    private static double[] toDoubleArray(List<Double> list) {
        if (list == null) return new double[0];
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static RuntimeException vintageException(String msg) {
        return new RuntimeException(msg);
    }

    private static Map<String, List<ChronologicalBurnInQueue.CommitRecord>> loadCsv(File file) throws IOException {
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
        File tempFile = File.createTempFile("gate4_", ".tmp", targetFile.getParentFile());

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
