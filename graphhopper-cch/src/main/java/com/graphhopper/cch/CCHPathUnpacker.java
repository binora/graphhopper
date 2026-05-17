// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.Path;
import com.graphhopper.storage.Graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CCHPathUnpacker {
    private static final double WEIGHT_EPSILON = 1.e-9;

    private final CCHTopology topology;
    private final CCHMetric metric;

    public CCHPathUnpacker(CCHTopology topology, CCHMetric metric) {
        this.topology = Objects.requireNonNull(topology, "topology");
        this.metric = Objects.requireNonNull(metric, "metric");
        if (topology.getArcs() != metric.getArcs())
            throw new IllegalArgumentException("topology and metric arc counts differ: " + topology.getArcs() + " != " + metric.getArcs());
    }

    public CCHUnpackedPath unpack(CCHQueryResult result) {
        Objects.requireNonNull(result, "result");
        checkNode("source", result.getSource());
        checkNode("target", result.getTarget());
        if (!result.isFound())
            return new CCHUnpackedPath(false, result.getSource(), result.getTarget(), Collections.emptyList(),
                    Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);

        List<Integer> overlayArcs = reconstructOverlayArcs(result);
        List<CCHUnpackedEdge> edges = new ArrayList<>();
        for (int cchArc : overlayArcs) {
            unpackArc(cchArc, edges);
        }
        validateContiguity(result.getSource(), result.getTarget(), edges);
        Totals totals = totals(edges);
        if (Math.abs(totals.weight - result.getWeight()) > WEIGHT_EPSILON)
            throw new IllegalStateException("unpacked path weight differs from query result: " + totals.weight + " != " + result.getWeight());
        return new CCHUnpackedPath(true, result.getSource(), result.getTarget(), edges,
                totals.weight, totals.millis, totals.distance);
    }

    public Path toPath(Graph graph, CCHQueryResult result) {
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
        int node = result.getMeetingNode();
        while (node != result.getSource()) {
            int cchArc = forwardPredecessorArc[node];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing forward predecessor arc at node " + node);
            if (topology.getHead(cchArc) != node)
                throw new IllegalStateException("forward predecessor arc does not end at node " + node + ": " + cchArc);
            forwardArcs.add(cchArc);
            node = topology.getTail(cchArc);
        }
        Collections.reverse(forwardArcs);

        List<Integer> resultArcs = new ArrayList<>(forwardArcs);
        int[] backwardPredecessorArc = result.getBackwardPredecessorArcArray();
        node = result.getMeetingNode();
        while (node != result.getTarget()) {
            int cchArc = backwardPredecessorArc[node];
            if (cchArc == CCHStorage.NO_ARC)
                throw new IllegalStateException("missing backward predecessor arc at node " + node);
            if (topology.getTail(cchArc) != node)
                throw new IllegalStateException("backward predecessor arc does not start at node " + node + ": " + cchArc);
            resultArcs.add(cchArc);
            node = topology.getHead(cchArc);
        }
        return resultArcs;
    }

    private void unpackArc(int cchArc, List<CCHUnpackedEdge> edges) {
        CCHMetricProvenance provenance = metric.getProvenance(cchArc);
        if (provenance.isDirect()) {
            edges.add(new CCHUnpackedEdge(
                    provenance.getBaseEdge(),
                    provenance.isReverse(),
                    topology.getTail(cchArc),
                    topology.getHead(cchArc),
                    metric.getWeight(cchArc),
                    metric.getMillis(cchArc),
                    metric.getDistance(cchArc)));
        } else if (provenance.isShortcut()) {
            unpackArc(provenance.getFirstSkippedArc(), edges);
            unpackArc(provenance.getSecondSkippedArc(), edges);
        } else {
            throw new IllegalStateException("CCH arc " + cchArc + " has no unpackable provenance");
        }
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

    private void checkNode(String name, int node) {
        if (node < 0 || node >= topology.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + topology.getNodes() + "): " + node);
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
