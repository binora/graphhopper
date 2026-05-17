// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHInputEdge implements Comparable<CCHInputEdge> {
    private final int nodeA;
    private final int nodeB;

    public CCHInputEdge(int nodeA, int nodeB) {
        if (nodeA < 0)
            throw new IllegalArgumentException("nodeA must be >= 0");
        if (nodeB < 0)
            throw new IllegalArgumentException("nodeB must be >= 0");
        if (nodeA == nodeB)
            throw new IllegalArgumentException("CCH support edges cannot be loops");
        this.nodeA = Math.min(nodeA, nodeB);
        this.nodeB = Math.max(nodeA, nodeB);
    }

    public int getNodeA() {
        return nodeA;
    }

    public int getNodeB() {
        return nodeB;
    }

    @Override
    public int compareTo(CCHInputEdge other) {
        int byNodeA = Integer.compare(nodeA, other.nodeA);
        return byNodeA != 0 ? byNodeA : Integer.compare(nodeB, other.nodeB);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHInputEdge))
            return false;
        CCHInputEdge that = (CCHInputEdge) o;
        return nodeA == that.nodeA && nodeB == that.nodeB;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeA, nodeB);
    }
}
