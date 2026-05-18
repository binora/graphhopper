// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBasedCCHPerformanceStorageGateTest {
    private static final long TEN_SECONDS_NANOS = 10_000_000_000L;
    private static final int H_VERSION = 4;

    @TempDir
    Path tempDir;

    @Test
    void mediumGridPreparationCustomizationAndQueryStayWithinSmokeBudget() {
        BaseGraph graph = gridGraph(9, 9);
        Weighting weighting = new DistanceTurnCostWeighting();

        long started = System.nanoTime();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .buildFromBaseNodeOrder(edgeGraph, baseCoordinateOrder(graph));
        CCHMetric metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology,
                new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology()));
        EdgeBasedCCHQuery query = new EdgeBasedCCHQuery(graph, weighting, edgeTopology, metric);
        EdgeBasedCCHPathUnpacker unpacker = new EdgeBasedCCHPathUnpacker(edgeTopology, metric);

        int nodes = graph.getNodes();
        for (int source = 0; source < nodes; source += 10) {
            int target = nodes - 1 - source;
            EdgeBasedCCHQueryResult result = query.calc(source, target);
            CCHUnpackedPath path = unpacker.unpack(result);
            assertTrue(result.isFound(), "expected grid route " + source + " -> " + target);
            assertTrue(path.isFound(), "expected unpacked grid route " + source + " -> " + target);
            assertEquals(result.getWeight(), path.getWeight(), 1.e-9);
        }
        long elapsed = System.nanoTime() - started;

        assertEquals(graph.getEdges() * 2, edgeGraph.getStates());
        assertEquals(edgeGraph.getStates(), edgeTopology.getStates());
        assertEquals(edgeTopology.getTopology().getArcs(), metric.getArcs());
        assertTrue(edgeTopology.getTopology().getArcs() < edgeGraph.getStates() * edgeGraph.getStates());
        assertTrue(elapsed < TEN_SECONDS_NANOS, "edge CCH smoke gate took " + elapsed + " ns");
    }

    @Test
    void nodeTopologyRemainsLoadableAlongsideEdgeTopology() {
        BaseGraph graph = gridGraph(3, 3);
        CCHTopology nodeTopology = new CCHTopologyBuilder().build(
                BaseGraphCCHSupportBuilder.fromGraph(graph), CCHNodeOrder.identity(graph.getNodes()));
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .buildFromBaseNodeOrder(edgeGraph, baseCoordinateOrder(graph));

        CCHDataAccessStore writer = store("compat");
        writer.saveTopology(nodeTopology);
        writer.saveEdgeTopology(edgeTopology);
        writer.close();

        CCHDataAccessStore reader = store("compat");
        CCHTopology loadedNodeTopology = reader.loadTopology(graph.getNodes());
        EdgeStateCCHTopology loadedEdgeTopology = reader.loadEdgeTopology(graph.getNodes(), graph.getEdges());
        assertArrayEquals(nodeTopology.getUpFirstOutArray(), loadedNodeTopology.getUpFirstOutArray());
        assertArrayEquals(nodeTopology.getUpHeadArray(), loadedNodeTopology.getUpHeadArray());
        assertArrayEquals(edgeTopology.getEdgeStateInputGraph().getStateEdgeKeyArray(),
                loadedEdgeTopology.getEdgeStateInputGraph().getStateEdgeKeyArray());
        reader.close();
    }

    @Test
    void incompatibleEdgeTopologyVersionIsRejectedClearly() {
        BaseGraph graph = gridGraph(2, 2);
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));

        CCHDataAccessStore writer = store("bad-version");
        writer.saveEdgeTopology(edgeTopology);
        writer.close();
        mutateEdgeTopologyHeader("bad-version", H_VERSION, 99);

        CCHDataAccessStore reader = store("bad-version");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reader.loadEdgeTopology(graph.getNodes(), graph.getEdges()));
        assertTrue(error.getMessage().contains("unsupported version"), error.getMessage());
        reader.close();
    }

    private CCHDataAccessStore store(String directory) {
        return new CCHDataAccessStore(new RAMDirectory(tempDir.resolve(directory).toString(), true).create(), -1);
    }

    private void mutateEdgeTopologyHeader(String directory, int offset, int value) {
        RAMDirectory dir = new RAMDirectory(tempDir.resolve(directory).toString(), true);
        dir.create();
        DataAccess access = dir.create("cch_edge_topology");
        assertTrue(access.loadExisting());
        access.setHeader(offset, value);
        access.flush();
        access.close();
        dir.close();
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

    private static CCHNodeOrder baseCoordinateOrder(BaseGraph graph) {
        return new CoordinateNestedDissectionCCHNodeOrderProvider(graph, 4)
                .build(BaseGraphCCHSupportBuilder.fromGraph(graph));
    }

    private static final class DistanceTurnCostWeighting implements Weighting {
        @Override
        public double calcMinWeightPerDistance() {
            return 1;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return edgeState.getDistance();
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return Math.round(edgeState.getDistance() * 10);
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return inEdge == outEdge ? 7 : 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return inEdge == outEdge ? 70 : 0;
        }

        @Override
        public boolean hasTurnCosts() {
            return true;
        }

        @Override
        public String getName() {
            return "distance_turn_cost";
        }
    }
}
