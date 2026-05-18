// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class EdgeBasedCCHQueryResult {
    public static final int NO_STATE = EdgeStateCCHInputGraph.NO_STATE;

    private final boolean found;
    private final int sourceNode;
    private final int targetNode;
    private final int sourceState;
    private final int targetState;
    private final int firstEdgeKey;
    private final double firstEdgeWeight;
    private final long firstEdgeMillis;
    private final double firstEdgeDistance;
    private final double weight;
    private final long millis;
    private final double distance;
    private final int visitedNodes;
    private final CCHQueryResult coreResult;

    EdgeBasedCCHQueryResult(boolean found, int sourceNode, int targetNode, int sourceState, int targetState,
                            int firstEdgeKey, double firstEdgeWeight, long firstEdgeMillis,
                            double firstEdgeDistance, double weight, long millis, double distance,
                            int visitedNodes, CCHQueryResult coreResult) {
        this.found = found;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.sourceState = sourceState;
        this.targetState = targetState;
        this.firstEdgeKey = firstEdgeKey;
        this.firstEdgeWeight = firstEdgeWeight;
        this.firstEdgeMillis = firstEdgeMillis;
        this.firstEdgeDistance = firstEdgeDistance;
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
        this.visitedNodes = visitedNodes;
        this.coreResult = coreResult;
    }

    static EdgeBasedCCHQueryResult notFound(int sourceNode, int targetNode, int visitedNodes) {
        return new EdgeBasedCCHQueryResult(false, sourceNode, targetNode, NO_STATE, NO_STATE, CCHStorage.NO_ARC,
                Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, visitedNodes, null);
    }

    static EdgeBasedCCHQueryResult sameNode(int node) {
        return new EdgeBasedCCHQueryResult(true, node, node, NO_STATE, NO_STATE, CCHStorage.NO_ARC,
                0, 0, 0, 0, 0, 0, 0, null);
    }

    public boolean isFound() {
        return found;
    }

    public int getSourceNode() {
        return sourceNode;
    }

    public int getTargetNode() {
        return targetNode;
    }

    public int getSourceState() {
        return sourceState;
    }

    public int getTargetState() {
        return targetState;
    }

    public int getFirstEdgeKey() {
        return firstEdgeKey;
    }

    public double getFirstEdgeWeight() {
        return firstEdgeWeight;
    }

    public long getFirstEdgeMillis() {
        return firstEdgeMillis;
    }

    public double getFirstEdgeDistance() {
        return firstEdgeDistance;
    }

    public double getWeight() {
        return weight;
    }

    public long getMillis() {
        return millis;
    }

    public double getDistance() {
        return distance;
    }

    public int getVisitedNodes() {
        return visitedNodes;
    }

    public CCHQueryResult getCoreResult() {
        return coreResult;
    }
}
