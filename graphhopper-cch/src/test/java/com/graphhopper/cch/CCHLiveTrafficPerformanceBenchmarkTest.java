// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHLiveTrafficPerformanceBenchmarkTest {
    private static final int ROWS = 12;
    private static final int COLUMNS = 12;
    private static final int NODES = ROWS * COLUMNS;

    @TempDir
    Path tempDir;

    @Test
    void comparesOrdersForFullTrafficMetricRecustomization() throws IOException {
        CCHInputGraph inputGraph = weightedGridInputGraph(ROWS, COLUMNS);
        CCHNodeOrder degreeOrder = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
        CCHNodeOrder coordinateOrder = gridCoordinateOrder(ROWS, COLUMNS, 6).build(inputGraph);
        Path orderFile = tempDir.resolve("coordinate-nd.order");
        writeOrder(orderFile, coordinateOrder);
        CCHNodeOrder importedCoordinateOrder = new FileCCHNodeOrderProvider(orderFile).build(inputGraph);

        BenchmarkResult degree = benchmark("degree", inputGraph, degreeOrder);
        BenchmarkResult coordinate = benchmark("coordinate-nd", inputGraph, coordinateOrder);
        BenchmarkResult imported = benchmark("imported-coordinate-nd", inputGraph, importedCoordinateOrder);

        assertEquals(coordinate.stats, imported.stats, imported.diagnostics());
        assertTrue(coordinate.stats.getFillArcs() <= degree.stats.getFillArcs(),
                allDiagnostics(degree, coordinate, imported));
        assertTrue(coordinate.stats.getTriangles() <= degree.stats.getTriangles(),
                allDiagnostics(degree, coordinate, imported));
        assertTrue(coordinate.trafficCustomizationMillis <= Math.max(50, degree.trafficCustomizationMillis * 4 + 10),
                allDiagnostics(degree, coordinate, imported));
        assertTrue(coordinate.maxVisitedNodes <= NODES * 2, coordinate.diagnostics());
        assertTrue(imported.maxVisitedNodes <= NODES * 2, imported.diagnostics());
    }

    private static BenchmarkResult benchmark(String name, CCHInputGraph inputGraph, CCHNodeOrder order) {
        long topologyStart = System.nanoTime();
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, order);
        long topologyNanos = System.nanoTime() - topologyStart;

        long statsStart = System.nanoTime();
        CCHTopologyStatistics stats = CCHOrderDiagnostics.analyze(inputGraph, topology);
        long statsNanos = System.nanoTime() - statsStart;

        CCHMetricCustomizer customizer = new CCHMetricCustomizer();
        long customizationStart = System.nanoTime();
        CCHMetric baseMetric = customizer.customize(topology, new NodeBasedCCHMetricSource(topology, inputGraph));
        long customizationNanos = System.nanoTime() - customizationStart;

        long trafficCustomizationStart = System.nanoTime();
        CCHMetric trafficMetric = customizer.customize(topology, new TrafficGridMetricSource(topology, inputGraph));
        long trafficCustomizationNanos = System.nanoTime() - trafficCustomizationStart;

        NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, trafficMetric);
        int[][] pairs = {
                {0, inputGraph.getNodes() - 1},
                {COLUMNS - 1, inputGraph.getNodes() - COLUMNS},
                {COLUMNS * 2, inputGraph.getNodes() - COLUMNS * 3 - 1},
                {COLUMNS * 5 + 1, COLUMNS * 6 + 10},
                {inputGraph.getNodes() / 3, inputGraph.getNodes() * 2 / 3}
        };
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

        assertEquals(topology.getArcs(), baseMetric.getArcs());
        assertEquals(topology.getArcs(), trafficMetric.getArcs());
        BenchmarkResult result = new BenchmarkResult(name, stats, millis(topologyNanos), millis(statsNanos),
                millis(customizationNanos), millis(trafficCustomizationNanos), micros(queryNanos),
                maxVisitedNodes, foundQueries);
        assertEquals(pairs.length, foundQueries, result.diagnostics());
        assertTrue(stats.getArcs() < inputGraph.getNodes() * 220, result.diagnostics());
        assertTrue(stats.getTriangles() < inputGraph.getNodes() * 35_000L, result.diagnostics());
        return result;
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

    private static CCHInputGraph weightedGridInputGraph(int rows, int columns) {
        List<CCHInputArc> arcs = new ArrayList<>();
        List<CCHInputEdge> supportEdges = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int node = row * columns + column;
                if (column > 0)
                    addBidirectionalArc(arcs, supportEdges, node - 1, node, 10 + row + (column % 3));
                if (row > 0)
                    addBidirectionalArc(arcs, supportEdges, node - columns, node, 10 + column + (row % 3));
                if (row > 0 && column > 0 && (row + column) % 5 == 0)
                    addBidirectionalArc(arcs, supportEdges, node - columns - 1, node, 17 + row + column);
            }
        }
        return new CCHInputGraph(rows * columns, arcs, supportEdges);
    }

    private static void addBidirectionalArc(List<CCHInputArc> arcs, List<CCHInputEdge> supportEdges,
                                            int from, int to, double weight) {
        int edgeId = supportEdges.size();
        supportEdges.add(new CCHInputEdge(from, to));
        arcs.add(new CCHInputArc(from, to, edgeId, false, weight, Math.round(weight * 100), weight));
        arcs.add(new CCHInputArc(to, from, edgeId, true, weight, Math.round(weight * 100), weight));
    }

    private static void writeOrder(Path orderFile, CCHNodeOrder order) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int node : order.getOrderArray()) {
            builder.append(node).append('\n');
        }
        Files.write(orderFile, builder.toString().getBytes(StandardCharsets.UTF_8));
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

    private static final class TrafficGridMetricSource implements CCHMetricSource {
        private final List<CCHMetricCandidate> candidates;
        private final int nodes;

        private TrafficGridMetricSource(CCHTopology topology, CCHInputGraph inputGraph) {
            this.nodes = inputGraph.getNodes();
            this.candidates = new ArrayList<>(inputGraph.getArcs());
            for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
                CCHInputArc arc = inputGraph.getArc(inputArc);
                double factor = trafficFactor(arc);
                candidates.add(new CCHMetricCandidate(
                        topology.getInputArcCCHArc(inputArc),
                        arc.getWeight() * factor,
                        Math.round(arc.getMillis() * factor),
                        arc.getDistance(),
                        CCHMetricProvenance.direct(arc.getBaseEdge(), arc.isReverse()),
                        (((long) arc.getBaseEdge()) << 1) | (arc.isReverse() ? 1L : 0L)));
            }
        }

        @Override
        public int getNodes() {
            return nodes;
        }

        @Override
        public TraversalMode getTraversalMode() {
            return TraversalMode.NODE_BASED;
        }

        @Override
        public boolean hasTurnCosts() {
            return false;
        }

        @Override
        public int getCandidates() {
            return candidates.size();
        }

        @Override
        public CCHMetricCandidate getCandidate(int index) {
            return candidates.get(index);
        }

        private static double trafficFactor(CCHInputArc arc) {
            if (arc.getBaseEdge() % 11 == 0)
                return 8;
            if (arc.getBaseEdge() % 7 == 0)
                return 3;
            return 1;
        }
    }

    private static final class BenchmarkResult {
        private final String order;
        private final CCHTopologyStatistics stats;
        private final long topologyBuildMillis;
        private final long statsMillis;
        private final long customizationMillis;
        private final long trafficCustomizationMillis;
        private final long queryMicros;
        private final int maxVisitedNodes;
        private final int foundQueries;

        private BenchmarkResult(String order, CCHTopologyStatistics stats, long topologyBuildMillis, long statsMillis,
                                long customizationMillis, long trafficCustomizationMillis, long queryMicros,
                                int maxVisitedNodes, int foundQueries) {
            this.order = order;
            this.stats = stats;
            this.topologyBuildMillis = topologyBuildMillis;
            this.statsMillis = statsMillis;
            this.customizationMillis = customizationMillis;
            this.trafficCustomizationMillis = trafficCustomizationMillis;
            this.queryMicros = queryMicros;
            this.maxVisitedNodes = maxVisitedNodes;
            this.foundQueries = foundQueries;
        }

        private String diagnostics() {
            return "order=" + order
                    + "\nstats=" + stats
                    + "\ntopologyBuildMillis=" + topologyBuildMillis
                    + ", statsMillis=" + statsMillis
                    + ", customizationMillis=" + customizationMillis
                    + ", trafficCustomizationMillis=" + trafficCustomizationMillis
                    + ", queryMicros=" + queryMicros
                    + ", maxVisitedNodes=" + maxVisitedNodes
                    + ", foundQueries=" + foundQueries;
        }
    }
}
