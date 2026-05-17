// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stores the metric-independent CCH overlay. This is deliberately not a GraphHopper {@code Graph}: the CCH overlay is a
 * read-only accelerator over a {@link com.graphhopper.storage.BaseGraph}, not the source of road edges, geometry, or
 * mutable graph traversal.
 */
public final class CCHStorage {
    public static final int NO_ARC = -1;

    private final int nodes;
    private final int[] order;
    private final int[] rank;
    private final int[] upFirstOut;
    private final int[] upHead;
    private final int[] downFirstOut;
    private final int[] downHead;
    private final int[] baseEdge;
    private final boolean[] reverse;

    private CCHStorage(Builder builder) {
        this.nodes = builder.nodes;
        this.order = copyAndCheckLength("order", builder.order, nodes);
        this.rank = copyAndCheckLength("rank", builder.rank, nodes);
        this.upFirstOut = copyAndCheckLength("upFirstOut", builder.upFirstOut, nodes + 1);
        this.upHead = copy(builder.upHead);
        this.downFirstOut = copyAndCheckLength("downFirstOut", builder.downFirstOut, nodes + 1);
        this.downHead = copy(builder.downHead);
        this.baseEdge = copyAndCheckLength("baseEdge", builder.baseEdge, getArcs());
        this.reverse = builder.reverse.clone();
        if (reverse.length != getArcs())
            throw new IllegalArgumentException("reverse length must be " + getArcs() + ", but was " + reverse.length);
        checkFirstOut("upFirstOut", upFirstOut, upHead.length);
        checkFirstOut("downFirstOut", downFirstOut, downHead.length);
        checkPermutation("order", order);
        checkPermutation("rank", rank);
        checkInverseOrderAndRank();
        checkHeads("upHead", upHead);
        checkHeads("downHead", downHead);
    }

    public int getNodes() {
        return nodes;
    }

    public int getShortcuts() {
        int shortcuts = 0;
        for (int edge : baseEdge) {
            if (edge == NO_ARC)
                shortcuts++;
        }
        return shortcuts;
    }

    public int getArcs() {
        return getUpArcs() + getDownArcs();
    }

    public int getUpArcs() {
        return upHead.length;
    }

    public int getDownArcs() {
        return downHead.length;
    }

    public int getDownArcId(int downArc) {
        if (downArc < 0 || downArc >= getDownArcs())
            throw new IllegalArgumentException("downArc outside [0," + getDownArcs() + "): " + downArc);
        return getUpArcs() + downArc;
    }

    public int getOrder(int rank) {
        return order[rank];
    }

    public int getRank(int node) {
        return rank[node];
    }

    public int getUpArcStart(int node) {
        return upFirstOut[node];
    }

    public int getUpArcEnd(int node) {
        return upFirstOut[node + 1];
    }

    public int getUpHead(int upArc) {
        return upHead[upArc];
    }

    public int getDownArcStart(int node) {
        return downFirstOut[node];
    }

    public int getDownArcEnd(int node) {
        return downFirstOut[node + 1];
    }

    public int getDownHead(int downArc) {
        return downHead[downArc];
    }

    public int getBaseEdge(int cchArc) {
        return baseEdge[cchArc];
    }

    public boolean isReverse(int cchArc) {
        return reverse[cchArc];
    }

    public boolean isShortcut(int cchArc) {
        return getBaseEdge(cchArc) == NO_ARC;
    }

    public static Builder builder(int nodes) {
        return new Builder(nodes);
    }

    private static int[] copy(int[] values) {
        return Objects.requireNonNull(values, "array").clone();
    }

    private static int[] copyAndCheckLength(String name, int[] values, int expectedLength) {
        int[] copy = copy(values);
        if (copy.length != expectedLength)
            throw new IllegalArgumentException(name + " length must be " + expectedLength + ", but was " + copy.length);
        return copy;
    }

    private void checkHeads(String name, int[] heads) {
        for (int head : heads) {
            if (head < 0 || head >= nodes)
                throw new IllegalArgumentException(name + " contains node " + head + " outside [0," + nodes + ")");
        }
    }

    private static void checkFirstOut(String name, int[] firstOut, int entries) {
        if (firstOut[0] != 0)
            throw new IllegalArgumentException(name + " must start with 0");
        for (int i = 1; i < firstOut.length; i++) {
            if (firstOut[i] < firstOut[i - 1])
                throw new IllegalArgumentException(name + " must be monotonic");
        }
        if (firstOut[firstOut.length - 1] != entries)
            throw new IllegalArgumentException(name + " must end with " + entries);
    }

    private static void checkPermutation(String name, int[] values) {
        boolean[] seen = new boolean[values.length];
        for (int value : values) {
            if (value < 0 || value >= values.length)
                throw new IllegalArgumentException(name + " contains invalid node " + value);
            if (seen[value])
                throw new IllegalArgumentException(name + " contains duplicate node " + value);
            seen[value] = true;
        }
    }

    private void checkInverseOrderAndRank() {
        for (int i = 0; i < nodes; i++) {
            if (rank[order[i]] != i)
                throw new IllegalArgumentException("order and rank must be inverse arrays");
        }
    }

    public static final class Builder {
        private final int nodes;
        private int[] order;
        private int[] rank;
        private int[] upFirstOut;
        private int[] upHead = new int[0];
        private int[] downFirstOut;
        private int[] downHead = new int[0];
        private int[] baseEdge = new int[0];
        private boolean[] reverse = new boolean[0];

        private Builder(int nodes) {
            if (nodes < 0)
                throw new IllegalArgumentException("nodes must be >= 0");
            this.nodes = nodes;
            this.order = identity(nodes);
            this.rank = identity(nodes);
            this.upFirstOut = new int[nodes + 1];
            this.downFirstOut = new int[nodes + 1];
        }

        public Builder order(int[] order) {
            this.order = copy(order);
            return this;
        }

        public Builder rank(int[] rank) {
            this.rank = copy(rank);
            return this;
        }

        public Builder upwardGraph(int[] firstOut, int[] head) {
            this.upFirstOut = copy(firstOut);
            this.upHead = copy(head);
            return this;
        }

        public Builder downwardGraph(int[] firstOut, int[] head) {
            this.downFirstOut = copy(firstOut);
            this.downHead = copy(head);
            return this;
        }

        public Builder baseEdgeMapping(int[] baseEdge, boolean[] reverse) {
            this.baseEdge = copy(baseEdge);
            this.reverse = Objects.requireNonNull(reverse, "reverse").clone();
            return this;
        }

        public CCHStorage build() {
            return new CCHStorage(this);
        }

        private static int[] identity(int size) {
            int[] values = new int[size];
            Arrays.setAll(values, i -> i);
            return values;
        }
    }
}
