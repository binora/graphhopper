// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class CCHTopologyBuilder {
    public CCHTopology build(CCHInputGraph inputGraph, CCHNodeOrder order) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        Objects.requireNonNull(order, "order");
        if (inputGraph.getNodes() != order.getNodes())
            throw new IllegalArgumentException("input graph and order node counts differ: " + inputGraph.getNodes() + " != " + order.getNodes());

        TreeSet<Integer>[] adjacency = createAdjacency(inputGraph.getNodes());
        Set<Long> supportEdges = new HashSet<>();
        for (CCHInputEdge edge : inputGraph.getAllSupportEdges()) {
            addUndirectedEdge(adjacency, edge.getNodeA(), edge.getNodeB());
            supportEdges.add(edgeKey(edge.getNodeA(), edge.getNodeB()));
        }
        checkInputArcsHaveSupportEdges(inputGraph, supportEdges);

        for (int rank = 0; rank < order.getNodes(); rank++) {
            int node = order.getNodeAtRank(rank);
            List<Integer> higherNeighbors = higherNeighbors(adjacency[node], order, rank);
            for (int i = 0; i < higherNeighbors.size(); i++) {
                for (int j = i + 1; j < higherNeighbors.size(); j++) {
                    addUndirectedEdge(adjacency, higherNeighbors.get(i), higherNeighbors.get(j));
                }
            }
        }

        List<DirectedArc> upArcs = new ArrayList<>();
        List<DirectedArc> downArcs = new ArrayList<>();
        for (int tail = 0; tail < adjacency.length; tail++) {
            for (int head : adjacency[tail]) {
                if (tail < head) {
                    int tailRank = order.getRank(tail);
                    int headRank = order.getRank(head);
                    int low = tailRank < headRank ? tail : head;
                    int high = tailRank < headRank ? head : tail;
                    boolean fill = !supportEdges.contains(edgeKey(tail, head));
                    upArcs.add(new DirectedArc(low, high, fill));
                    downArcs.add(new DirectedArc(high, low, fill));
                }
            }
        }

        upArcs.sort((a, b) -> compareOutgoing(a, b, order));
        downArcs.sort((a, b) -> compareOutgoing(a, b, order));

        Map<Long, Integer> upArcIds = new HashMap<>();
        Map<Long, Integer> downArcIds = new HashMap<>();
        for (int i = 0; i < upArcs.size(); i++) {
            DirectedArc arc = upArcs.get(i);
            upArcIds.put(directedEdgeKey(arc.tail, arc.head), i);
        }
        for (int i = 0; i < downArcs.size(); i++) {
            DirectedArc arc = downArcs.get(i);
            downArcIds.put(directedEdgeKey(arc.tail, arc.head), upArcs.size() + i);
        }

        int[] inputArcToCCHArc = mapInputArcs(inputGraph, order, upArcIds, downArcIds);
        int[] upFirstOut = firstOut(inputGraph.getNodes(), upArcs);
        int[] downFirstOut = firstOut(inputGraph.getNodes(), downArcs);
        int[] upTail = tails(upArcs);
        int[] upHead = heads(upArcs);
        int[] downTail = tails(downArcs);
        int[] downHead = heads(downArcs);
        boolean[] fillArc = fillArcs(upArcs, downArcs);
        int[] skippedArc1 = new int[fillArc.length];
        int[] skippedArc2 = new int[fillArc.length];
        Arrays.fill(skippedArc1, CCHStorage.NO_ARC);
        Arrays.fill(skippedArc2, CCHStorage.NO_ARC);

        return new CCHTopology(order, upFirstOut, upTail, upHead, downFirstOut, downTail, downHead,
                inputArcToCCHArc, fillArc, skippedArc1, skippedArc2);
    }

    private static TreeSet<Integer>[] createAdjacency(int nodes) {
        @SuppressWarnings("unchecked")
        TreeSet<Integer>[] adjacency = new TreeSet[nodes];
        for (int node = 0; node < nodes; node++) {
            adjacency[node] = new TreeSet<>();
        }
        return adjacency;
    }

    private static void addUndirectedEdge(TreeSet<Integer>[] adjacency, int a, int b) {
        if (a == b)
            throw new IllegalArgumentException("topology edges cannot be loops");
        adjacency[a].add(b);
        adjacency[b].add(a);
    }

    private static void checkInputArcsHaveSupportEdges(CCHInputGraph inputGraph, Set<Long> supportEdges) {
        for (int i = 0; i < inputGraph.getArcs(); i++) {
            CCHInputArc arc = inputGraph.getArc(i);
            if (arc.getFrom() == arc.getTo())
                throw new IllegalArgumentException("input arcs cannot be loops");
            if (!supportEdges.contains(edgeKey(arc.getFrom(), arc.getTo())))
                throw new IllegalArgumentException("input arc " + i + " has no support edge");
        }
    }

    private static List<Integer> higherNeighbors(Set<Integer> neighbors, CCHNodeOrder order, int rank) {
        List<Integer> higherNeighbors = new ArrayList<>();
        for (int neighbor : neighbors) {
            if (order.getRank(neighbor) > rank)
                higherNeighbors.add(neighbor);
        }
        higherNeighbors.sort((a, b) -> {
            int byRank = Integer.compare(order.getRank(a), order.getRank(b));
            return byRank != 0 ? byRank : Integer.compare(a, b);
        });
        return higherNeighbors;
    }

    private static int compareOutgoing(DirectedArc a, DirectedArc b, CCHNodeOrder order) {
        int byTail = Integer.compare(a.tail, b.tail);
        if (byTail != 0)
            return byTail;
        int byHeadRank = Integer.compare(order.getRank(a.head), order.getRank(b.head));
        return byHeadRank != 0 ? byHeadRank : Integer.compare(a.head, b.head);
    }

    private static int[] mapInputArcs(CCHInputGraph inputGraph, CCHNodeOrder order,
                                      Map<Long, Integer> upArcIds, Map<Long, Integer> downArcIds) {
        int[] mapping = new int[inputGraph.getArcs()];
        for (int i = 0; i < inputGraph.getArcs(); i++) {
            CCHInputArc inputArc = inputGraph.getArc(i);
            int fromRank = order.getRank(inputArc.getFrom());
            int toRank = order.getRank(inputArc.getTo());
            Map<Long, Integer> arcIds = fromRank < toRank ? upArcIds : downArcIds;
            Integer cchArc = arcIds.get(directedEdgeKey(inputArc.getFrom(), inputArc.getTo()));
            if (cchArc == null)
                throw new IllegalStateException("input arc " + i + " did not map to a CCH arc");
            mapping[i] = cchArc;
        }
        return mapping;
    }

    private static int[] firstOut(int nodes, List<DirectedArc> arcs) {
        int[] firstOut = new int[nodes + 1];
        for (DirectedArc arc : arcs) {
            firstOut[arc.tail + 1]++;
        }
        for (int i = 1; i < firstOut.length; i++) {
            firstOut[i] += firstOut[i - 1];
        }
        return firstOut;
    }

    private static int[] tails(List<DirectedArc> arcs) {
        int[] tails = new int[arcs.size()];
        for (int i = 0; i < arcs.size(); i++) {
            tails[i] = arcs.get(i).tail;
        }
        return tails;
    }

    private static int[] heads(List<DirectedArc> arcs) {
        int[] heads = new int[arcs.size()];
        for (int i = 0; i < arcs.size(); i++) {
            heads[i] = arcs.get(i).head;
        }
        return heads;
    }

    private static boolean[] fillArcs(List<DirectedArc> upArcs, List<DirectedArc> downArcs) {
        boolean[] fill = new boolean[upArcs.size() + downArcs.size()];
        for (int i = 0; i < upArcs.size(); i++) {
            fill[i] = upArcs.get(i).fill;
        }
        for (int i = 0; i < downArcs.size(); i++) {
            fill[upArcs.size() + i] = downArcs.get(i).fill;
        }
        return fill;
    }

    private static long edgeKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private static long directedEdgeKey(int tail, int head) {
        return ((long) tail << 32) | (head & 0xffffffffL);
    }

    private static final class DirectedArc {
        private final int tail;
        private final int head;
        private final boolean fill;

        private DirectedArc(int tail, int head, boolean fill) {
            this.tail = tail;
            this.head = head;
            this.fill = fill;
        }
    }
}
