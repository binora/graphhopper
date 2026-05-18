// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EdgeStateCCHNodeOrderBuilderTest {

    @Test
    void liftsBaseNodeOrderByStateHeadRankThenEdgeKey() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);

        CCHNodeOrder order = new EdgeStateCCHNodeOrderBuilder()
                .build(edgeGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        assertArrayEquals(new int[]{
                state(edgeGraph, first, false),
                state(edgeGraph, second, true),
                state(edgeGraph, first, true),
                state(edgeGraph, second, false)
        }, order.getOrderArray());
    }

    @Test
    void buildsDeterministicTopologyFromLiftedCoordinateOrder() {
        BaseGraph graph = gridGraph(5, 5);
        CCHInputGraph nodeInputGraph = BaseGraphCCHSupportBuilder.fromGraph(graph);
        CCHNodeOrder baseOrder = new CoordinateNestedDissectionCCHNodeOrderProvider(graph, 3).build(nodeInputGraph);
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);

        CCHNodeOrder liftedOrder = new EdgeStateCCHNodeOrderBuilder().build(edgeGraph, baseOrder);
        EdgeStateCCHTopology first = new EdgeStateCCHTopologyBuilder().build(edgeGraph, liftedOrder);
        EdgeStateCCHTopology second = new EdgeStateCCHTopologyBuilder().buildFromBaseNodeOrder(edgeGraph, baseOrder);
        EdgeStateCCHTopology identity = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));

        assertArrayEquals(first.getNodeOrder().getOrderArray(), second.getNodeOrder().getOrderArray());
        assertArrayEquals(first.getTopology().getUpHeadArray(), second.getTopology().getUpHeadArray());
        assertFalse(Arrays.equals(identity.getNodeOrder().getOrderArray(), first.getNodeOrder().getOrderArray()));

        CCHTopologyStatistics identityStats = CCHOrderDiagnostics.analyze(identity);
        CCHTopologyStatistics liftedStats = CCHOrderDiagnostics.analyze(first);
        assertNotEquals(identityStats, liftedStats);
        assertTrue(liftedStats.getMaxEliminationTreeDepth() <= identityStats.getMaxEliminationTreeDepth(),
                "lifted=" + liftedStats + ", identity=" + identityStats);
    }

    @Test
    void liftedOrderPreservesEdgeBasedTurnCostCorrectness() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        int direct = graph.edge(0, 2).setDistance(100).getEdge();
        CCHNodeOrder baseOrder = CCHNodeOrder.fromOrder(new int[]{1, 0, 2});
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .buildFromBaseNodeOrder(edgeGraph, baseOrder);
        Weighting weighting = new DistanceWeighting();
        CCHMetric metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology,
                new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology()));
        EdgeBasedCCHQuery query = new EdgeBasedCCHQuery(graph, weighting, edgeTopology, metric);
        EdgeBasedCCHPathUnpacker unpacker = new EdgeBasedCCHPathUnpacker(edgeTopology, metric);

        CCHUnpackedPath path = unpacker.unpack(query.calc(0, 2));

        assertTrue(path.isFound());
        assertEquals(30, path.getWeight(), 1.e-9);
        assertEquals(Arrays.asList(GHUtility.createEdgeKey(first, false), GHUtility.createEdgeKey(second, false)),
                path.getEdges().stream().map(CCHUnpackedEdge::getEdgeKey).toList());
        assertNotEquals(GHUtility.createEdgeKey(direct, false), path.getEdges().get(0).getEdgeKey());
    }

    @Test
    void rejectsInvalidInputsClearly() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHNodeOrderBuilder builder = new EdgeStateCCHNodeOrderBuilder();

        assertThrows(NullPointerException.class, () -> builder.build((EdgeStateCCHInputGraph) null, CCHNodeOrder.identity(2)));
        assertThrows(NullPointerException.class, () -> builder.build(edgeGraph, null));
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> builder.build(edgeGraph, CCHNodeOrder.identity(3)));
        assertTrue(mismatch.getMessage().contains("base node order count"), mismatch.getMessage());
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

    private static int state(EdgeStateCCHInputGraph edgeGraph, int edge, boolean reverse) {
        return edgeGraph.getStateForEdgeKey(GHUtility.createEdgeKey(edge, reverse));
    }

    private static final class DistanceWeighting implements Weighting {
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
            return Math.round(edgeState.getDistance());
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public boolean hasTurnCosts() {
            return true;
        }

        @Override
        public String getName() {
            return "distance";
        }
    }
}
