// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.routing.TestProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateNestedDissectionCCHNodeOrderProviderTest {
    private static final String PROFILE = "profile";
    private static final String OSM = "src/test/resources/com/graphhopper/cch/cch-virtual-endpoints.osm.xml";

    @TempDir
    Path tempDir;

    @Test
    void buildsDeterministicPermutationFromCoordinates() {
        CCHInputGraph inputGraph = gridInputGraph(4, 4);
        CoordinateNestedDissectionCCHNodeOrderProvider provider = gridProvider(4, 4, 3);

        CCHNodeOrder first = provider.build(inputGraph);
        CCHNodeOrder second = provider.build(inputGraph);

        assertArrayEquals(first.getOrderArray(), second.getOrderArray());
        assertArrayEquals(first.getRankArray(), second.getRankArray());
        assertEquals(inputGraph.getNodes(), first.getNodes());
    }

    @Test
    void gridOrderReducesFillAndTrianglesAgainstDegreeFallback() {
        CCHInputGraph inputGraph = gridInputGraph(8, 8);
        CCHNodeOrder degreeOrder = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
        CCHNodeOrder nestedOrder = gridProvider(8, 8, 4).build(inputGraph);

        CCHTopologyStatistics degreeStats = CCHOrderDiagnostics.analyze(inputGraph, degreeOrder);
        CCHTopologyStatistics nestedStats = CCHOrderDiagnostics.analyze(inputGraph, nestedOrder);

        assertTrue(nestedStats.getFillArcs() <= degreeStats.getFillArcs(),
                "nested=" + nestedStats + ", degree=" + degreeStats);
        assertTrue(nestedStats.getTriangles() <= degreeStats.getTriangles(),
                "nested=" + nestedStats + ", degree=" + degreeStats);
    }

    @Test
    void osmSmokeOrderDoesNotRegressTopologyQualityAgainstDegreeFallback() {
        CCHGraphHopper hopper = new CCHGraphHopper();
        hopper.setGraphHopperLocation(tempDir.resolve("osm-order-cache").toString())
                .setOSMFile(OSM)
                .setEncodedValuesString("car_access, car_average_speed")
                .setProfiles(TestProfiles.accessAndSpeed(PROFILE, "car"));
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();
        try {
            BaseGraph baseGraph = hopper.getBaseGraph();
            CCHInputGraph inputGraph = BaseGraphCCHSupportBuilder.fromGraph(baseGraph);
            CCHNodeOrder degreeOrder = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
            CCHNodeOrder nestedOrder = new CoordinateNestedDissectionCCHNodeOrderProvider(baseGraph, 4).build(inputGraph);

            CCHTopologyStatistics degreeStats = CCHOrderDiagnostics.analyze(inputGraph, degreeOrder);
            CCHTopologyStatistics nestedStats = CCHOrderDiagnostics.analyze(inputGraph, nestedOrder);

            assertTrue(nestedStats.getFillArcs() <= degreeStats.getFillArcs()
                            || nestedStats.getTriangles() <= degreeStats.getTriangles(),
                    "nested=" + nestedStats + ", degree=" + degreeStats);
        } finally {
            hopper.close();
        }
    }

    @Test
    void coordinateOrderPreservesShortestPathCorrectness() {
        CCHInputGraph inputGraph = weightedInputGraph(3,
                arc(0, 1, 0, false, 1),
                arc(1, 0, 0, true, 1),
                arc(1, 2, 1, false, 2),
                arc(2, 1, 1, true, 2),
                arc(0, 2, 2, false, 10),
                arc(2, 0, 2, true, 10));
        CoordinateNestedDissectionCCHNodeOrderProvider provider = new CoordinateNestedDissectionCCHNodeOrderProvider(
                new double[]{0, 1, 2}, new double[]{0, 0.01, 0.02}, 2);
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, provider.build(inputGraph));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, new NodeBasedCCHMetricSource(topology, inputGraph));

        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(0, 2);

        assertTrue(result.isFound());
        assertEquals(3, result.getWeight(), 1.e-9);
    }

    @Test
    void canReadCoordinatesFromBaseGraph() {
        BaseGraph graph = gridGraph(3, 3);
        CCHInputGraph inputGraph = BaseGraphCCHSupportBuilder.fromGraph(graph);

        CCHNodeOrder order = new CoordinateNestedDissectionCCHNodeOrderProvider(graph, 2).build(inputGraph);

        assertEquals(graph.getNodes(), order.getNodes());
    }

    @Test
    void rejectsInvalidInputsClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> new CoordinateNestedDissectionCCHNodeOrderProvider(new double[]{0}, new double[]{0, 1}));
        assertThrows(IllegalArgumentException.class,
                () -> new CoordinateNestedDissectionCCHNodeOrderProvider(new double[]{0}, new double[]{0}, 1));

        CCHInputGraph inputGraph = gridInputGraph(2, 2);
        CoordinateNestedDissectionCCHNodeOrderProvider provider =
                new CoordinateNestedDissectionCCHNodeOrderProvider(new double[]{0, 1}, new double[]{0, 1}, 2);
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class, () -> provider.build(inputGraph));
        assertTrue(mismatch.getMessage().contains("coordinate count"), mismatch.getMessage());
    }

    private static CoordinateNestedDissectionCCHNodeOrderProvider gridProvider(int rows, int columns, int leafSize) {
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

    private static BaseGraph gridGraph(int rows, int columns) {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                graph.getNodeAccess().setNode(node, row, column);
                if (column > 0)
                    graph.edge(node - 1, node).setDistance(10);
                if (row > 0)
                    graph.edge(node - columns, node).setDistance(10);
            }
        }
        return graph;
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
            arcs.add(arc(edge.getNodeA(), edge.getNodeB(), edgeId, false, 1));
            arcs.add(arc(edge.getNodeB(), edge.getNodeA(), edgeId, true, 1));
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputGraph weightedInputGraph(int nodes, CCHInputArc... arcs) {
        List<CCHInputEdge> supportEdges = new ArrayList<>();
        for (CCHInputArc arc : arcs) {
            CCHInputEdge supportEdge = edge(arc.getFrom(), arc.getTo());
            if (!supportEdges.contains(supportEdge))
                supportEdges.add(supportEdge);
        }
        return new CCHInputGraph(nodes, Arrays.asList(arcs), supportEdges);
    }

    private static CCHInputArc arc(int from, int to, int edge, boolean reverse, double weight) {
        return new CCHInputArc(from, to, edge, reverse, weight, Math.round(weight * 10), weight);
    }

    private static CCHInputEdge edge(int a, int b) {
        return new CCHInputEdge(a, b);
    }
}
