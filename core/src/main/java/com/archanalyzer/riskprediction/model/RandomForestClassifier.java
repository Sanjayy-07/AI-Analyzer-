package com.archanalyzer.riskprediction.model;

import java.util.*;

/**
 * Trainable, pure-Java Random Forest ensemble classifier.
 * Employs bootstrap aggregation (bagging) and random subspace feature selection.
 */
public class RandomForestClassifier implements TrainableClassifier {

    private final int numTrees;
    private final int maxDepth;
    private final int minSamplesSplit;
    private final long randomSeed;

    private String[] featureNames = new String[0];
    private final List<DecisionTree> trees = new ArrayList<>();
    private double[] accumulatedFeatureImportances = new double[0];

    public RandomForestClassifier() {
        this(15, 5, 2, 42L);
    }

    public RandomForestClassifier(int numTrees, int maxDepth, int minSamplesSplit, long randomSeed) {
        this.numTrees = Math.max(1, numTrees);
        this.maxDepth = Math.max(1, maxDepth);
        this.minSamplesSplit = Math.max(2, minSamplesSplit);
        this.randomSeed = randomSeed;
    }

    @Override
    public void train(double[][] X, int[] y, String[] featureNames) {
        Objects.requireNonNull(X, "Training matrix X cannot be null");
        Objects.requireNonNull(y, "Target labels y cannot be null");
        int n = X.length;
        if (n == 0) return;
        int p = X[0].length;

        this.featureNames = featureNames != null ? featureNames.clone() : new String[p];
        this.trees.clear();
        this.accumulatedFeatureImportances = new double[p];

        Random rng = new Random(randomSeed);

        for (int b = 0; b < numTrees; b++) {
            // Bootstrap sampling with replacement
            int[] bootstrapIndices = new int[n];
            for (int i = 0; i < n; i++) {
                bootstrapIndices[i] = rng.nextInt(n);
            }

            DecisionTree tree = new DecisionTree(maxDepth, minSamplesSplit, p);
            tree.train(X, y, bootstrapIndices, rng);
            trees.add(tree);

            double[] treeGains = tree.getFeatureGiniGains();
            for (int j = 0; j < p; j++) {
                accumulatedFeatureImportances[j] += treeGains[j];
            }
        }
    }

    @Override
    public double predictProbability(double[] x) {
        if (trees.isEmpty()) return 0.5;
        double sum = 0.0;
        for (DecisionTree tree : trees) {
            sum += tree.predict(x);
        }
        return sum / trees.size();
    }

    @Override
    public String getModelName() {
        return "RandomForestClassifier (trees=" + trees.size() + ")";
    }

    @Override
    public String[] getFeatureNames() {
        return featureNames;
    }

    @Override
    public Map<String, Double> getFeatureImportances() {
        Map<String, Double> map = new LinkedHashMap<>();
        double total = 0.0;
        for (double imp : accumulatedFeatureImportances) total += imp;

        for (int j = 0; j < featureNames.length; j++) {
            double normalized = total > 1e-7 ? accumulatedFeatureImportances[j] / total : (1.0 / featureNames.length);
            map.put(featureNames[j], Math.round(normalized * 1000.0) / 1000.0);
        }
        return map;
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL=RandomForest\n");
        sb.append("NUM_TREES=").append(trees.size()).append("\n");
        sb.append("MAX_DEPTH=").append(maxDepth).append("\n");
        sb.append("MIN_SAMPLES_SPLIT=").append(minSamplesSplit).append("\n");
        sb.append("FEATURES=").append(String.join(",", featureNames)).append("\n");
        sb.append("IMPORTANCES=").append(joinDoubles(accumulatedFeatureImportances)).append("\n");
        for (int i = 0; i < trees.size(); i++) {
            sb.append("TREE_").append(i).append("=").append(trees.get(i).serialize()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void deserialize(String data) {
        this.trees.clear();
        int loadedMaxDepth = this.maxDepth;
        int loadedMinSplit = this.minSamplesSplit;
        List<String> treeLines = new ArrayList<>();

        for (String line : data.split("\n")) {
            line = line.trim();
            if (line.startsWith("FEATURES=")) {
                this.featureNames = line.substring("FEATURES=".length()).split(",");
            } else if (line.startsWith("MAX_DEPTH=")) {
                loadedMaxDepth = Integer.parseInt(line.substring("MAX_DEPTH=".length()));
            } else if (line.startsWith("MIN_SAMPLES_SPLIT=")) {
                loadedMinSplit = Integer.parseInt(line.substring("MIN_SAMPLES_SPLIT=".length()));
            } else if (line.startsWith("IMPORTANCES=")) {
                this.accumulatedFeatureImportances = parseDoubles(line.substring("IMPORTANCES=".length()));
            } else if (line.matches("^TREE_\\d+=.*")) {
                int eq = line.indexOf('=');
                treeLines.add(line.substring(eq + 1));
            }
        }

        int p = this.featureNames.length;
        for (String treeData : treeLines) {
            DecisionTree tree = DecisionTree.deserialize(treeData, loadedMaxDepth, loadedMinSplit, p);
            this.trees.add(tree);
        }
    }

    private static String joinDoubles(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }
}
