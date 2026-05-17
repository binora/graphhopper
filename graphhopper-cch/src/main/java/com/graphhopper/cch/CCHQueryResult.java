// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHQueryResult {
    public static final int NO_NODE = CCHEliminationTree.NO_NODE;

    private final int source;
    private final int target;
    private final int meetingNode;
    private final double weight;
    private final int forwardVisitedNodes;
    private final int backwardVisitedNodes;
    private final double[] forwardDistance;
    private final double[] backwardDistance;
    private final int[] forwardPredecessorArc;
    private final int[] backwardPredecessorArc;
    private final boolean[] forwardSettled;
    private final boolean[] backwardSettled;

    CCHQueryResult(int source, int target, int meetingNode, double weight,
                   int forwardVisitedNodes, int backwardVisitedNodes,
                   double[] forwardDistance, double[] backwardDistance,
                   int[] forwardPredecessorArc, int[] backwardPredecessorArc,
                   boolean[] forwardSettled, boolean[] backwardSettled) {
        this.source = source;
        this.target = target;
        this.meetingNode = meetingNode;
        this.weight = weight;
        this.forwardVisitedNodes = forwardVisitedNodes;
        this.backwardVisitedNodes = backwardVisitedNodes;
        this.forwardDistance = copy(forwardDistance);
        this.backwardDistance = copy(backwardDistance);
        this.forwardPredecessorArc = copy(forwardPredecessorArc);
        this.backwardPredecessorArc = copy(backwardPredecessorArc);
        this.forwardSettled = Objects.requireNonNull(forwardSettled, "forwardSettled").clone();
        this.backwardSettled = Objects.requireNonNull(backwardSettled, "backwardSettled").clone();
    }

    public boolean isFound() {
        return meetingNode != NO_NODE;
    }

    public double getWeight() {
        return weight;
    }

    public int getSource() {
        return source;
    }

    public int getTarget() {
        return target;
    }

    public int getMeetingNode() {
        return meetingNode;
    }

    public int getForwardVisitedNodes() {
        return forwardVisitedNodes;
    }

    public int getBackwardVisitedNodes() {
        return backwardVisitedNodes;
    }

    public int getVisitedNodes() {
        return forwardVisitedNodes + backwardVisitedNodes;
    }

    public double[] getForwardDistanceArray() {
        return forwardDistance.clone();
    }

    public double[] getBackwardDistanceArray() {
        return backwardDistance.clone();
    }

    public int[] getForwardPredecessorArcArray() {
        return forwardPredecessorArc.clone();
    }

    public int[] getBackwardPredecessorArcArray() {
        return backwardPredecessorArc.clone();
    }

    public boolean[] getForwardSettledArray() {
        return forwardSettled.clone();
    }

    public boolean[] getBackwardSettledArray() {
        return backwardSettled.clone();
    }

    private static double[] copy(double[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }

    private static int[] copy(int[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }
}
