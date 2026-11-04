package com.archanalyzer.riskprediction.model;

import java.util.*;

/**
 * Pure-Java binary decision tree for classification using Gini impurity.
 */
public class DecisionTree {

    public static class Node {
        public boolean isLeaf;
        public int featureIndex = -1;
        public double threshold = 0.0;
        public double leafProbability = 0.0;
        public Node left;
        public Node right;

        public static Node leaf(double probability) {
            Node n = new Node();
            n.isLeaf = true;
            n.leafProbability = probability;
            return n;
        }

        public static Node split(int feature, double threshold, Node left, Node right) {
            Node n = new Node();
            n.isLeaf = false;
            n.featureIndex = feature;
            n.threshold = threshold;
            n.left = left;
            n.right = right;
            return n;
        }
    }

    private Node root;
    private final int maxDepth;
    private final int minSamplesSplit;
    private final double[] featureGiniGains;

    public DecisionTree(int maxDepth, int minSamplesSplit, int numFeatures) {
        this.maxDepth = maxDepth;
        this.minSamplesSplit = minSamplesSplit;
        this.featureGiniGains = new double[numFeatures];
    }

    public void train(double[][] X, int[] y, int[] sampleIndices, Random rng) {
        this.root = buildTree(X, y, sampleIndices, 0, rng);
    }

    public double predict(double[] x) {
        if (root == null) return 0.5;
        Node cur = root;
        while (!cur.isLeaf) {
            double val = x[cur.featureIndex];
            if (Double.isNaN(val)) {
                cur = cur.left != null ? cur.left : cur.right;
            } else {
                cur = (val <= cur.threshold) ? cur.left : cur.right;
            }
        }
        return cur.leafProbability;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        serializeNode(root, sb);
        return sb.toString();
    }

    private void serializeNode(Node node, StringBuilder sb) {
        if (node == null) {
            sb.append("#;");
            return;
        }
        if (node.isLeaf) {
            sb.append("L:").append(node.leafProbability).append(";");
        } else {
            sb.append("S:").append(node.featureIndex).append(":").append(node.threshold).append(";");
            serializeNode(node.left, sb);
            serializeNode(node.right, sb);
        }
    }

    public static DecisionTree deserialize(String data, int maxDepth, int minSamplesSplit, int numFeatures) {
        DecisionTree tree = new DecisionTree(maxDepth, minSamplesSplit, numFeatures);
        String[] tokens = data.split(";");
        int[] index = new int[]{0};
        tree.root = deserializeNode(tokens, index);
        return tree;
    }

    private static Node deserializeNode(String[] tokens, int[] index) {
        if (index[0] >= tokens.length) return null;
        String token = tokens[index[0]++].trim();
        if (token.isEmpty() || token.equals("#")) return null;
        if (token.startsWith("L:")) {
            double prob = Double.parseDouble(token.substring(2));
            return Node.leaf(prob);
        } else if (token.startsWith("S:")) {
            String[] parts = token.substring(2).split(":");
            int feat = Integer.parseInt(parts[0]);
            double thresh = Double.parseDouble(parts[1]);
            Node left = deserializeNode(tokens, index);
            Node right = deserializeNode(tokens, index);
            return Node.split(feat, thresh, left, right);
        }
        return null;
    }

    public double[] getFeatureGiniGains() {
        return featureGiniGains;
    }

    private Node buildTree(double[][] X, int[] y, int[] indices, int depth, Random rng) {
        int n = indices.length;
        if (n == 0) return Node.leaf(0.5);

        int posCount = 0;
        for (int idx : indices) {
            if (y[idx] == 1) posCount++;
        }
        double prob = (double) posCount / n;

        // Base cases: pure node, depth limit, or insufficient samples
        if (prob == 0.0 || prob == 1.0 || depth >= maxDepth || n < minSamplesSplit) {
            return Node.leaf(prob);
        }

        double parentGini = 1.0 - (prob * prob) - ((1.0 - prob) * (1.0 - prob));
        int p = X[0].length;
        int mTry = Math.max(1, (int) Math.ceil(Math.sqrt(p)));

        // Select random subset of candidate features
        List<Integer> allFeatures = new ArrayList<>(p);
        for (int j = 0; j < p; j++) allFeatures.add(j);
        Collections.shuffle(allFeatures, rng);
        List<Integer> candidateFeatures = allFeatures.subList(0, Math.min(mTry, p));

        double bestGain = 0.0;
        int bestFeature = -1;
        double bestThreshold = 0.0;
        int[] bestLeftIndices = null;
        int[] bestRightIndices = null;

        for (int feat : candidateFeatures) {
            // Find unique values
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = X[indices[i]][feat];
            Arrays.sort(vals);

            for (int i = 0; i < n - 1; i++) {
                if (vals[i] == vals[i + 1]) continue;
                double thresh = (vals[i] + vals[i + 1]) / 2.0;

                int leftCount = 0, leftPos = 0;
                int rightCount = 0, rightPos = 0;

                for (int idx : indices) {
                    if (X[idx][feat] <= thresh) {
                        leftCount++;
                        if (y[idx] == 1) leftPos++;
                    } else {
                        rightCount++;
                        if (y[idx] == 1) rightPos++;
                    }
                }

                if (leftCount == 0 || rightCount == 0) continue;

                double pL = (double) leftPos / leftCount;
                double giniL = 1.0 - (pL * pL) - ((1.0 - pL) * (1.0 - pL));

                double pR = (double) rightPos / rightCount;
                double giniR = 1.0 - (pR * pR) - ((1.0 - pR) * (1.0 - pR));

                double childGini = ((double) leftCount / n * giniL) + ((double) rightCount / n * giniR);
                double gain = parentGini - childGini;

                if (gain > bestGain) {
                    bestGain = gain;
                    bestFeature = feat;
                    bestThreshold = thresh;

                    bestLeftIndices = new int[leftCount];
                    bestRightIndices = new int[rightCount];
                    int li = 0, ri = 0;
                    for (int idx : indices) {
                        if (X[idx][feat] <= thresh) {
                            bestLeftIndices[li++] = idx;
                        } else {
                            bestRightIndices[ri++] = idx;
                        }
                    }
                }
            }
        }

        if (bestGain <= 1e-7 || bestLeftIndices == null || bestRightIndices == null) {
            return Node.leaf(prob);
        }

        featureGiniGains[bestFeature] += bestGain * n;
        Node leftNode = buildTree(X, y, bestLeftIndices, depth + 1, rng);
        Node rightNode = buildTree(X, y, bestRightIndices, depth + 1, rng);
        return Node.split(bestFeature, bestThreshold, leftNode, rightNode);
    }
}
