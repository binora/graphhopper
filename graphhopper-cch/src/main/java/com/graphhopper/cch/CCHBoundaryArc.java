// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class CCHBoundaryArc {
    private final int tailNode;
    private final int headNode;
    private final int edgeId;
    private final int edgeKey;
    private final int originalEdgeKey;
    private final double weight;
    private final long millis;
    private final double distance;
    private final boolean virtualEdge;

    public CCHBoundaryArc(int tailNode, int headNode, int edgeId, int edgeKey, int originalEdgeKey,
                          double weight, long millis, double distance, boolean virtualEdge) {
        if (tailNode < 0)
            throw new IllegalArgumentException("tailNode must be >= 0");
        if (headNode < 0)
            throw new IllegalArgumentException("headNode must be >= 0");
        if (edgeId < 0)
            throw new IllegalArgumentException("edgeId must be >= 0");
        if (edgeKey < 0)
            throw new IllegalArgumentException("edgeKey must be >= 0");
        if (originalEdgeKey < 0)
            throw new IllegalArgumentException("originalEdgeKey must be >= 0");
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0");
        this.tailNode = tailNode;
        this.headNode = headNode;
        this.edgeId = edgeId;
        this.edgeKey = edgeKey;
        this.originalEdgeKey = originalEdgeKey;
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
        this.virtualEdge = virtualEdge;
    }

    public int getTailNode() {
        return tailNode;
    }

    public int getHeadNode() {
        return headNode;
    }

    public int getEdgeId() {
        return edgeId;
    }

    public int getEdgeKey() {
        return edgeKey;
    }

    public int getOriginalEdgeKey() {
        return originalEdgeKey;
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

    public boolean isVirtualEdge() {
        return virtualEdge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHBoundaryArc))
            return false;
        CCHBoundaryArc that = (CCHBoundaryArc) o;
        return tailNode == that.tailNode
                && headNode == that.headNode
                && edgeId == that.edgeId
                && edgeKey == that.edgeKey
                && originalEdgeKey == that.originalEdgeKey
                && Double.compare(that.weight, weight) == 0
                && millis == that.millis
                && Double.compare(that.distance, distance) == 0
                && virtualEdge == that.virtualEdge;
    }

    @Override
    public int hashCode() {
        int result = tailNode;
        long temp;
        result = 31 * result + headNode;
        result = 31 * result + edgeId;
        result = 31 * result + edgeKey;
        result = 31 * result + originalEdgeKey;
        temp = Double.doubleToLongBits(weight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (millis ^ (millis >>> 32));
        temp = Double.doubleToLongBits(distance);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + Boolean.hashCode(virtualEdge);
        return result;
    }

    @Override
    public String toString() {
        return tailNode + "->" + headNode
                + " edge=" + edgeId
                + " key=" + edgeKey
                + " originalKey=" + originalEdgeKey
                + " weight=" + weight
                + " millis=" + millis
                + " distance=" + distance
                + " virtual=" + virtualEdge;
    }
}
