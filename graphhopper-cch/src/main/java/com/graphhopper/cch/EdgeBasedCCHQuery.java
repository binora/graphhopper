// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EdgeBasedCCHQuery {
    private final BaseGraph graph;
    private final Weighting weighting;
    private final EdgeStateCCHTopology edgeTopology;
    private final EdgeStateCCHInputGraph edgeStateInputGraph;
    private final CCHTopology topology;
    private final CCHMetric metric;
    private final NodeBasedCCHQuery coreQuery;

    public EdgeBasedCCHQuery(BaseGraph graph, Weighting weighting, EdgeStateCCHTopology edgeTopology, CCHMetric metric) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.weighting = Objects.requireNonNull(weighting, "weighting");
        this.edgeTopology = Objects.requireNonNull(edgeTopology, "edgeTopology");
        this.edgeStateInputGraph = edgeTopology.getEdgeStateInputGraph();
        this.topology = edgeTopology.getTopology();
        this.metric = Objects.requireNonNull(metric, "metric");
        if (graph.getNodes() != edgeTopology.getBaseNodes())
            throw new IllegalArgumentException("graph and edge topology base node counts differ: " + graph.getNodes() + " != " + edgeTopology.getBaseNodes());
        if (graph.getEdges() != edgeTopology.getBaseEdges())
            throw new IllegalArgumentException("graph and edge topology base edge counts differ: " + graph.getEdges() + " != " + edgeTopology.getBaseEdges());
        if (metric.getArcs() != topology.getArcs())
            throw new IllegalArgumentException("metric and topology arc counts differ: " + metric.getArcs() + " != " + topology.getArcs());
        this.coreQuery = new NodeBasedCCHQuery(topology, metric);
    }

    public EdgeBasedCCHQueryResult calc(int sourceNode, int targetNode) {
        checkBaseNode("sourceNode", sourceNode);
        checkBaseNode("targetNode", targetNode);
        if (sourceNode == targetNode)
            return EdgeBasedCCHQueryResult.sameNode(sourceNode);

        List<StartState> starts = startStates(sourceNode);
        List<Integer> targets = targetStates(targetNode);
        Candidate best = null;
        int visitedNodes = 0;
        for (StartState start : starts) {
            for (int targetState : targets) {
                CCHQueryResult core = coreQuery.calc(start.state, targetState);
                visitedNodes += core.getVisitedNodes();
                if (!core.isFound())
                    continue;
                Candidate candidate = new Candidate(start, targetState, core,
                        start.weight + core.getWeight(),
                        saturatedAdd(start.millis, coreMillis(core)),
                        start.distance + coreDistance(core));
                if (best == null || candidate.isBetterThan(best))
                    best = candidate;
            }
        }
        if (best == null)
            return EdgeBasedCCHQueryResult.notFound(sourceNode, targetNode, visitedNodes);
        return new EdgeBasedCCHQueryResult(true, sourceNode, targetNode, best.start.state, best.targetState,
                best.start.edgeKey, best.start.weight, best.start.millis, best.start.distance,
                best.weight, best.millis, best.distance, visitedNodes, best.coreResult);
    }

    private List<StartState> startStates(int sourceNode) {
        List<StartState> states = new ArrayList<>();
        for (int state = 0; state < edgeStateInputGraph.getStates(); state++) {
            if (edgeStateInputGraph.getStateTailNode(state) != sourceNode)
                continue;
            int edgeKey = edgeStateInputGraph.getStateEdgeKey(state);
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeKey);
            double weight = weighting.calcEdgeWeight(edge, false);
            if (Double.isNaN(weight))
                throw new IllegalArgumentException("Invalid first-edge weight for edge key " + edgeKey + ": NaN");
            if (weight == Double.POSITIVE_INFINITY)
                continue;
            if (!Double.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid first-edge weight for edge key " + edgeKey + ": " + weight);
            long millis = weighting.calcEdgeMillis(edge, false);
            if (millis < 0)
                throw new IllegalArgumentException("Negative first-edge millis for edge key " + edgeKey + ": " + millis);
            states.add(new StartState(state, edgeKey, weight, millis, edge.getDistance()));
        }
        return states;
    }

    private List<Integer> targetStates(int targetNode) {
        List<Integer> states = new ArrayList<>();
        for (int state = 0; state < edgeStateInputGraph.getStates(); state++) {
            if (edgeStateInputGraph.getStateHeadNode(state) == targetNode)
                states.add(state);
        }
        return states;
    }

    private double coreDistance(CCHQueryResult core) {
        if (core.getSource() == core.getTarget())
            return 0;
        return sumCoreMetric(core, false);
    }

    private long coreMillis(CCHQueryResult core) {
        if (core.getSource() == core.getTarget())
            return 0;
        return (long) sumCoreMetric(core, true);
    }

    private double sumCoreMetric(CCHQueryResult core, boolean millis) {
        double sum = 0;
        for (int cchArc : reconstructCoreArcs(core)) {
            sum += millis ? metric.getMillis(cchArc) : metric.getDistance(cchArc);
        }
        return sum;
    }

    private List<Integer> reconstructCoreArcs(CCHQueryResult core) {
        List<Integer> arcs = new ArrayList<>();
        List<Integer> forward = new ArrayList<>();
        int[] forwardPredecessorArc = core.getForwardPredecessorArcArray();
        int state = core.getMeetingNode();
        while (state != core.getSource()) {
            int cchArc = forwardPredecessorArc[state];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing forward predecessor arc at state " + state);
            forward.add(cchArc);
            state = topology.getTail(cchArc);
        }
        for (int i = forward.size() - 1; i >= 0; i--) {
            arcs.add(forward.get(i));
        }
        int[] backwardPredecessorArc = core.getBackwardPredecessorArcArray();
        state = core.getMeetingNode();
        while (state != core.getTarget()) {
            int cchArc = backwardPredecessorArc[state];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing backward predecessor arc at state " + state);
            arcs.add(cchArc);
            state = topology.getHead(cchArc);
        }
        return arcs;
    }

    private void checkBaseNode(String name, int node) {
        if (node < 0 || node >= graph.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + graph.getNodes() + "): " + node);
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static final class StartState {
        private final int state;
        private final int edgeKey;
        private final double weight;
        private final long millis;
        private final double distance;

        private StartState(int state, int edgeKey, double weight, long millis, double distance) {
            this.state = state;
            this.edgeKey = edgeKey;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
        }
    }

    private static final class Candidate {
        private final StartState start;
        private final int targetState;
        private final CCHQueryResult coreResult;
        private final double weight;
        private final long millis;
        private final double distance;

        private Candidate(StartState start, int targetState, CCHQueryResult coreResult,
                          double weight, long millis, double distance) {
            this.start = start;
            this.targetState = targetState;
            this.coreResult = coreResult;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
        }

        private boolean isBetterThan(Candidate other) {
            int byWeight = Double.compare(weight, other.weight);
            if (byWeight != 0)
                return byWeight < 0;
            int byMillis = Long.compare(millis, other.millis);
            if (byMillis != 0)
                return byMillis < 0;
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0)
                return byDistance < 0;
            if (start.state != other.start.state)
                return start.state < other.start.state;
            if (targetState != other.targetState)
                return targetState < other.targetState;
            return start.edgeKey < other.start.edgeKey;
        }
    }
}
