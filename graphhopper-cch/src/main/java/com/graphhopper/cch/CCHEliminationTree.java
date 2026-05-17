// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;
import java.util.function.IntConsumer;

public final class CCHEliminationTree {
    public static final int NO_NODE = -1;

    private final CCHTopology topology;
    private final int[] parent;

    public CCHEliminationTree(CCHTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
        parent = new int[topology.getNodes()];
        for (int node = 0; node < topology.getNodes(); node++) {
            parent[node] = findParent(topology, node);
        }
        validateParentRanks();
    }

    public int getNodes() {
        return parent.length;
    }

    public int getParent(int node) {
        checkNode(node);
        return parent[node];
    }

    public int[] getParentArray() {
        return parent.clone();
    }

    public void forEachAncestor(int node, IntConsumer consumer) {
        checkNode(node);
        Objects.requireNonNull(consumer, "consumer");
        int current = node;
        while (current != NO_NODE) {
            consumer.accept(current);
            current = parent[current];
        }
    }

    private static int findParent(CCHTopology topology, int node) {
        int parent = NO_NODE;
        int parentRank = Integer.MAX_VALUE;
        CCHNodeOrder order = topology.getNodeOrder();
        for (int arc = topology.getUpArcStart(node); arc < topology.getUpArcEnd(node); arc++) {
            int head = topology.getUpHead(arc);
            int rank = order.getRank(head);
            if (rank < parentRank) {
                parent = head;
                parentRank = rank;
            }
        }
        return parent;
    }

    private void validateParentRanks() {
        CCHNodeOrder order = topology.getNodeOrder();
        for (int node = 0; node < parent.length; node++) {
            if (parent[node] != NO_NODE && order.getRank(parent[node]) <= order.getRank(node))
                throw new IllegalArgumentException("elimination tree parent must have higher rank: " + node + " -> " + parent[node]);
        }
    }

    private void checkNode(int node) {
        if (node < 0 || node >= parent.length)
            throw new IllegalArgumentException("node outside [0," + parent.length + "): " + node);
    }
}
