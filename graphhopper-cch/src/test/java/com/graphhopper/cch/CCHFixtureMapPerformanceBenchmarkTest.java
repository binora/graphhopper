// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.FlexiblePathCalculator;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

class CCHFixtureMapPerformanceBenchmarkTest {
    private static final String PROFILE = "profile";
    private static final int ROWS = 9;
    private static final int COLUMNS = 9;

    @TempDir
    Path tempDir;

    @Test
    void comparesOrdersOnImportedFixtureMapForFullTrafficRecustomization() throws IOException {
        Path osmFile = tempDir.resolve("cch-fixture-map.osm.xml");
        writeGridOsm(osmFile, ROWS, COLUMNS);

        CCHGraphHopper hopper = new CCHGraphHopper();
        hopper.setGraphHopperLocation(tempDir.resolve("fixture-cache").toString())
                .setOSMFile(osmFile.toString())
                .setEncodedValuesString("car_access, car_average_speed")
                .setProfiles(TestProfiles.accessAndSpeed(PROFILE, "car"))
                .setStoreOnFlush(false);
        hopper.setMinNetworkSize(0);
        hopper.setCCHProfiles(new CCHProfile(PROFILE));
        hopper.importOrLoad();
        try {
            BaseGraph baseGraph = hopper.getBaseGraph();
            CCHInputGraph supportGraph = BaseGraphCCHSupportBuilder.fromGraph(baseGraph);
            Weighting weighting = hopper.getCCHGraphs().get(PROFILE).getWeighting();
            int[][] pairs = towerNodePairs(baseGraph);

            CCHNodeOrder degreeOrder = new DeterministicCCHNodeOrderBuilder().build(supportGraph);
            CCHNodeOrder coordinateOrder = new CoordinateNestedDissectionCCHNodeOrderProvider(baseGraph, 6)
                    .build(supportGraph);
            Path importedOrderFile = tempDir.resolve("fixture-coordinate-nd.order");
            writeOrder(importedOrderFile, coordinateOrder);
            CCHNodeOrder importedCoordinateOrder = new FileCCHNodeOrderProvider(importedOrderFile)
                    .build(supportGraph);

            BenchmarkResult degree = benchmark("degree", baseGraph, supportGraph, weighting, trafficSnapshot(baseGraph),
                    pairs, degreeOrder);
            BenchmarkResult coordinate = benchmark("coordinate-nd", baseGraph, supportGraph, weighting,
                    trafficSnapshot(baseGraph), pairs, coordinateOrder);
            BenchmarkResult imported = benchmark("imported-coordinate-nd", baseGraph, supportGraph, weighting,
                    trafficSnapshot(baseGraph), pairs, importedCoordinateOrder);

            assertEquals(coordinate.stats, imported.stats, imported.diagnostics());
            assertTrue(coordinate.stats.getFillArcs() <= degree.stats.getFillArcs(),
                    allDiagnostics(degree, coordinate, imported));
            assertTrue(coordinate.stats.getTriangles() <= degree.stats.getTriangles(),
                    allDiagnostics(degree, coordinate, imported));
            assertTrue(coordinate.trafficCustomizationMillis <= Math.max(50, degree.trafficCustomizationMillis * 4 + 10),
                    allDiagnostics(degree, coordinate, imported));
            assertTrue(coordinate.maxVisitedNodes <= baseGraph.getNodes() * 2, coordinate.diagnostics());
            assertTrue(imported.maxVisitedNodes <= baseGraph.getNodes() * 2, imported.diagnostics());
        } finally {
            hopper.close();
        }
    }

    private static BenchmarkResult benchmark(String name, BaseGraph baseGraph, CCHInputGraph supportGraph,
                                             Weighting weighting, CCHTrafficSnapshot trafficSnapshot, int[][] pairs,
                                             CCHNodeOrder order) {
        long topologyStart = System.nanoTime();
        CCHTopology topology = new CCHTopologyBuilder().build(supportGraph, order);
        long topologyNanos = System.nanoTime() - topologyStart;

        long statsStart = System.nanoTime();
        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(supportGraph, topology);
        long statsNanos = System.nanoTime() - statsStart;

        CCHMetricCustomizer customizer = new CCHMetricCustomizer();
        long customizationStart = System.nanoTime();
        CCHMetric baseMetric = customizer.customize(topology,
                new BaseGraphCCHMetricSource(baseGraph, weighting, topology));
        long customizationNanos = System.nanoTime() - customizationStart;

        Weighting trafficWeighting = new CCHTrafficWeighting(weighting, trafficSnapshot);
        long trafficCustomizationStart = System.nanoTime();
        CCHMetric trafficMetric = customizer.customize(topology,
                new BaseGraphCCHMetricSource(baseGraph, trafficWeighting, topology));
        long trafficCustomizationNanos = System.nanoTime() - trafficCustomizationStart;

        QueryGraph queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
        NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, trafficMetric);
        CCHPathCalculator calculator = new CCHPathCalculator(
                new DefaultRoutingCCHGraph(baseGraph, topology, trafficMetric, trafficWeighting), queryGraph);
        FlexiblePathCalculator flexible = new FlexiblePathCalculator(queryGraph, new RoutingAlgorithmFactorySimple(),
                trafficWeighting, new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI)
                .setTraversalMode(TraversalMode.NODE_BASED));

        long queryStart = System.nanoTime();
        int maxVisitedNodes = 0;
        int foundQueries = 0;
        for (int[] pair : pairs) {
            CCHQueryResult result = query.calc(pair[0], pair[1]);
            if (result.isFound())
                foundQueries++;
            maxVisitedNodes = Math.max(maxVisitedNodes, result.getVisitedNodes());
        }
        long queryNanos = System.nanoTime() - queryStart;

        long pathStart = System.nanoTime();
        int pathMatches = 0;
        for (int[] pair : pairs) {
            com.graphhopper.routing.Path cchPath = calculator.calcPaths(pair[0], pair[1], new EdgeRestrictions()).get(0);
            com.graphhopper.routing.Path flexiblePath = flexible.calcPaths(pair[0], pair[1], new EdgeRestrictions()).get(0);
            assertEquals(flexiblePath.isFound(), cchPath.isFound(),
                    diagnostics(name, stats, pair, flexiblePath, cchPath));
            if (flexiblePath.isFound()) {
                assertEquals(flexiblePath.getWeight(), cchPath.getWeight(), 1.e-6,
                        diagnostics(name, stats, pair, flexiblePath, cchPath));
                pathMatches++;
            }
        }
        long pathNanos = System.nanoTime() - pathStart;

        assertEquals(topology.getArcs(), baseMetric.getArcs());
        assertEquals(topology.getArcs(), trafficMetric.getArcs());
        BenchmarkResult result = new BenchmarkResult(name, stats, millis(topologyNanos), millis(statsNanos),
                millis(customizationNanos), millis(trafficCustomizationNanos), micros(queryNanos),
                micros(pathNanos), maxVisitedNodes, foundQueries, pathMatches);
        assertEquals(pairs.length, foundQueries, result.diagnostics());
        assertEquals(pairs.length, pathMatches, result.diagnostics());
        assertTrue(stats.getArcs() < baseGraph.getNodes() * 220, result.diagnostics());
        assertTrue(stats.getTriangles() < baseGraph.getNodes() * 35_000L, result.diagnostics());
        return result;
    }

    private static CCHTrafficSnapshot trafficSnapshot(BaseGraph baseGraph) {
        CCHTrafficSnapshot.Builder builder = CCHTrafficSnapshot.builder("fixture-map-traffic")
                .setCreatedMillis(1);
        for (int edge = 0; edge < baseGraph.getEdges(); edge++) {
            if (edge % 11 == 0)
                builder.delay(edge, false, 45_000);
            if (edge % 13 == 0)
                builder.speed(edge, true, 12);
            if (edge % 17 == 0)
                builder.delay(edge, true, 30_000);
        }
        return builder.build();
    }

    private static int[][] towerNodePairs(BaseGraph graph) {
        return new int[][]{
                {nearestNode(graph, 0.0, 0.0), nearestNode(graph, 0.008, 0.008)},
                {nearestNode(graph, 0.0, 0.008), nearestNode(graph, 0.008, 0.0)},
                {nearestNode(graph, 0.002, 0.001), nearestNode(graph, 0.007, 0.006)},
                {nearestNode(graph, 0.004, 0.000), nearestNode(graph, 0.004, 0.008)},
                {nearestNode(graph, 0.001, 0.006), nearestNode(graph, 0.008, 0.002)}
        };
    }

    private static int nearestNode(BaseGraph graph, double lat, double lon) {
        NodeAccess nodeAccess = graph.getNodeAccess();
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestNode = -1;
        for (int node = 0; node < graph.getNodes(); node++) {
            double deltaLat = nodeAccess.getLat(node) - lat;
            double deltaLon = nodeAccess.getLon(node) - lon;
            double distance = deltaLat * deltaLat + deltaLon * deltaLon;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNode = node;
            }
        }
        assertTrue(bestNode >= 0, "no tower node found in imported fixture graph");
        return bestNode;
    }

    private static void writeGridOsm(Path osmFile, int rows, int columns) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='UTF-8'?>\n");
        xml.append("<osm version='0.6' generator='graphhopper-cch-test'>\n");
        xml.append("  <bounds minlat='0.0' minlon='0.0' maxlat='0.008' maxlon='0.008'/>\n");
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                xml.append("  <node id='").append(nodeRef(row, column, columns)).append("' lat='")
                        .append(formatCoord(row)).append("' lon='").append(formatCoord(column)).append("'/>\n");
            }
        }
        long wayId = 10_000;
        for (int row = 0; row < rows; row++) {
            xml.append("  <way id='").append(wayId++).append("'>\n");
            for (int column = 0; column < columns; column++)
                xml.append("    <nd ref='").append(nodeRef(row, column, columns)).append("'/>\n");
            xml.append("    <tag k='highway' v='residential'/>\n");
            xml.append("    <tag k='name' v='row-").append(row).append("'/>\n");
            xml.append("  </way>\n");
        }
        for (int column = 0; column < columns; column++) {
            xml.append("  <way id='").append(wayId++).append("'>\n");
            for (int row = 0; row < rows; row++)
                xml.append("    <nd ref='").append(nodeRef(row, column, columns)).append("'/>\n");
            xml.append("    <tag k='highway' v='residential'/>\n");
            xml.append("    <tag k='name' v='column-").append(column).append("'/>\n");
            xml.append("  </way>\n");
        }
        for (int diagonal = 0; diagonal < 3; diagonal++) {
            xml.append("  <way id='").append(wayId++).append("'>\n");
            for (int offset = 0; offset < rows - 1; offset++) {
                int row = offset;
                int column = Math.min(columns - 1, offset + diagonal * 2);
                xml.append("    <nd ref='").append(nodeRef(row, column, columns)).append("'/>\n");
            }
            xml.append("    <tag k='highway' v='secondary'/>\n");
            xml.append("    <tag k='maxspeed' v='35'/>\n");
            xml.append("    <tag k='name' v='diagonal-").append(diagonal).append("'/>\n");
            xml.append("  </way>\n");
        }
        xml.append("</osm>\n");
        Files.write(osmFile, xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeOrder(Path orderFile, CCHNodeOrder order) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int node : order.getOrderArray())
            builder.append(node).append('\n');
        Files.write(orderFile, builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static long nodeRef(int row, int column, int columns) {
        return 1_000_000L + row * columns + column;
    }

    private static String formatCoord(int value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value / 1000d);
    }

    private static long millis(long nanos) {
        return Math.max(0, nanos / 1_000_000);
    }

    private static long micros(long nanos) {
        return Math.max(0, nanos / 1_000);
    }

    private static String allDiagnostics(BenchmarkResult... results) {
        StringBuilder builder = new StringBuilder();
        Arrays.stream(results).forEach(result -> builder.append(result.diagnostics()).append('\n'));
        return builder.toString();
    }

    private static String diagnostics(String order, CCHTopologyStatistics stats, int[] pair,
                                      com.graphhopper.routing.Path flexiblePath, com.graphhopper.routing.Path cchPath) {
        return "order=" + order
                + "\nstats=" + stats
                + "\npair=" + Arrays.toString(pair)
                + "\nflexible=" + pathDiagnostics(flexiblePath)
                + "\ncch=" + pathDiagnostics(cchPath);
    }

    private static String pathDiagnostics(com.graphhopper.routing.Path path) {
        if (!path.isFound())
            return "{found=false}";
        return "{found=true, weight=" + path.getWeight()
                + ", time=" + path.getTime()
                + ", distance=" + path.getDistance()
                + ", edges=" + path.getEdges()
                + ", nodes=" + path.calcNodes()
                + '}';
    }

    private static final class BenchmarkResult {
        private final String order;
        private final CCHTopologyStatistics stats;
        private final long topologyBuildMillis;
        private final long statsMillis;
        private final long customizationMillis;
        private final long trafficCustomizationMillis;
        private final long queryMicros;
        private final long pathMicros;
        private final int maxVisitedNodes;
        private final int foundQueries;
        private final int pathMatches;

        private BenchmarkResult(String order, CCHTopologyStatistics stats, long topologyBuildMillis, long statsMillis,
                                long customizationMillis, long trafficCustomizationMillis, long queryMicros,
                                long pathMicros, int maxVisitedNodes, int foundQueries, int pathMatches) {
            this.order = order;
            this.stats = stats;
            this.topologyBuildMillis = topologyBuildMillis;
            this.statsMillis = statsMillis;
            this.customizationMillis = customizationMillis;
            this.trafficCustomizationMillis = trafficCustomizationMillis;
            this.queryMicros = queryMicros;
            this.pathMicros = pathMicros;
            this.maxVisitedNodes = maxVisitedNodes;
            this.foundQueries = foundQueries;
            this.pathMatches = pathMatches;
        }

        private String diagnostics() {
            return "order=" + order
                    + "\nstats=" + stats
                    + "\ntopologyBuildMillis=" + topologyBuildMillis
                    + ", statsMillis=" + statsMillis
                    + ", customizationMillis=" + customizationMillis
                    + ", trafficCustomizationMillis=" + trafficCustomizationMillis
                    + ", queryMicros=" + queryMicros
                    + ", pathMicros=" + pathMicros
                    + ", maxVisitedNodes=" + maxVisitedNodes
                    + ", foundQueries=" + foundQueries
                    + ", pathMatches=" + pathMatches;
        }
    }
}
