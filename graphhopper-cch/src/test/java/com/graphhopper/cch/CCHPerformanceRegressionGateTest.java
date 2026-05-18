// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHPerformanceRegressionGateTest {

    @Test
    void coordinateNestedDissectionGridMetricsStayWithinBroadBounds() {
        CCHInputGraph inputGraph = gridInputGraph(8, 8);
        CCHNodeOrder degreeOrder = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
        CCHNodeOrder coordinateOrder = gridCoordinateOrder(8, 8, 4).build(inputGraph);

        CCHTopologyStatistics degreeStats = CCHOrderDiagnostics.analyze(inputGraph, degreeOrder);
        CCHTopologyStatistics coordinateStats = CCHOrderDiagnostics.analyze(inputGraph, coordinateOrder);
        String diagnostics = diagnostics("node-grid", coordinateStats, "degreeStats=" + degreeStats);

        assertTrue(coordinateStats.getFillArcs() <= degreeStats.getFillArcs(), diagnostics);
        assertTrue(coordinateStats.getTriangles() <= degreeStats.getTriangles(), diagnostics);
        assertTrue(coordinateStats.getArcs() < inputGraph.getNodes() * 160, diagnostics);
        assertTrue(coordinateStats.getTriangles() < inputGraph.getNodes() * 20_000L, diagnostics);
    }

    @Test
    void nodeBasedCustomizationAndQueryStayWithinDeterministicSmokeBounds() {
        CCHInputGraph inputGraph = weightedGridInputGraph(7, 7);
        CCHNodeOrder coordinateOrder = gridCoordinateOrder(7, 7, 4).build(inputGraph);
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, coordinateOrder);
        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(inputGraph, coordinateOrder);
        CCHMetric metric = new CCHMetricCustomizer().customize(topology,
                new NodeBasedCCHMetricSource(topology, inputGraph));
        NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, metric);

        int maxVisitedNodes = 0;
        for (int source = 0; source < inputGraph.getNodes(); source += 8) {
            int target = inputGraph.getNodes() - 1 - source;
            CCHQueryResult result = query.calc(source, target);
            assertTrue(result.isFound(), diagnostics("node-query", stats, "source=" + source + ", target=" + target));
            maxVisitedNodes = Math.max(maxVisitedNodes, result.getVisitedNodes());
        }

        String diagnostics = diagnostics("node-query", stats, "maxVisitedNodes=" + maxVisitedNodes);
        assertTrue(stats.getArcs() < inputGraph.getNodes() * 180, diagnostics);
        assertTrue(stats.getTriangles() < inputGraph.getNodes() * 25_000L, diagnostics);
        assertTrue(maxVisitedNodes < inputGraph.getNodes() * 2, diagnostics);
    }

    @Test
    void liftedEdgeStateOrderMetricsStayWithinBroadBounds() {
        BaseGraph graph = gridGraph(5, 5);
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHNodeOrder baseOrder = new CoordinateNestedDissectionCCHNodeOrderProvider(graph, 3)
                .build(BaseGraphCCHSupportBuilder.fromGraph(graph));
        EdgeStateCCHTopology identityTopology = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));
        EdgeStateCCHTopology liftedTopology = new EdgeStateCCHTopologyBuilder()
                .buildFromBaseNodeOrder(edgeGraph, baseOrder);

        CCHTopologyStatistics identityStats = CCHOrderDiagnostics.analyze(identityTopology);
        CCHTopologyStatistics liftedStats = CCHOrderDiagnostics.analyze(liftedTopology);
        String diagnostics = diagnostics("edge-state-grid", liftedStats, "identityStats=" + identityStats);

        assertNotEquals(identityStats, liftedStats, diagnostics);
        assertTrue(liftedStats.getMaxEliminationTreeDepth() <= identityStats.getMaxEliminationTreeDepth(), diagnostics);
        assertTrue(liftedStats.getArcs() < edgeGraph.getStates() * 80, diagnostics);
        assertTrue(liftedStats.getTriangles() < edgeGraph.getStates() * 30_000L, diagnostics);
    }

    private static CoordinateNestedDissectionCCHNodeOrderProvider gridCoordinateOrder(int rows, int columns, int leafSize) {
        double[] latitudes = new double[rows * columns];
        double[] longitudes = new double[rows * columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                latitudes[node] = row;
                longitudes[node] = column;
            }
        }
        return new CoordinateNestedDissectionCCHNodeOrderProvider(latitudes, longitudes, leafSize);
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

    private static CCHInputGraph weightedGridInputGraph(int rows, int columns) {
        List<CCHInputArc> arcs = new ArrayList<>();
        List<CCHInputEdge> supportEdges = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                if (column > 0)
                    addBidirectionalArc(arcs, supportEdges, node - 1, node, 10 + row);
                if (row > 0)
                    addBidirectionalArc(arcs, supportEdges, node - columns, node, 10 + column);
            }
        }
        return new CCHInputGraph(rows * columns, arcs, supportEdges);
    }

    private static void addBidirectionalArc(List<CCHInputArc> arcs, List<CCHInputEdge> supportEdges,
                                            int from, int to, double weight) {
        int edgeId = supportEdges.size();
        supportEdges.add(edge(from, to));
        arcs.add(new CCHInputArc(from, to, edgeId, false, weight, Math.round(weight * 10), weight));
        arcs.add(new CCHInputArc(to, from, edgeId, true, weight, Math.round(weight * 10), weight));
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

    private static BaseGraph gridGraph(int rows, int columns) {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                graph.getNodeAccess().setNode(node, row, column);
                if (column > 0)
                    graph.edge(node - 1, node).setDistance(10 + row);
                if (row > 0)
                    graph.edge(node - columns, node).setDistance(10 + column);
            }
        }
        return graph;
    }

    private static CCHInputEdge edge(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static String diagnostics(String caseName, CCHTopologyStatistics stats, String extra) {
        return "case=" + caseName
                + "\nstats=" + stats
                + "\n" + extra;
    }
}
