// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BaseGraphCCHMetricSource implements CCHMetricSource {
    private final BaseGraph graph;
    private final List<CCHMetricCandidate> candidates;

    public BaseGraphCCHMetricSource(BaseGraph graph, Weighting weighting, CCHTopology topology) {
        this.graph = Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(weighting, "weighting");
        Objects.requireNonNull(topology, "topology");
        if (graph.getNodes() != topology.getNodes())
            throw new IllegalArgumentException("graph and CCH topology node counts differ: " + graph.getNodes() + " != " + topology.getNodes());
        if (weighting.hasTurnCosts())
            throw new IllegalArgumentException("graphhopper-cch v1 only supports node-based metric sources without turn costs");
        this.candidates = buildCandidates(graph, weighting, topology);
    }

    @Override
    public int getNodes() {
        return graph.getNodes();
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
        return candidates.size();
    }

    @Override
    public CCHMetricCandidate getCandidate(int index) {
        if (index < 0 || index >= candidates.size())
            throw new IllegalArgumentException("candidate outside [0," + candidates.size() + "): " + index);
        return candidates.get(index);
    }

    private static List<CCHMetricCandidate> buildCandidates(BaseGraph graph, Weighting weighting, CCHTopology topology) {
        List<CCHMetricCandidate> candidates = new ArrayList<>();
        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            if (edge.getBaseNode() == edge.getAdjNode())
                continue;
            addCandidateIfAccessible(candidates, edge, weighting, topology, false);
            addCandidateIfAccessible(candidates, edge, weighting, topology, true);
        }
        return candidates;
    }

    private static void addCandidateIfAccessible(List<CCHMetricCandidate> candidates, AllEdgesIterator edge,
                                                 Weighting weighting, CCHTopology topology, boolean reverse) {
        double weight = weighting.calcEdgeWeight(edge, reverse);
        if (Double.isNaN(weight))
            throw invalidWeight(edge.getEdge(), reverse, "NaN");
        if (weight == Double.POSITIVE_INFINITY)
            return;
        if (!Double.isFinite(weight))
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));
        if (weight < 0)
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));

        long millis = weighting.calcEdgeMillis(edge, reverse);
        if (millis < 0)
            throw new IllegalArgumentException("Negative millis for edge " + edge.getEdge() + ", reverse=" + reverse + ": " + millis);

        int from = reverse ? edge.getAdjNode() : edge.getBaseNode();
        int to = reverse ? edge.getBaseNode() : edge.getAdjNode();
        int cchArc = topology.findArc(from, to);
        if (cchArc == CCHStorage.NO_ARC)
            throw new IllegalStateException("missing CCH topology arc for base edge " + edge.getEdge() + ": " + from + "->" + to);

        candidates.add(new CCHMetricCandidate(
                cchArc,
                weight,
                millis,
                edge.getDistance(),
                CCHMetricProvenance.direct(edge.getEdge(), reverse),
                (((long) edge.getEdge()) << 1) | (reverse ? 1L : 0L)));
    }

    private static IllegalArgumentException invalidWeight(int edge, boolean reverse, String weight) {
        return new IllegalArgumentException("Invalid weight for edge " + edge + ", reverse=" + reverse + ": " + weight);
    }
}
