// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHOrderDiagnosticsTest {

    @Test
    void pathOrderWithoutFillHasSmallDiagnostics() {
        CCHInputGraph inputGraph = undirectedInputGraph(3, edge(0, 1), edge(1, 2));

        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(inputGraph, CCHNodeOrder.identity(3));

        assertEquals(3, stats.getNodes());
        assertEquals(4, stats.getInputArcs());
        assertEquals(2, stats.getSupportEdges());
        assertEquals(4, stats.getArcs());
        assertEquals(4, stats.getDirectArcs());
        assertEquals(0, stats.getFillArcs());
        assertEquals(0, stats.getTriangles());
        assertEquals(1, stats.getEliminationTreeRoots());
        assertEquals(3, stats.getMaxEliminationTreeDepth());
        assertTrue(stats.getEstimatedTopologyBytes() > 0);
    }

    @Test
    void pathOrderWithSeparatorFirstReportsFillAndTriangles() {
        CCHInputGraph inputGraph = undirectedInputGraph(3, edge(0, 1), edge(1, 2));

        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        assertEquals(6, stats.getArcs());
        assertEquals(4, stats.getDirectArcs());
        assertEquals(2, stats.getFillArcs());
        assertEquals(2, stats.getLowerTriangles());
        assertEquals(2, stats.getIntermediateTriangles());
        assertEquals(2, stats.getUpperTriangles());
        assertEquals(6, stats.getTriangles());
        assertTrue(stats.toString().contains("fillArcs=2"), stats.toString());
    }

    @Test
    void diagnosticsAreDeterministicForRepeatedAnalysis() {
        CCHInputGraph inputGraph = gridInputGraph(4, 4);
        CCHNodeOrder order = new DeterministicCCHNodeOrderBuilder().build(inputGraph);

        CCHTopologyStatistics first = CCHOrderDiagnostics.analyze(inputGraph, order);
        CCHTopologyStatistics second = CCHOrderDiagnostics.analyze(inputGraph, order);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.getArcs() >= inputGraph.getSupportEdges() * 2);
        assertTrue(first.getTriangles() > 0);
    }

    @Test
    void disconnectedComponentsReportMultipleEliminationTreeRoots() {
        CCHInputGraph inputGraph = undirectedInputGraph(5, edge(0, 1), edge(2, 3));

        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(inputGraph, CCHNodeOrder.identity(5));

        assertEquals(3, stats.getEliminationTreeRoots());
        assertEquals(2, stats.getMaxEliminationTreeDepth());
    }

    @Test
    void analyzesEdgeStateTopology() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.getNodeAccess().setNode(0, 0, 0);
        graph.getNodeAccess().setNode(1, 0, 1);
        graph.getNodeAccess().setNode(2, 1, 1);
        graph.edge(0, 1).setDistance(10);
        graph.edge(1, 2).setDistance(10);

        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));

        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(edgeTopology);

        assertEquals(edgeGraph.getStates(), stats.getNodes());
        assertEquals(edgeGraph.getInputGraph().getArcs(), stats.getInputArcs());
        assertEquals(edgeGraph.getInputGraph().getSupportEdges(), stats.getSupportEdges());
        assertEquals(edgeTopology.getArcs(), stats.getArcs());
    }

    @Test
    void rejectsMismatchedOrderOrTopology() {
        CCHInputGraph inputGraph = undirectedInputGraph(2, edge(0, 1));
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, CCHNodeOrder.identity(2));

        assertThrows(IllegalArgumentException.class,
                () -> CCHOrderDiagnostics.analyze(inputGraph, CCHNodeOrder.identity(3)));
        assertThrows(IllegalArgumentException.class,
                () -> CCHOrderDiagnostics.analyze(undirectedInputGraph(3, edge(0, 1)), topology));
    }

    private static CCHInputGraph gridInputGraph(int rows, int columns) {
        List<CCHInputEdge> supportEdges = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                if (column > 0)
                    supportEdges.add(edge(node - 1, node));
                if (row > 0)
                    supportEdges.add(edge(node - columns, node));
            }
        }
        return undirectedInputGraph(rows * columns, supportEdges.toArray(new CCHInputEdge[0]));
    }

    private static CCHInputGraph undirectedInputGraph(int nodes, CCHInputEdge... supportEdges) {
        List<CCHInputArc> arcs = new ArrayList<>();
        for (int edgeId = 0; edgeId < supportEdges.length; edgeId++) {
            CCHInputEdge edge = supportEdges[edgeId];
            arcs.add(new CCHInputArc(edge.getNodeA(), edge.getNodeB(), edgeId, false, 1, 1, 1));
            arcs.add(new CCHInputArc(edge.getNodeB(), edge.getNodeA(), edgeId, true, 1, 1, 1));
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputEdge edge(int a, int b) {
        return new CCHInputEdge(a, b);
    }
}
