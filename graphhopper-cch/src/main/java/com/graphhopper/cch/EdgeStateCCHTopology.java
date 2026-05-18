// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class EdgeStateCCHTopology {
    private final EdgeStateCCHInputGraph edgeStateInputGraph;
    private final CCHTopology topology;

    public EdgeStateCCHTopology(EdgeStateCCHInputGraph edgeStateInputGraph, CCHTopology topology) {
        this.edgeStateInputGraph = Objects.requireNonNull(edgeStateInputGraph, "edgeStateInputGraph");
        this.topology = Objects.requireNonNull(topology, "topology");
        if (edgeStateInputGraph.getStates() != topology.getNodes())
            throw new IllegalArgumentException("edge-state count and CCH topology node count differ: " + edgeStateInputGraph.getStates() + " != " + topology.getNodes());
        if (edgeStateInputGraph.getInputGraph().getArcs() != topology.getInputArcCCHArcArray().length)
            throw new IllegalArgumentException("edge-state input arc count and CCH topology input mapping length differ: "
                    + edgeStateInputGraph.getInputGraph().getArcs() + " != " + topology.getInputArcCCHArcArray().length);
    }

    public int getBaseNodes() {
        return edgeStateInputGraph.getBaseNodes();
    }

    public int getBaseEdges() {
        return edgeStateInputGraph.getBaseEdges();
    }

    public int getStates() {
        return edgeStateInputGraph.getStates();
    }

    public int getArcs() {
        return topology.getArcs();
    }

    public EdgeStateCCHInputGraph getEdgeStateInputGraph() {
        return edgeStateInputGraph;
    }

    public CCHTopology getTopology() {
        return topology;
    }

    public CCHNodeOrder getNodeOrder() {
        return topology.getNodeOrder();
    }
}
