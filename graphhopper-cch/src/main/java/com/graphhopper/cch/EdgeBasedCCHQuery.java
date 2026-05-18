// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

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
        return calcBaseNodes(sourceNode, targetNode);
    }

    public EdgeBasedCCHQueryResult calc(QueryGraph queryGraph, int sourceNode, int targetNode) {
        Objects.requireNonNull(queryGraph, "queryGraph");
        if (queryGraph.getBaseGraph().getNodes() != graph.getNodes())
            throw new IllegalArgumentException("query graph base node count and edge topology base node count differ: "
                    + queryGraph.getBaseGraph().getNodes() + " != " + graph.getNodes());
        checkQueryGraphNode("sourceNode", sourceNode, queryGraph);
        checkQueryGraphNode("targetNode", targetNode, queryGraph);
        if (isCoreBaseNode(queryGraph, sourceNode) && isCoreBaseNode(queryGraph, targetNode))
            return calcBaseNodes(sourceNode, targetNode);

        Weighting queryWeighting = queryGraph.wrapWeighting(weighting);
        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder(true)
                .build(sourceNode, targetNode, queryGraph, graph.getNodes(), queryWeighting);
        return calcWithBoundaryOverlay(queryGraph, queryWeighting, overlay);
    }

    private EdgeBasedCCHQueryResult calcBaseNodes(int sourceNode, int targetNode) {
        if (sourceNode == targetNode)
            return EdgeBasedCCHQueryResult.sameNode(sourceNode);
        return calcBest(sourceNode, targetNode, startStates(sourceNode, weighting), targetStates(targetNode), null);
    }

    private EdgeBasedCCHQueryResult calcWithBoundaryOverlay(QueryGraph queryGraph, Weighting queryWeighting,
                                                           CCHBoundaryOverlay overlay) {
        if (overlay.getSourceNode() == overlay.getTargetNode())
            return EdgeBasedCCHQueryResult.sameNode(overlay.getSourceNode());

        Candidate best = null;
        for (CCHBoundaryArc directArc : overlay.getDirectSourceToTargetArcs()) {
            best = better(best, Candidate.direct(directArc));
        }

        List<StartState> starts = sourceStates(queryGraph, overlay, queryWeighting);
        List<TargetState> targets = targetStates(queryGraph, overlay, queryWeighting);
        return calcBest(overlay.getSourceNode(), overlay.getTargetNode(), starts, targets, best);
    }

    private EdgeBasedCCHQueryResult calcBest(int sourceNode, int targetNode, List<StartState> starts,
                                             List<TargetState> targets, Candidate initialBest) {
        Candidate best = initialBest;
        int visitedNodes = 0;
        for (StartState start : starts) {
            for (TargetState target : targets) {
                CCHQueryResult core = coreQuery.calc(start.state, target.state);
                visitedNodes += core.getVisitedNodes();
                if (!core.isFound())
                    continue;
                List<Integer> coreArcs = reconstructCoreArcs(core);
                if (usesForbiddenBaseEdge(start, coreArcs, target))
                    continue;
                Candidate candidate = Candidate.routed(start, target, core,
                        start.weight + core.getWeight() + target.weight,
                        saturatedAdd(saturatedAdd(start.millis, sumCoreMillis(coreArcs)), target.millis),
                        start.distance + sumCoreDistance(coreArcs) + target.distance,
                        start.segmentCount + coreArcs.size() + target.segmentCount);
                best = better(best, candidate);
            }
        }
        if (best == null)
            return EdgeBasedCCHQueryResult.notFound(sourceNode, targetNode, visitedNodes);
        return best.toResult(sourceNode, targetNode, visitedNodes);
    }

    private List<StartState> sourceStates(QueryGraph queryGraph, CCHBoundaryOverlay overlay, Weighting queryWeighting) {
        if (overlay.isSourceCoreNode())
            return startStates(overlay.getSourceNode(), queryWeighting);
        List<StartState> starts = new ArrayList<>();
        for (CCHBoundaryArc arc : overlay.getSourceOutgoingArcs()) {
            if (!isCoreBaseNode(queryGraph, arc.getHeadNode()))
                continue;
            int state = stateForBoundaryArc(arc);
            if (edgeStateInputGraph.getStateHeadNode(state) != arc.getHeadNode())
                throw new IllegalStateException("source boundary arc " + arc + " maps to edge-state ending at "
                        + edgeStateInputGraph.getStateHeadNode(state));
            starts.add(StartState.boundary(state, arc));
        }
        return starts;
    }

    private List<StartState> startStates(int sourceNode, Weighting edgeWeighting) {
        List<StartState> states = new ArrayList<>();
        for (int state = 0; state < edgeStateInputGraph.getStates(); state++) {
            if (edgeStateInputGraph.getStateTailNode(state) != sourceNode)
                continue;
            int edgeKey = edgeStateInputGraph.getStateEdgeKey(state);
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeKey);
            double weight = edgeWeight(edgeWeighting, edge, false, "first-edge", edgeKey);
            if (weight == Double.POSITIVE_INFINITY)
                continue;
            long millis = edgeMillis(edgeWeighting, edge, false, "first-edge", edgeKey);
            states.add(StartState.fullEdge(state, edgeKey, weight, millis, edge.getDistance()));
        }
        return states;
    }

    private List<TargetState> targetStates(QueryGraph queryGraph, CCHBoundaryOverlay overlay, Weighting queryWeighting) {
        if (overlay.isTargetCoreNode())
            return targetStates(overlay.getTargetNode());
        List<TargetState> targets = new ArrayList<>();
        for (CCHBoundaryArc arc : overlay.getTargetIncomingArcs()) {
            if (!isCoreBaseNode(queryGraph, arc.getTailNode()))
                continue;
            for (int state = 0; state < edgeStateInputGraph.getStates(); state++) {
                if (edgeStateInputGraph.getStateHeadNode(state) != arc.getTailNode())
                    continue;
                double turnWeight = turnWeight(queryWeighting, state, arc);
                if (turnWeight == Double.POSITIVE_INFINITY)
                    continue;
                targets.add(TargetState.boundary(state, arc, turnWeight, turnMillis(queryWeighting, state, arc)));
            }
        }
        return targets;
    }

    private List<TargetState> targetStates(int targetNode) {
        List<TargetState> states = new ArrayList<>();
        for (int state = 0; state < edgeStateInputGraph.getStates(); state++) {
            if (edgeStateInputGraph.getStateHeadNode(state) == targetNode)
                states.add(TargetState.core(state));
        }
        return states;
    }

    private boolean usesForbiddenBaseEdge(StartState start, List<Integer> coreArcs, TargetState target) {
        Set<Integer> forbidden = new HashSet<>(start.forbiddenBaseEdges);
        forbidden.addAll(target.forbiddenBaseEdges);
        if (forbidden.isEmpty())
            return false;
        if (start.firstEdgeKey != CCHStorage.NO_ARC && forbidden.contains(GHUtility.getEdgeFromEdgeKey(start.firstEdgeKey)))
            return true;
        for (int cchArc : coreArcs) {
            if (arcUsesForbiddenBaseEdge(cchArc, forbidden))
                return true;
        }
        return false;
    }

    private boolean arcUsesForbiddenBaseEdge(int cchArc, Set<Integer> forbiddenBaseEdges) {
        CCHMetricProvenance provenance = metric.getProvenance(cchArc);
        if (provenance.isEdgeTransition())
            return forbiddenBaseEdges.contains(provenance.getOutgoingBaseEdge());
        if (provenance.isShortcut())
            return arcUsesForbiddenBaseEdge(provenance.getFirstSkippedArc(), forbiddenBaseEdges)
                    || arcUsesForbiddenBaseEdge(provenance.getSecondSkippedArc(), forbiddenBaseEdges);
        return false;
    }

    private double sumCoreDistance(List<Integer> coreArcs) {
        double sum = 0;
        for (int cchArc : coreArcs) {
            sum += metric.getDistance(cchArc);
        }
        return sum;
    }

    private long sumCoreMillis(List<Integer> coreArcs) {
        long sum = 0;
        for (int cchArc : coreArcs) {
            sum = saturatedAdd(sum, metric.getMillis(cchArc));
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

    private int stateForBoundaryArc(CCHBoundaryArc arc) {
        int state = edgeStateInputGraph.getStateForEdgeKey(arc.getOriginalEdgeKey());
        if (state == EdgeStateCCHInputGraph.NO_STATE)
            throw new IllegalStateException("boundary arc " + arc + " does not map to a prepared edge-state");
        return state;
    }

    private boolean isCoreBaseNode(QueryGraph queryGraph, int node) {
        return node >= 0 && node < graph.getNodes() && !queryGraph.isVirtualNode(node);
    }

    private double turnWeight(Weighting queryWeighting, int inState, CCHBoundaryArc outArc) {
        int inEdge = edgeStateInputGraph.getStateBaseEdge(inState);
        double turnWeight = queryWeighting.calcTurnWeight(inEdge, outArc.getTailNode(), outArc.getEdgeId());
        if (Double.isNaN(turnWeight))
            throw new IllegalArgumentException("Invalid target-boundary turn weight for edge " + inEdge
                    + " -> " + outArc.getEdgeId() + " via " + outArc.getTailNode() + ": NaN");
        if (turnWeight == Double.POSITIVE_INFINITY)
            return Double.POSITIVE_INFINITY;
        if (!Double.isFinite(turnWeight) || turnWeight < 0)
            throw new IllegalArgumentException("Invalid target-boundary turn weight for edge " + inEdge
                    + " -> " + outArc.getEdgeId() + " via " + outArc.getTailNode() + ": " + turnWeight);
        return turnWeight;
    }

    private long turnMillis(Weighting queryWeighting, int inState, CCHBoundaryArc outArc) {
        int inEdge = edgeStateInputGraph.getStateBaseEdge(inState);
        long millis = queryWeighting.calcTurnMillis(inEdge, outArc.getTailNode(), outArc.getEdgeId());
        if (millis < 0)
            throw new IllegalArgumentException("Negative target-boundary turn millis for edge " + inEdge
                    + " -> " + outArc.getEdgeId() + " via " + outArc.getTailNode() + ": " + millis);
        return millis;
    }

    private static double edgeWeight(Weighting weighting, EdgeIteratorState edge, boolean reverse, String label, int edgeKey) {
        double weight = weighting.calcEdgeWeight(edge, reverse);
        if (Double.isNaN(weight))
            throw new IllegalArgumentException("Invalid " + label + " weight for edge key " + edgeKey + ": NaN");
        if (weight == Double.POSITIVE_INFINITY)
            return Double.POSITIVE_INFINITY;
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("Invalid " + label + " weight for edge key " + edgeKey + ": " + weight);
        return weight;
    }

    private static long edgeMillis(Weighting weighting, EdgeIteratorState edge, boolean reverse, String label, int edgeKey) {
        long millis = weighting.calcEdgeMillis(edge, reverse);
        if (millis < 0)
            throw new IllegalArgumentException("Negative " + label + " millis for edge key " + edgeKey + ": " + millis);
        return millis;
    }

    private void checkBaseNode(String name, int node) {
        if (node < 0 || node >= graph.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + graph.getNodes() + "): " + node);
    }

    private static void checkQueryGraphNode(String name, int node, QueryGraph queryGraph) {
        if (node < 0 || node >= queryGraph.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + queryGraph.getNodes() + "): " + node);
    }

    private static Candidate better(Candidate current, Candidate candidate) {
        if (candidate == null)
            return current;
        if (current == null || candidate.isBetterThan(current))
            return candidate;
        return current;
    }

    private static long saturatedAdd(long first, long second) {
        if (first == Long.MAX_VALUE || second == Long.MAX_VALUE)
            return Long.MAX_VALUE;
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static final class StartState {
        private final int state;
        private final int firstEdgeKey;
        private final CCHBoundaryArc boundaryArc;
        private final double weight;
        private final long millis;
        private final double distance;
        private final int segmentCount;
        private final Set<Integer> forbiddenBaseEdges;

        private StartState(int state, int firstEdgeKey, CCHBoundaryArc boundaryArc,
                           double weight, long millis, double distance, int segmentCount,
                           Set<Integer> forbiddenBaseEdges) {
            this.state = state;
            this.firstEdgeKey = firstEdgeKey;
            this.boundaryArc = boundaryArc;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
            this.segmentCount = segmentCount;
            this.forbiddenBaseEdges = forbiddenBaseEdges;
        }

        private static StartState fullEdge(int state, int edgeKey, double weight, long millis, double distance) {
            return new StartState(state, edgeKey, null, weight, millis, distance, 1, new HashSet<>());
        }

        private static StartState boundary(int state, CCHBoundaryArc arc) {
            Set<Integer> forbidden = new HashSet<>();
            if (arc.isVirtualEdge())
                forbidden.add(GHUtility.getEdgeFromEdgeKey(arc.getOriginalEdgeKey()));
            return new StartState(state, CCHStorage.NO_ARC, arc, arc.getWeight(), arc.getMillis(), arc.getDistance(), 1, forbidden);
        }
    }

    private static final class TargetState {
        private final int state;
        private final CCHBoundaryArc boundaryArc;
        private final double turnWeight;
        private final long turnMillis;
        private final double weight;
        private final long millis;
        private final double distance;
        private final int segmentCount;
        private final Set<Integer> forbiddenBaseEdges;

        private TargetState(int state, CCHBoundaryArc boundaryArc, double turnWeight, long turnMillis,
                            double weight, long millis, double distance, int segmentCount,
                            Set<Integer> forbiddenBaseEdges) {
            this.state = state;
            this.boundaryArc = boundaryArc;
            this.turnWeight = turnWeight;
            this.turnMillis = turnMillis;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
            this.segmentCount = segmentCount;
            this.forbiddenBaseEdges = forbiddenBaseEdges;
        }

        private static TargetState core(int state) {
            return new TargetState(state, null, 0, 0, 0, 0, 0, 0, new HashSet<>());
        }

        private static TargetState boundary(int state, CCHBoundaryArc arc, double turnWeight, long turnMillis) {
            Set<Integer> forbidden = new HashSet<>();
            if (arc.isVirtualEdge())
                forbidden.add(GHUtility.getEdgeFromEdgeKey(arc.getOriginalEdgeKey()));
            double weight = turnWeight == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : arc.getWeight() + turnWeight;
            long millis = turnWeight == Double.POSITIVE_INFINITY ? 0 : saturatedAdd(arc.getMillis(), turnMillis);
            return new TargetState(state, arc, turnWeight, turnMillis, weight, millis, arc.getDistance(), 1, forbidden);
        }
    }

    private static final class Candidate {
        private final StartState start;
        private final TargetState target;
        private final CCHQueryResult coreResult;
        private final CCHBoundaryArc directArc;
        private final double weight;
        private final long millis;
        private final double distance;
        private final int segmentCount;

        private Candidate(StartState start, TargetState target, CCHQueryResult coreResult, CCHBoundaryArc directArc,
                          double weight, long millis, double distance, int segmentCount) {
            this.start = start;
            this.target = target;
            this.coreResult = coreResult;
            this.directArc = directArc;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
            this.segmentCount = segmentCount;
        }

        private static Candidate direct(CCHBoundaryArc directArc) {
            return new Candidate(null, null, null, directArc, directArc.getWeight(), directArc.getMillis(), directArc.getDistance(), 1);
        }

        private static Candidate routed(StartState start, TargetState target, CCHQueryResult coreResult,
                                        double weight, long millis, double distance, int segmentCount) {
            return new Candidate(start, target, coreResult, null, weight, millis, distance, segmentCount);
        }

        private EdgeBasedCCHQueryResult toResult(int sourceNode, int targetNode, int visitedNodes) {
            if (directArc != null) {
                return new EdgeBasedCCHQueryResult(true, sourceNode, targetNode,
                        EdgeStateCCHInputGraph.NO_STATE, EdgeStateCCHInputGraph.NO_STATE, CCHStorage.NO_ARC,
                        0, 0, 0, directArc, null, 0, 0,
                        weight, millis, distance, visitedNodes, null);
            }
            return new EdgeBasedCCHQueryResult(true, sourceNode, targetNode, start.state, target.state,
                    start.firstEdgeKey, start.firstEdgeKey == CCHStorage.NO_ARC ? 0 : start.weight,
                    start.firstEdgeKey == CCHStorage.NO_ARC ? 0 : start.millis,
                    start.firstEdgeKey == CCHStorage.NO_ARC ? 0 : start.distance,
                    start.boundaryArc, target.boundaryArc, target.turnWeight, target.turnMillis,
                    weight, millis, distance, visitedNodes, coreResult);
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
            int bySegments = Integer.compare(segmentCount, other.segmentCount);
            if (bySegments != 0)
                return bySegments < 0;
            int byStart = Integer.compare(state(start), state(other.start));
            if (byStart != 0)
                return byStart < 0;
            int byTarget = Integer.compare(state(target), state(other.target));
            if (byTarget != 0)
                return byTarget < 0;
            int byFirstEdge = Integer.compare(firstEdgeKey(), other.firstEdgeKey());
            if (byFirstEdge != 0)
                return byFirstEdge < 0;
            int bySourceBoundary = Integer.compare(boundaryKey(start == null ? null : start.boundaryArc, directArc),
                    boundaryKey(other.start == null ? null : other.start.boundaryArc, other.directArc));
            if (bySourceBoundary != 0)
                return bySourceBoundary < 0;
            return Integer.compare(boundaryKey(target == null ? null : target.boundaryArc, null),
                    boundaryKey(other.target == null ? null : other.target.boundaryArc, null)) < 0;
        }

        private int firstEdgeKey() {
            return start == null ? NO_EDGE : start.firstEdgeKey;
        }

        private static int state(StartState state) {
            return state == null ? EdgeStateCCHInputGraph.NO_STATE : state.state;
        }

        private static int state(TargetState state) {
            return state == null ? EdgeStateCCHInputGraph.NO_STATE : state.state;
        }

        private static int boundaryKey(CCHBoundaryArc boundaryArc, CCHBoundaryArc directArc) {
            CCHBoundaryArc arc = boundaryArc == null ? directArc : boundaryArc;
            return arc == null ? NO_EDGE : arc.getEdgeKey();
        }
    }
}
