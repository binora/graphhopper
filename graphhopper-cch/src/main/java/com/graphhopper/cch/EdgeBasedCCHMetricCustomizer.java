// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;

import java.util.Objects;

public final class EdgeBasedCCHMetricCustomizer {
    public CCHMetric customize(EdgeStateCCHTopology edgeTopology, CCHMetricSource source) {
        Objects.requireNonNull(edgeTopology, "edgeTopology");
        Objects.requireNonNull(source, "source");
        CCHTopology topology = edgeTopology.getTopology();
        if (topology.getNodes() != source.getNodes())
            throw new IllegalArgumentException("topology and metric source node counts differ: " + topology.getNodes() + " != " + source.getNodes());
        if (source.getTraversalMode() != TraversalMode.EDGE_BASED)
            throw new IllegalArgumentException("edge-state CCH customization requires an edge-based metric source");

        CCHMetric metric = new CCHMetric(topology.getArcs());
        initializeEdgeTransitions(metric, topology, source);
        customizeLowerTriangles(metric, topology);
        return metric;
    }

    public CCHMetric customize(EdgeStateCCHTopology edgeTopology, EdgeBasedCCHMetricSource source) {
        return customize(edgeTopology, (CCHMetricSource) source);
    }

    private static void initializeEdgeTransitions(CCHMetric metric, CCHTopology topology, CCHMetricSource source) {
        for (int i = 0; i < source.getCandidates(); i++) {
            CCHMetricCandidate candidate = Objects.requireNonNull(source.getCandidate(i), "candidate");
            if (candidate.getCCHArc() >= topology.getArcs())
                throw new IllegalArgumentException("candidate CCH arc outside [0," + topology.getArcs() + "): " + candidate.getCCHArc());
            if (!candidate.getProvenance().isEdgeTransition())
                throw new IllegalArgumentException("edge-state CCH customization requires edge-transition provenance");
            if (metric.edgeTransitionCandidateIsBetter(candidate))
                metric.setEdgeTransition(candidate);
        }
    }

    private static void customizeLowerTriangles(CCHMetric metric, CCHTopology topology) {
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        int[] arcsByRank = arcsByIncreasingMinimumEndpointRank(topology);
        for (int targetArc : arcsByRank) {
            enumerator.forEachLowerTriangle(targetArc, triangle -> relax(metric, triangle));
        }
    }

    private static void relax(CCHMetric metric, CCHTriangle triangle) {
        int first = triangle.getFirstWitnessArc();
        int second = triangle.getSecondWitnessArc();
        if (!metric.isFinite(first) || !metric.isFinite(second))
            return;
        double weight = metric.getWeight(first) + metric.getWeight(second);
        if (!Double.isFinite(weight))
            return;
        long millis = saturatedAdd(metric.getMillis(first), metric.getMillis(second));
        double distance = metric.getDistance(first) + metric.getDistance(second);
        if (!Double.isFinite(distance))
            return;
        int target = triangle.getTargetArc();
        if (metric.shortcutCandidateIsBetter(target, weight, first, second))
            metric.setShortcut(target, weight, millis, distance, first, second);
    }

    private static int[] arcsByIncreasingMinimumEndpointRank(CCHTopology topology) {
        int[] counts = new int[topology.getNodes()];
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            counts[minimumEndpointRank(topology, arc)]++;
        }
        int[] first = new int[topology.getNodes() + 1];
        for (int i = 0; i < counts.length; i++) {
            first[i + 1] = first[i] + counts[i];
        }
        int[] next = first.clone();
        int[] arcs = new int[topology.getArcs()];
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            int rank = minimumEndpointRank(topology, arc);
            arcs[next[rank]++] = arc;
        }
        return arcs;
    }

    private static int minimumEndpointRank(CCHTopology topology, int arc) {
        int tailRank = topology.getNodeOrder().getRank(topology.getTail(arc));
        int headRank = topology.getNodeOrder().getRank(topology.getHead(arc));
        return Math.min(tailRank, headRank);
    }

    private static long saturatedAdd(long first, long second) {
        if (first == Long.MAX_VALUE || second == Long.MAX_VALUE)
            return Long.MAX_VALUE;
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }
}
