// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.Path;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EdgeBasedCCHPathUnpacker {
    private static final double WEIGHT_EPSILON = 1.e-9;

    private final EdgeStateCCHTopology edgeTopology;
    private final EdgeStateCCHInputGraph edgeStateInputGraph;
    private final CCHTopology topology;
    private final CCHMetric metric;

    public EdgeBasedCCHPathUnpacker(EdgeStateCCHTopology edgeTopology, CCHMetric metric) {
        this.edgeTopology = Objects.requireNonNull(edgeTopology, "edgeTopology");
        this.edgeStateInputGraph = edgeTopology.getEdgeStateInputGraph();
        this.topology = edgeTopology.getTopology();
        this.metric = Objects.requireNonNull(metric, "metric");
        if (topology.getArcs() != metric.getArcs())
            throw new IllegalArgumentException("topology and metric arc counts differ: " + topology.getArcs() + " != " + metric.getArcs());
    }

    public CCHUnpackedPath unpack(EdgeBasedCCHQueryResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.isFound())
            return new CCHUnpackedPath(false, result.getSourceNode(), result.getTargetNode(), Collections.emptyList(),
                    Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);

        List<CCHUnpackedEdge> edges = new ArrayList<>();
        if (result.getSourceBoundaryArc() != null) {
            edges.add(fromBoundaryArc(result.getSourceBoundaryArc(),
                    result.getSourceBoundaryArc().getWeight(),
                    result.getSourceBoundaryArc().getMillis()));
        }
        if (result.getFirstEdgeKey() != CCHStorage.NO_ARC) {
            int state = result.getSourceState();
            edges.add(new CCHUnpackedEdge(
                    GHUtility.getEdgeFromEdgeKey(result.getFirstEdgeKey()),
                    (result.getFirstEdgeKey() & 1) == 1,
                    edgeStateInputGraph.getStateTailNode(state),
                    edgeStateInputGraph.getStateHeadNode(state),
                    result.getFirstEdgeWeight(),
                    result.getFirstEdgeMillis(),
                    result.getFirstEdgeDistance()));
        }
        if (result.getCoreResult() != null) {
            for (int cchArc : reconstructOverlayArcs(result.getCoreResult())) {
                unpackArc(cchArc, edges);
            }
        }
        if (result.getTargetBoundaryArc() != null) {
            CCHBoundaryArc arc = result.getTargetBoundaryArc();
            edges.add(fromBoundaryArc(arc,
                    arc.getWeight() + result.getTargetBoundaryTurnWeight(),
                    saturatedAdd(arc.getMillis(), result.getTargetBoundaryTurnMillis())));
        }
        validateContiguity(result.getSourceNode(), result.getTargetNode(), edges);
        Totals totals = totals(edges);
        if (Math.abs(totals.weight - result.getWeight()) > WEIGHT_EPSILON)
            throw new IllegalStateException("unpacked path weight differs from query result: " + totals.weight + " != " + result.getWeight());
        return new CCHUnpackedPath(true, result.getSourceNode(), result.getTargetNode(), edges,
                totals.weight, totals.millis, totals.distance);
    }

    public Path toPath(Graph graph, EdgeBasedCCHQueryResult result) {
        Objects.requireNonNull(graph, "graph");
        CCHUnpackedPath unpackedPath = unpack(result);
        Path path = new Path(graph);
        if (!unpackedPath.isFound())
            return path;
        path.setFound(true)
                .setFromNode(unpackedPath.getSource())
                .setEndNode(unpackedPath.getTarget())
                .setWeight(unpackedPath.getWeight())
                .setTime(unpackedPath.getMillis())
                .setDistance(unpackedPath.getDistance());
        for (CCHUnpackedEdge edge : unpackedPath.getEdges()) {
            if (graph.getEdgeIteratorState(edge.getBaseEdge(), edge.getFrom()) == null)
                throw new IllegalStateException("base edge " + edge.getBaseEdge() + " is not adjacent to " + edge.getFrom());
            path.addEdge(edge.getBaseEdge());
        }
        return path;
    }

    private List<Integer> reconstructOverlayArcs(CCHQueryResult result) {
        List<Integer> forwardArcs = new ArrayList<>();
        int[] forwardPredecessorArc = result.getForwardPredecessorArcArray();
        int state = result.getMeetingNode();
        while (state != result.getSource()) {
            int cchArc = forwardPredecessorArc[state];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing forward predecessor arc at state " + state);
            if (topology.getHead(cchArc) != state)
                throw new IllegalStateException("forward predecessor arc does not end at state " + state + ": " + cchArc);
            forwardArcs.add(cchArc);
            state = topology.getTail(cchArc);
        }
        Collections.reverse(forwardArcs);

        List<Integer> resultArcs = new ArrayList<>(forwardArcs);
        int[] backwardPredecessorArc = result.getBackwardPredecessorArcArray();
        state = result.getMeetingNode();
        while (state != result.getTarget()) {
            int cchArc = backwardPredecessorArc[state];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing backward predecessor arc at state " + state);
            if (topology.getTail(cchArc) != state)
                throw new IllegalStateException("backward predecessor arc does not start at state " + state + ": " + cchArc);
            resultArcs.add(cchArc);
            state = topology.getHead(cchArc);
        }
        return resultArcs;
    }

    private void unpackArc(int cchArc, List<CCHUnpackedEdge> edges) {
        CCHMetricProvenance provenance = metric.getProvenance(cchArc);
        if (provenance.isEdgeTransition()) {
            int outState = topology.getHead(cchArc);
            edges.add(new CCHUnpackedEdge(
                    provenance.getOutgoingBaseEdge(),
                    provenance.isOutgoingReverse(),
                    edgeStateInputGraph.getStateTailNode(outState),
                    edgeStateInputGraph.getStateHeadNode(outState),
                    metric.getWeight(cchArc),
                    metric.getMillis(cchArc),
                    metric.getDistance(cchArc)));
        } else if (provenance.isShortcut()) {
            unpackArc(provenance.getFirstSkippedArc(), edges);
            unpackArc(provenance.getSecondSkippedArc(), edges);
        } else {
            throw new IllegalStateException("edge-state CCH arc " + cchArc + " has no unpackable provenance");
        }
    }

    private static CCHUnpackedEdge fromBoundaryArc(CCHBoundaryArc arc, double weight, long millis) {
        return new CCHUnpackedEdge(
                arc.getEdgeId(),
                arc.getEdgeKey(),
                (arc.getEdgeKey() & 1) == 1,
                arc.getTailNode(),
                arc.getHeadNode(),
                weight,
                millis,
                arc.getDistance());
    }

    private static void validateContiguity(int source, int target, List<CCHUnpackedEdge> edges) {
        int node = source;
        for (CCHUnpackedEdge edge : edges) {
            if (edge.getFrom() != node)
                throw new IllegalStateException("unpacked path is not contiguous at edge " + edge.getBaseEdge() + ": expected from " + node + ", got " + edge.getFrom());
            node = edge.getTo();
        }
        if (node != target)
            throw new IllegalStateException("unpacked path ended at " + node + ", expected " + target);
    }

    private static Totals totals(List<CCHUnpackedEdge> edges) {
        double weight = 0;
        long millis = 0;
        double distance = 0;
        for (CCHUnpackedEdge edge : edges) {
            weight += edge.getWeight();
            millis = saturatedAdd(millis, edge.getMillis());
            distance += edge.getDistance();
        }
        return new Totals(weight, millis, distance);
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static final class Totals {
        private final double weight;
        private final long millis;
        private final double distance;

        private Totals(double weight, long millis, double distance) {
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
        }
    }
}
