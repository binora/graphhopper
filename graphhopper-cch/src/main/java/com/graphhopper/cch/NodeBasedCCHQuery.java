// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class NodeBasedCCHQuery {
    private final CCHTopology topology;
    private final CCHMetric metric;
    private final CCHEliminationTree eliminationTree;
    private final int[] upArcToDownArc;
    private final double[] forwardDistance;
    private final double[] backwardDistance;
    private final int[] forwardPredecessorArc;
    private final int[] backwardPredecessorArc;
    private final boolean[] forwardSettled;
    private final boolean[] backwardSettled;

    public NodeBasedCCHQuery(CCHTopology topology, CCHMetric metric) {
        this(topology, metric, new CCHEliminationTree(topology));
    }

    public NodeBasedCCHQuery(CCHTopology topology, CCHMetric metric, CCHEliminationTree eliminationTree) {
        this.topology = Objects.requireNonNull(topology, "topology");
        this.metric = Objects.requireNonNull(metric, "metric");
        this.eliminationTree = Objects.requireNonNull(eliminationTree, "eliminationTree");
        if (metric.getArcs() != topology.getArcs())
            throw new IllegalArgumentException("metric and topology arc counts differ: " + metric.getArcs() + " != " + topology.getArcs());
        if (eliminationTree.getNodes() != topology.getNodes())
            throw new IllegalArgumentException("elimination tree and topology node counts differ: " + eliminationTree.getNodes() + " != " + topology.getNodes());
        upArcToDownArc = buildUpArcToDownArc(topology);
        forwardDistance = new double[topology.getNodes()];
        backwardDistance = new double[topology.getNodes()];
        forwardPredecessorArc = new int[topology.getNodes()];
        backwardPredecessorArc = new int[topology.getNodes()];
        forwardSettled = new boolean[topology.getNodes()];
        backwardSettled = new boolean[topology.getNodes()];
    }

    public CCHQueryResult calc(int source, int target) {
        checkNode("source", source);
        checkNode("target", target);
        reset();

        forwardDistance[source] = 0;
        backwardDistance[target] = 0;

        final int[] forwardVisited = {0};
        eliminationTree.forEachAncestor(source, node -> {
            forwardSettled[node] = true;
            forwardVisited[0]++;
            relaxForward(node);
        });

        final int[] backwardVisited = {0};
        final double[] bestWeight = {Double.POSITIVE_INFINITY};
        final int[] meetingNode = {CCHQueryResult.NO_NODE};
        eliminationTree.forEachAncestor(target, node -> {
            backwardSettled[node] = true;
            backwardVisited[0]++;
            relaxBackward(node);
            if (forwardSettled[node]) {
                double pathWeight = forwardDistance[node] + backwardDistance[node];
                if (pathWeight < bestWeight[0]) {
                    bestWeight[0] = pathWeight;
                    meetingNode[0] = node;
                }
            }
        });

        if (!Double.isFinite(bestWeight[0]))
            meetingNode[0] = CCHQueryResult.NO_NODE;

        return new CCHQueryResult(
                source,
                target,
                meetingNode[0],
                meetingNode[0] == CCHQueryResult.NO_NODE ? Double.POSITIVE_INFINITY : bestWeight[0],
                forwardVisited[0],
                backwardVisited[0],
                forwardDistance,
                backwardDistance,
                forwardPredecessorArc,
                backwardPredecessorArc,
                forwardSettled,
                backwardSettled);
    }

    public CCHEliminationTree getEliminationTree() {
        return eliminationTree;
    }

    private void relaxForward(int node) {
        if (!Double.isFinite(forwardDistance[node]))
            return;
        for (int upArc = topology.getUpArcStart(node); upArc < topology.getUpArcEnd(node); upArc++) {
            double arcWeight = metric.getWeight(upArc);
            if (!Double.isFinite(arcWeight))
                continue;
            int head = topology.getUpHead(upArc);
            double nextWeight = forwardDistance[node] + arcWeight;
            if (nextWeight < forwardDistance[head]) {
                forwardDistance[head] = nextWeight;
                forwardPredecessorArc[head] = upArc;
            }
        }
    }

    private void relaxBackward(int node) {
        if (!Double.isFinite(backwardDistance[node]))
            return;
        for (int upArc = topology.getUpArcStart(node); upArc < topology.getUpArcEnd(node); upArc++) {
            int downArc = upArcToDownArc[upArc];
            double arcWeight = metric.getWeight(downArc);
            if (!Double.isFinite(arcWeight))
                continue;
            int head = topology.getUpHead(upArc);
            double nextWeight = backwardDistance[node] + arcWeight;
            if (nextWeight < backwardDistance[head]) {
                backwardDistance[head] = nextWeight;
                backwardPredecessorArc[head] = downArc;
            }
        }
    }

    private void reset() {
        Arrays.fill(forwardDistance, Double.POSITIVE_INFINITY);
        Arrays.fill(backwardDistance, Double.POSITIVE_INFINITY);
        Arrays.fill(forwardPredecessorArc, CCHStorage.NO_ARC);
        Arrays.fill(backwardPredecessorArc, CCHStorage.NO_ARC);
        Arrays.fill(forwardSettled, false);
        Arrays.fill(backwardSettled, false);
    }

    private void checkNode(String name, int node) {
        if (node < 0 || node >= topology.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + topology.getNodes() + "): " + node);
    }

    private static int[] buildUpArcToDownArc(CCHTopology topology) {
        Map<Long, Integer> downArcIds = new HashMap<>();
        for (int downArc = 0; downArc < topology.getDownArcs(); downArc++) {
            int cchArc = topology.getDownArcId(downArc);
            downArcIds.put(directedEdgeKey(topology.getDownTail(downArc), topology.getDownHead(downArc)), cchArc);
        }
        int[] upArcToDownArc = new int[topology.getUpArcs()];
        for (int upArc = 0; upArc < topology.getUpArcs(); upArc++) {
            int tail = topology.getUpTail(upArc);
            int head = topology.getUpHead(upArc);
            Integer downArc = downArcIds.get(directedEdgeKey(head, tail));
            if (downArc == null)
                throw new IllegalArgumentException("missing downward mirror for upward CCH arc " + tail + "->" + head);
            upArcToDownArc[upArc] = downArc;
        }
        return upArcToDownArc;
    }

    private static long directedEdgeKey(int tail, int head) {
        return ((long) tail << 32) | (head & 0xffffffffL);
    }
}
