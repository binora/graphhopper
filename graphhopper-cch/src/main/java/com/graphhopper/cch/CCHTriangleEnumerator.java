// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class CCHTriangleEnumerator {
    private final CCHTopology topology;
    private final int[][] neighborsByNode;
    private final Map<Long, Integer> directedArcIds;

    public CCHTriangleEnumerator(CCHTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
        this.neighborsByNode = buildNeighborsByNode(topology);
        this.directedArcIds = buildDirectedArcIds(topology);
    }

    public void forEachLowerTriangle(int targetArc, CCHTriangleConsumer consumer) {
        forEachTriangle(targetArc, CCHTriangleType.LOWER, consumer);
    }

    public void forEachIntermediateTriangle(int targetArc, CCHTriangleConsumer consumer) {
        forEachTriangle(targetArc, CCHTriangleType.INTERMEDIATE, consumer);
    }

    public void forEachUpperTriangle(int targetArc, CCHTriangleConsumer consumer) {
        forEachTriangle(targetArc, CCHTriangleType.UPPER, consumer);
    }

    public List<CCHTriangle> getLowerTriangles(int targetArc) {
        return getTriangles(targetArc, CCHTriangleType.LOWER);
    }

    public List<CCHTriangle> getIntermediateTriangles(int targetArc) {
        return getTriangles(targetArc, CCHTriangleType.INTERMEDIATE);
    }

    public List<CCHTriangle> getUpperTriangles(int targetArc) {
        return getTriangles(targetArc, CCHTriangleType.UPPER);
    }

    private List<CCHTriangle> getTriangles(int targetArc, CCHTriangleType type) {
        List<CCHTriangle> triangles = new ArrayList<>();
        forEachTriangle(targetArc, type, triangles::add);
        return triangles;
    }

    private void forEachTriangle(int targetArc, CCHTriangleType type, CCHTriangleConsumer consumer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(consumer, "consumer");
        checkCCHArc(targetArc);
        int tail = topology.getTail(targetArc);
        int head = topology.getHead(targetArc);
        int minRank = Math.min(topology.getNodeOrder().getRank(tail), topology.getNodeOrder().getRank(head));
        int maxRank = Math.max(topology.getNodeOrder().getRank(tail), topology.getNodeOrder().getRank(head));
        int[] witnesses = commonNeighbors(tail, head);
        sortByRankThenNode(witnesses);
        for (int witness : witnesses) {
            int witnessRank = topology.getNodeOrder().getRank(witness);
            if (!matches(type, witnessRank, minRank, maxRank))
                continue;
            consumer.accept(new CCHTriangle(type, targetArc, directedArc(tail, witness), directedArc(witness, head)));
        }
    }

    private int directedArc(int tail, int head) {
        Integer arc = directedArcIds.get(directedEdgeKey(tail, head));
        if (arc == null)
            throw new IllegalStateException("missing CCH arc " + tail + "->" + head);
        return arc;
    }

    private int[] commonNeighbors(int a, int b) {
        int[] aNeighbors = neighborsByNode[a];
        int[] bNeighbors = neighborsByNode[b];
        int[] common = new int[Math.min(aNeighbors.length, bNeighbors.length)];
        int commonCount = 0;
        int aIndex = 0;
        int bIndex = 0;
        while (aIndex < aNeighbors.length && bIndex < bNeighbors.length) {
            int aNeighbor = aNeighbors[aIndex];
            int bNeighbor = bNeighbors[bIndex];
            if (aNeighbor == bNeighbor) {
                common[commonCount++] = aNeighbor;
                aIndex++;
                bIndex++;
            } else if (aNeighbor < bNeighbor) {
                aIndex++;
            } else {
                bIndex++;
            }
        }
        return commonCount == common.length ? common : Arrays.copyOf(common, commonCount);
    }

    private void sortByRankThenNode(int[] witnesses) {
        for (int i = 1; i < witnesses.length; i++) {
            int witness = witnesses[i];
            int j = i - 1;
            while (j >= 0 && compareByRankThenNode(witnesses[j], witness) > 0) {
                witnesses[j + 1] = witnesses[j];
                j--;
            }
            witnesses[j + 1] = witness;
        }
    }

    private int compareByRankThenNode(int a, int b) {
        int byRank = Integer.compare(topology.getNodeOrder().getRank(a), topology.getNodeOrder().getRank(b));
        return byRank != 0 ? byRank : Integer.compare(a, b);
    }

    private static boolean matches(CCHTriangleType type, int witnessRank, int minRank, int maxRank) {
        switch (type) {
            case LOWER:
                return witnessRank < minRank;
            case INTERMEDIATE:
                return witnessRank > minRank && witnessRank < maxRank;
            case UPPER:
                return witnessRank > maxRank;
            default:
                throw new IllegalArgumentException("Unsupported triangle type: " + type);
        }
    }

    private void checkCCHArc(int targetArc) {
        if (targetArc < 0 || targetArc >= topology.getArcs())
            throw new IllegalArgumentException("targetArc outside [0," + topology.getArcs() + "): " + targetArc);
    }

    private static int[][] buildNeighborsByNode(CCHTopology topology) {
        @SuppressWarnings("unchecked")
        TreeSet<Integer>[] neighbors = new TreeSet[topology.getNodes()];
        for (int node = 0; node < topology.getNodes(); node++) {
            neighbors[node] = new TreeSet<>();
        }
        for (int upArc = 0; upArc < topology.getUpArcs(); upArc++) {
            int tail = topology.getUpTail(upArc);
            int head = topology.getUpHead(upArc);
            neighbors[tail].add(head);
            neighbors[head].add(tail);
        }
        int[][] result = new int[topology.getNodes()][];
        for (int node = 0; node < topology.getNodes(); node++) {
            result[node] = new int[neighbors[node].size()];
            int index = 0;
            for (int neighbor : neighbors[node]) {
                result[node][index++] = neighbor;
            }
        }
        return result;
    }

    private static Map<Long, Integer> buildDirectedArcIds(CCHTopology topology) {
        Map<Long, Integer> arcIds = new HashMap<>();
        for (int upArc = 0; upArc < topology.getUpArcs(); upArc++) {
            arcIds.put(directedEdgeKey(topology.getUpTail(upArc), topology.getUpHead(upArc)), upArc);
        }
        for (int downArc = 0; downArc < topology.getDownArcs(); downArc++) {
            int cchArc = topology.getDownArcId(downArc);
            arcIds.put(directedEdgeKey(topology.getDownTail(downArc), topology.getDownHead(downArc)), cchArc);
        }
        return arcIds;
    }

    private static long directedEdgeKey(int tail, int head) {
        return ((long) tail << 32) | (head & 0xffffffffL);
    }
}
