// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;

import java.util.Objects;

public final class EdgeStateCCHTopologyBuilder {
    private final CCHTopologyBuilder topologyBuilder = new CCHTopologyBuilder();
    private final EdgeStateCCHNodeOrderBuilder edgeOrderBuilder = new EdgeStateCCHNodeOrderBuilder();

    public EdgeStateCCHTopology build(EdgeStateCCHInputGraph edgeStateInputGraph, CCHNodeOrder order) {
        Objects.requireNonNull(edgeStateInputGraph, "edgeStateInputGraph");
        Objects.requireNonNull(order, "order");
        if (edgeStateInputGraph.getStates() != order.getNodes())
            throw new IllegalArgumentException("edge-state count and order node count differ: " + edgeStateInputGraph.getStates() + " != " + order.getNodes());
        CCHTopology topology = topologyBuilder.build(edgeStateInputGraph.getInputGraph(), order);
        return new EdgeStateCCHTopology(edgeStateInputGraph, topology);
    }

    public EdgeStateCCHTopology build(BaseGraph graph, CCHNodeOrder order) {
        return build(EdgeStateCCHInputBuilder.fromGraph(graph), order);
    }

    public EdgeStateCCHTopology buildFromBaseNodeOrder(EdgeStateCCHInputGraph edgeStateInputGraph,
                                                       CCHNodeOrder baseNodeOrder) {
        return build(edgeStateInputGraph, edgeOrderBuilder.build(edgeStateInputGraph, baseNodeOrder));
    }

    public EdgeStateCCHTopology buildFromBaseNodeOrder(BaseGraph graph, CCHNodeOrder baseNodeOrder) {
        return buildFromBaseNodeOrder(EdgeStateCCHInputBuilder.fromGraph(graph), baseNodeOrder);
    }
}
