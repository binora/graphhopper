// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHOrderDiagnostics {
    private CCHOrderDiagnostics() {
    }

    public static CCHTopologyStatistics analyze(CCHInputGraph inputGraph, CCHNodeOrder order) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        Objects.requireNonNull(order, "order");
        if (inputGraph.getNodes() != order.getNodes())
            throw new IllegalArgumentException("input graph and order node counts differ: "
                    + inputGraph.getNodes() + " != " + order.getNodes());
        return analyze(inputGraph, new CCHTopologyBuilder().build(inputGraph, order));
    }

    public static CCHTopologyStatistics analyze(CCHInputGraph inputGraph, CCHTopology topology) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        Objects.requireNonNull(topology, "topology");
        if (inputGraph.getNodes() != topology.getNodes())
            throw new IllegalArgumentException("input graph and topology node counts differ: "
                    + inputGraph.getNodes() + " != " + topology.getNodes());

        int fillArcs = countFillArcs(topology);
        long[] triangles = countTriangles(topology);
        int[] eliminationTreeShape = eliminationTreeShape(topology);
        return new CCHTopologyStatistics(
                topology.getNodes(),
                inputGraph.getArcs(),
                inputGraph.getSupportEdges(),
                topology.getUpArcs(),
                topology.getDownArcs(),
                fillArcs,
                triangles[0],
                triangles[1],
                triangles[2],
                eliminationTreeShape[0],
                eliminationTreeShape[1],
                estimateTopologyBytes(topology));
    }

    public static CCHTopologyStatistics analyze(EdgeStateCCHTopology edgeTopology) {
        Objects.requireNonNull(edgeTopology, "edgeTopology");
        return analyze(edgeTopology.getEdgeStateInputGraph().getInputGraph(), edgeTopology.getTopology());
    }

    private static int countFillArcs(CCHTopology topology) {
        int fillArcs = 0;
        for (boolean fillArc : topology.getFillArcArray()) {
            if (fillArc)
                fillArcs++;
        }
        return fillArcs;
    }

    private static long[] countTriangles(CCHTopology topology) {
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        long[] counts = new long[3];
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            enumerator.forEachLowerTriangle(arc, triangle -> counts[0]++);
            enumerator.forEachIntermediateTriangle(arc, triangle -> counts[1]++);
            enumerator.forEachUpperTriangle(arc, triangle -> counts[2]++);
        }
        return counts;
    }

    private static int[] eliminationTreeShape(CCHTopology topology) {
        CCHEliminationTree tree = new CCHEliminationTree(topology);
        int roots = 0;
        int maxDepth = 0;
        for (int node = 0; node < tree.getNodes(); node++) {
            int depth = 0;
            int current = node;
            while (current != CCHEliminationTree.NO_NODE) {
                depth++;
                current = tree.getParent(current);
            }
            if (tree.getParent(node) == CCHEliminationTree.NO_NODE)
                roots++;
            maxDepth = Math.max(maxDepth, depth);
        }
        return new int[]{roots, maxDepth};
    }

    private static long estimateTopologyBytes(CCHTopology topology) {
        long ints = 0;
        ints += topology.getNodeOrder().getOrderArray().length;
        ints += topology.getNodeOrder().getRankArray().length;
        ints += topology.getUpFirstOutArray().length;
        ints += topology.getUpTailArray().length;
        ints += topology.getUpHeadArray().length;
        ints += topology.getDownFirstOutArray().length;
        ints += topology.getDownTailArray().length;
        ints += topology.getDownHeadArray().length;
        ints += topology.getInputArcCCHArcArray().length;
        ints += topology.getSkippedArc1Array().length;
        ints += topology.getSkippedArc2Array().length;
        return ints * Integer.BYTES + topology.getFillArcArray().length;
    }
}
