// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;

import java.util.Objects;

public final class NodeBasedCCHMetricSource implements CCHMetricSource {
    private final CCHTopology topology;
    private final CCHInputGraph inputGraph;

    public NodeBasedCCHMetricSource(CCHTopology topology, CCHInputGraph inputGraph) {
        this.topology = Objects.requireNonNull(topology, "topology");
        this.inputGraph = Objects.requireNonNull(inputGraph, "inputGraph");
        if (topology.getNodes() != inputGraph.getNodes())
            throw new IllegalArgumentException("topology and input graph node counts differ: " + topology.getNodes() + " != " + inputGraph.getNodes());
    }

    @Override
    public int getNodes() {
        return inputGraph.getNodes();
    }

    @Override
    public TraversalMode getTraversalMode() {
        return TraversalMode.NODE_BASED;
    }

    @Override
    public boolean hasTurnCosts() {
        return false;
    }

    @Override
    public int getCandidates() {
        return inputGraph.getArcs();
    }

    @Override
    public CCHMetricCandidate getCandidate(int index) {
        if (index < 0 || index >= inputGraph.getArcs())
            throw new IllegalArgumentException("candidate outside [0," + inputGraph.getArcs() + "): " + index);
        CCHInputArc arc = inputGraph.getArc(index);
        return new CCHMetricCandidate(
                topology.getInputArcCCHArc(index),
                arc.getWeight(),
                arc.getMillis(),
                arc.getDistance(),
                CCHMetricProvenance.direct(arc.getBaseEdge(), arc.isReverse()),
                (((long) arc.getBaseEdge()) << 1) | (arc.isReverse() ? 1L : 0L));
    }
}
