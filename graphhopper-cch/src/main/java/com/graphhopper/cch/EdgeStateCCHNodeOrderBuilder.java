// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Lifts a base-node CCH order into the edge-state graph used by edge-based CCH.
 * <p>
 * An edge state represents "we traversed this directed edge and are now at its head node", so the primary ordering key
 * is the rank of the state's head/via base node. Edge key and state id are stable tie-breakers.
 */
public final class EdgeStateCCHNodeOrderBuilder {

    public CCHNodeOrder build(EdgeStateCCHInputGraph edgeStateInputGraph, CCHNodeOrder baseNodeOrder) {
        Objects.requireNonNull(edgeStateInputGraph, "edgeStateInputGraph");
        Objects.requireNonNull(baseNodeOrder, "baseNodeOrder");
        if (edgeStateInputGraph.getBaseNodes() != baseNodeOrder.getNodes())
            throw new IllegalArgumentException("base node order count and edge-state base node count differ: "
                    + baseNodeOrder.getNodes() + " != " + edgeStateInputGraph.getBaseNodes());

        Integer[] states = new Integer[edgeStateInputGraph.getStates()];
        Arrays.setAll(states, i -> i);
        Arrays.sort(states, Comparator
                .comparingInt((Integer state) -> baseNodeOrder.getRank(edgeStateInputGraph.getStateHeadNode(state)))
                .thenComparingInt(edgeStateInputGraph::getStateEdgeKey)
                .thenComparingInt(state -> state));

        int[] order = new int[states.length];
        for (int rank = 0; rank < states.length; rank++) {
            order[rank] = states[rank];
        }
        return CCHNodeOrder.fromOrder(order);
    }

    public CCHNodeOrder build(BaseGraph graph, CCHNodeOrder baseNodeOrder) {
        return build(EdgeStateCCHInputBuilder.fromGraph(Objects.requireNonNull(graph, "graph")), baseNodeOrder);
    }
}
