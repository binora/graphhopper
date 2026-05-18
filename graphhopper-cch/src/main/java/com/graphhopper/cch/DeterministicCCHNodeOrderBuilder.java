// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Deterministic v1 ordering that is correct and reproducible, but does not claim separator quality. Better nested
 * dissection implementations can replace this builder while still returning the same {@link CCHNodeOrder} abstraction.
 */
public final class DeterministicCCHNodeOrderBuilder implements CCHNodeOrderProvider {
    @Override
    public CCHNodeOrder build(CCHInputGraph inputGraph) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        int[] degree = new int[inputGraph.getNodes()];
        for (CCHInputEdge edge : inputGraph.getAllSupportEdges()) {
            degree[edge.getNodeA()]++;
            degree[edge.getNodeB()]++;
        }

        Integer[] nodes = new Integer[inputGraph.getNodes()];
        Arrays.setAll(nodes, i -> i);
        Arrays.sort(nodes, Comparator.comparingInt((Integer node) -> degree[node]).thenComparingInt(node -> node));

        int[] order = new int[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            order[i] = nodes[i];
        }
        return CCHNodeOrder.fromOrder(order);
    }
}
