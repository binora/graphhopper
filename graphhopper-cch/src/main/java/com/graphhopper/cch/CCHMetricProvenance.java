// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHMetricProvenance {
    public enum Type {
        NONE,
        DIRECT,
        SHORTCUT,
        EDGE_TRANSITION
    }

    private static final CCHMetricProvenance NONE = new CCHMetricProvenance(Type.NONE, CCHStorage.NO_ARC, false,
            CCHStorage.NO_ARC, CCHStorage.NO_ARC, CCHStorage.NO_ARC, CCHStorage.NO_ARC, CCHStorage.NO_ARC,
            CCHStorage.NO_ARC, false, 0, 0, 0, 0, 0);

    private final Type type;
    private final int baseEdge;
    private final boolean reverse;
    private final int firstSkippedArc;
    private final int secondSkippedArc;
    private final int incomingEdgeKey;
    private final int viaNode;
    private final int outgoingEdgeKey;
    private final int outgoingBaseEdge;
    private final boolean outgoingReverse;
    private final double turnWeight;
    private final long turnMillis;
    private final double edgeWeight;
    private final long edgeMillis;
    private final double edgeDistance;

    private CCHMetricProvenance(Type type, int baseEdge, boolean reverse, int firstSkippedArc, int secondSkippedArc) {
        this(type, baseEdge, reverse, firstSkippedArc, secondSkippedArc, CCHStorage.NO_ARC, CCHStorage.NO_ARC,
                CCHStorage.NO_ARC, CCHStorage.NO_ARC, false, 0, 0, 0, 0, 0);
    }

    private CCHMetricProvenance(Type type, int baseEdge, boolean reverse, int firstSkippedArc, int secondSkippedArc,
                                int incomingEdgeKey, int viaNode, int outgoingEdgeKey, int outgoingBaseEdge,
                                boolean outgoingReverse, double turnWeight, long turnMillis, double edgeWeight,
                                long edgeMillis, double edgeDistance) {
        this.type = Objects.requireNonNull(type, "type");
        this.baseEdge = baseEdge;
        this.reverse = reverse;
        this.firstSkippedArc = firstSkippedArc;
        this.secondSkippedArc = secondSkippedArc;
        this.incomingEdgeKey = incomingEdgeKey;
        this.viaNode = viaNode;
        this.outgoingEdgeKey = outgoingEdgeKey;
        this.outgoingBaseEdge = outgoingBaseEdge;
        this.outgoingReverse = outgoingReverse;
        this.turnWeight = turnWeight;
        this.turnMillis = turnMillis;
        this.edgeWeight = edgeWeight;
        this.edgeMillis = edgeMillis;
        this.edgeDistance = edgeDistance;
    }

    public static CCHMetricProvenance none() {
        return NONE;
    }

    public static CCHMetricProvenance direct(int baseEdge, boolean reverse) {
        if (baseEdge < 0)
            throw new IllegalArgumentException("baseEdge must be >= 0");
        return new CCHMetricProvenance(Type.DIRECT, baseEdge, reverse, CCHStorage.NO_ARC, CCHStorage.NO_ARC);
    }

    public static CCHMetricProvenance shortcut(int firstSkippedArc, int secondSkippedArc) {
        if (firstSkippedArc < 0)
            throw new IllegalArgumentException("firstSkippedArc must be >= 0");
        if (secondSkippedArc < 0)
            throw new IllegalArgumentException("secondSkippedArc must be >= 0");
        return new CCHMetricProvenance(Type.SHORTCUT, CCHStorage.NO_ARC, false, firstSkippedArc, secondSkippedArc);
    }

    public static CCHMetricProvenance edgeTransition(int incomingEdgeKey, int viaNode, int outgoingEdgeKey,
                                                     int outgoingBaseEdge, boolean outgoingReverse, double turnWeight,
                                                     long turnMillis, double edgeWeight, long edgeMillis,
                                                     double edgeDistance) {
        if (incomingEdgeKey < 0)
            throw new IllegalArgumentException("incomingEdgeKey must be >= 0");
        if (viaNode < 0)
            throw new IllegalArgumentException("viaNode must be >= 0");
        if (outgoingEdgeKey < 0)
            throw new IllegalArgumentException("outgoingEdgeKey must be >= 0");
        if (outgoingBaseEdge < 0)
            throw new IllegalArgumentException("outgoingBaseEdge must be >= 0");
        if (!Double.isFinite(turnWeight) || turnWeight < 0)
            throw new IllegalArgumentException("turnWeight must be finite and >= 0");
        if (turnMillis < 0)
            throw new IllegalArgumentException("turnMillis must be >= 0");
        if (!Double.isFinite(edgeWeight) || edgeWeight < 0)
            throw new IllegalArgumentException("edgeWeight must be finite and >= 0");
        if (edgeMillis < 0)
            throw new IllegalArgumentException("edgeMillis must be >= 0");
        if (!Double.isFinite(edgeDistance) || edgeDistance < 0)
            throw new IllegalArgumentException("edgeDistance must be finite and >= 0");
        return new CCHMetricProvenance(Type.EDGE_TRANSITION, CCHStorage.NO_ARC, false, CCHStorage.NO_ARC,
                CCHStorage.NO_ARC, incomingEdgeKey, viaNode, outgoingEdgeKey, outgoingBaseEdge, outgoingReverse,
                turnWeight, turnMillis, edgeWeight, edgeMillis, edgeDistance);
    }

    public Type getType() {
        return type;
    }

    public boolean isNone() {
        return type == Type.NONE;
    }

    public boolean isDirect() {
        return type == Type.DIRECT;
    }

    public boolean isShortcut() {
        return type == Type.SHORTCUT;
    }

    public boolean isEdgeTransition() {
        return type == Type.EDGE_TRANSITION;
    }

    public int getBaseEdge() {
        return baseEdge;
    }

    public boolean isReverse() {
        return reverse;
    }

    public int getFirstSkippedArc() {
        return firstSkippedArc;
    }

    public int getSecondSkippedArc() {
        return secondSkippedArc;
    }

    public int getIncomingEdgeKey() {
        return incomingEdgeKey;
    }

    public int getViaNode() {
        return viaNode;
    }

    public int getOutgoingEdgeKey() {
        return outgoingEdgeKey;
    }

    public int getOutgoingBaseEdge() {
        return outgoingBaseEdge;
    }

    public boolean isOutgoingReverse() {
        return outgoingReverse;
    }

    public double getTurnWeight() {
        return turnWeight;
    }

    public long getTurnMillis() {
        return turnMillis;
    }

    public double getEdgeWeight() {
        return edgeWeight;
    }

    public long getEdgeMillis() {
        return edgeMillis;
    }

    public double getEdgeDistance() {
        return edgeDistance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHMetricProvenance))
            return false;
        CCHMetricProvenance that = (CCHMetricProvenance) o;
        return baseEdge == that.baseEdge
                && reverse == that.reverse
                && firstSkippedArc == that.firstSkippedArc
                && secondSkippedArc == that.secondSkippedArc
                && incomingEdgeKey == that.incomingEdgeKey
                && viaNode == that.viaNode
                && outgoingEdgeKey == that.outgoingEdgeKey
                && outgoingBaseEdge == that.outgoingBaseEdge
                && outgoingReverse == that.outgoingReverse
                && Double.compare(turnWeight, that.turnWeight) == 0
                && turnMillis == that.turnMillis
                && Double.compare(edgeWeight, that.edgeWeight) == 0
                && edgeMillis == that.edgeMillis
                && Double.compare(edgeDistance, that.edgeDistance) == 0
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, baseEdge, reverse, firstSkippedArc, secondSkippedArc, incomingEdgeKey, viaNode,
                outgoingEdgeKey, outgoingBaseEdge, outgoingReverse, turnWeight, turnMillis, edgeWeight, edgeMillis,
                edgeDistance);
    }
}
