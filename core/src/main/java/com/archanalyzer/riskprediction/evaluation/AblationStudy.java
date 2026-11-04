package com.archanalyzer.riskprediction.evaluation;

import com.archanalyzer.riskprediction.dataset.TemporalDataset;
import com.archanalyzer.riskprediction.model.BaselinePriorClassifier;
import com.archanalyzer.riskprediction.model.RandomForestClassifier;
import com.archanalyzer.riskprediction.model.TrainableClassifier;

import java.util.*;

/**
 * Conducts formal ablation evaluation comparing:
 * 1. Baseline Marginal Prior
 * 2. Static Architecture Only
 * 3. Evolutionary Git Churn/Co-Change Only
 * 4. Combined Multi-Modal Model
 *
 * Evaluates the core research hypothesis:
 * Does combining static dependency architecture with historical evolutionary behavior
 * improve risk prediction over static structural metrics alone?
 */
public class AblationStudy {

    public record AblationResult(
            EvaluationMetrics baselineMetrics,
            EvaluationMetrics staticMetrics,
            EvaluationMetrics evolutionaryMetrics,
            EvaluationMetrics combinedMetrics,
            double deltaRocAucOverStatic,
            double deltaPrAucOverStatic,
            double deltaF1OverStatic,
            double deltaBrierOverStatic,
            boolean hypothesisSupported,
            String summary
    ) {}

    public AblationResult runAblation(TemporalDataset trainDataset, TemporalDataset testDataset) {
        Objects.requireNonNull(trainDataset, "Train dataset cannot be null");
        Objects.requireNonNull(testDataset, "Test dataset cannot be null");

        ModelEvaluator evaluator = new ModelEvaluator();

        var trainStatic = trainDataset.getStaticMatrix();
        var testStatic = testDataset.getStaticMatrix();

        var trainEvol = trainDataset.getEvolutionaryMatrix();
        var testEvol = testDataset.getEvolutionaryMatrix();

        var trainCombined = trainDataset.getCombinedMatrix();
        var testCombined = testDataset.getCombinedMatrix();

        // 1. Baseline Model
        TrainableClassifier baselineModel = new BaselinePriorClassifier();
        baselineModel.train(trainCombined.X(), trainCombined.y(), trainCombined.featureNames());
        EvaluationMetrics baseM = evaluator.evaluate(baselineModel, testCombined.X(), testCombined.y());

        // 2. Static-Only Model
        TrainableClassifier staticModel = new RandomForestClassifier(15, 5, 2, 42L);
        staticModel.train(trainStatic.X(), trainStatic.y(), trainStatic.featureNames());
        EvaluationMetrics staticM = evaluator.evaluate(staticModel, testStatic.X(), testStatic.y());

        // 3. Evolutionary-Only Model
        TrainableClassifier evolModel = new RandomForestClassifier(15, 5, 2, 42L);
        evolModel.train(trainEvol.X(), trainEvol.y(), trainEvol.featureNames());
        EvaluationMetrics evolM = evaluator.evaluate(evolModel, testEvol.X(), testEvol.y());

        // 4. Combined Static + Evolutionary Model
        TrainableClassifier combinedModel = new RandomForestClassifier(15, 5, 2, 42L);
        combinedModel.train(trainCombined.X(), trainCombined.y(), trainCombined.featureNames());
        EvaluationMetrics combinedM = evaluator.evaluate(combinedModel, testCombined.X(), testCombined.y());

        double deltaAuc = Math.round((combinedM.getRocAuc() - staticM.getRocAuc()) * 1000.0) / 1000.0;
        double deltaPr = Math.round((combinedM.getPrAuc() - staticM.getPrAuc()) * 1000.0) / 1000.0;
        double deltaF1 = Math.round((combinedM.getF1Score() - staticM.getF1Score()) * 1000.0) / 1000.0;
        double deltaBrier = Math.round((staticM.getBrierScore() - combinedM.getBrierScore()) * 1000.0) / 1000.0;

        boolean supported = combinedM.getRocAuc() >= staticM.getRocAuc()
                && (combinedM.getPrAuc() >= staticM.getPrAuc() || combinedM.getF1Score() >= staticM.getF1Score());

        String summary = String.format("Ablation Study: Combined ROC-AUC=%.3f vs Static-Only=%.3f (Δ=%.3f), PR-AUC=%.3f vs Static=%.3f (Δ=%.3f). Hypothesis %s.",
                combinedM.getRocAuc(), staticM.getRocAuc(), deltaAuc,
                combinedM.getPrAuc(), staticM.getPrAuc(), deltaPr,
                supported ? "SUPPORTED (evolutionary history adds predictive value)" : "NOT SUPPORTED in this regime");

        return new AblationResult(baseM, staticM, evolM, combinedM, deltaAuc, deltaPr, deltaF1, deltaBrier, supported, summary);
    }
}
