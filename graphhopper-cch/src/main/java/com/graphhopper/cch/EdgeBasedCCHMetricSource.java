// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EdgeBasedCCHMetricSource implements CCHMetricSource {
    private final EdgeStateCCHInputGraph edgeStateInputGraph;
    private final Weighting weighting;
    private final List<CCHMetricCandidate> candidates;

    public EdgeBasedCCHMetricSource(BaseGraph graph, Weighting weighting, EdgeStateCCHInputGraph edgeStateInputGraph,
                                    CCHTopology topology) {
        Objects.requireNonNull(graph, "graph");
        this.weighting = Objects.requireNonNull(weighting, "weighting");
        this.edgeStateInputGraph = Objects.requireNonNull(edgeStateInputGraph, "edgeStateInputGraph");
        Objects.requireNonNull(topology, "topology");
        if (graph.getNodes() != edgeStateInputGraph.getBaseNodes())
            throw new IllegalArgumentException("graph and edge-state input base node counts differ: " + graph.getNodes() + " != " + edgeStateInputGraph.getBaseNodes());
        if (graph.getEdges() != edgeStateInputGraph.getBaseEdges())
            throw new IllegalArgumentException("graph and edge-state input base edge counts differ: " + graph.getEdges() + " != " + edgeStateInputGraph.getBaseEdges());
        if (topology.getNodes() != edgeStateInputGraph.getStates())
            throw new IllegalArgumentException("topology and edge-state input counts differ: " + topology.getNodes() + " != " + edgeStateInputGraph.getStates());
        this.candidates = buildCandidates(graph, weighting, edgeStateInputGraph, topology);
    }

    @Override
    public int getNodes() {
        return edgeStateInputGraph.getStates();
    }

    @Override
    public TraversalMode getTraversalMode() {
        return TraversalMode.EDGE_BASED;
    }

    @Override
    public boolean hasTurnCosts() {
        return weighting.hasTurnCosts();
    }

    @Override
    public int getCandidates() {
        return candidates.size();
    }

    @Override
    public CCHMetricCandidate getCandidate(int index) {
        if (index < 0 || index >= candidates.size())
            throw new IllegalArgumentException("candidate outside [0," + candidates.size() + "): " + index);
        return candidates.get(index);
    }

    private static List<CCHMetricCandidate> buildCandidates(BaseGraph graph, Weighting weighting,
                                                            EdgeStateCCHInputGraph edgeStateInputGraph,
                                                            CCHTopology topology) {
        List<CCHMetricCandidate> candidates = new ArrayList<>();
        CCHInputGraph inputGraph = edgeStateInputGraph.getInputGraph();
        for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
            CCHMetricCandidate candidate = candidate(graph, weighting, edgeStateInputGraph, topology, inputArc);
            if (candidate != null)
                candidates.add(candidate);
        }
        return candidates;
    }

    private static CCHMetricCandidate candidate(BaseGraph graph, Weighting weighting,
                                                EdgeStateCCHInputGraph edgeStateInputGraph, CCHTopology topology,
                                                int inputArc) {
        int inEdgeKey = edgeStateInputGraph.getTransitionInEdgeKey(inputArc);
        int viaNode = edgeStateInputGraph.getTransitionViaNode(inputArc);
        int outEdgeKey = edgeStateInputGraph.getTransitionOutEdgeKey(inputArc);
        EdgeIteratorState outEdgeState = graph.getEdgeIteratorStateForKey(outEdgeKey);
        double edgeWeight = weighting.calcEdgeWeight(outEdgeState, false);
        if (Double.isNaN(edgeWeight))
            throw invalidMetric("edge weight", outEdgeKey, "NaN");
        if (edgeWeight == Double.POSITIVE_INFINITY)
            return null;
        if (!Double.isFinite(edgeWeight) || edgeWeight < 0)
            throw invalidMetric("edge weight", outEdgeKey, String.valueOf(edgeWeight));

        long edgeMillis = weighting.calcEdgeMillis(outEdgeState, false);
        if (edgeMillis < 0)
            throw new IllegalArgumentException("Negative edge millis for edge key " + outEdgeKey + ": " + edgeMillis);

        int inEdge = GHUtility.getEdgeFromEdgeKey(inEdgeKey);
        int outEdge = GHUtility.getEdgeFromEdgeKey(outEdgeKey);
        double turnWeight = weighting.calcTurnWeight(inEdge, viaNode, outEdge);
        if (Double.isNaN(turnWeight))
            throw invalidMetric("turn weight", outEdgeKey, "NaN");
        if (turnWeight == Double.POSITIVE_INFINITY)
            return null;
        if (!Double.isFinite(turnWeight) || turnWeight < 0)
            throw invalidMetric("turn weight", outEdgeKey, String.valueOf(turnWeight));

        long turnMillis = weighting.calcTurnMillis(inEdge, viaNode, outEdge);
        if (turnMillis < 0)
            throw new IllegalArgumentException("Negative turn millis for transition " + inEdgeKey + "->" + outEdgeKey + ": " + turnMillis);

        double weight = edgeWeight + turnWeight;
        if (!Double.isFinite(weight))
            return null;
        long millis = saturatedAdd(edgeMillis, turnMillis);
        CCHInputArc arc = edgeStateInputGraph.getInputGraph().getArc(inputArc);
        CCHMetricProvenance provenance = CCHMetricProvenance.edgeTransition(
                inEdgeKey,
                viaNode,
                outEdgeKey,
                GHUtility.getEdgeFromEdgeKey(outEdgeKey),
                (outEdgeKey & 1) == 1,
                turnWeight,
                turnMillis,
                edgeWeight,
                edgeMillis,
                outEdgeState.getDistance());
        return new CCHMetricCandidate(
                topology.getInputArcCCHArc(inputArc),
                weight,
                millis,
                outEdgeState.getDistance(),
                provenance,
                tieBreakKey(inEdgeKey, outEdgeKey, arc.getFrom(), arc.getTo()));
    }

    private static long saturatedAdd(long first, long second) {
        if (first == Long.MAX_VALUE || second == Long.MAX_VALUE)
            return Long.MAX_VALUE;
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static long tieBreakKey(int inEdgeKey, int outEdgeKey, int fromState, int toState) {
        long edgeKeys = (Integer.toUnsignedLong(inEdgeKey) << 32) | Integer.toUnsignedLong(outEdgeKey);
        long states = (((long) fromState) << 32) ^ (toState & 0xffffffffL);
        return edgeKeys ^ states;
    }

    private static IllegalArgumentException invalidMetric(String name, int outEdgeKey, String value) {
        return new IllegalArgumentException("Invalid " + name + " for edge key " + outEdgeKey + ": " + value);
    }
}
