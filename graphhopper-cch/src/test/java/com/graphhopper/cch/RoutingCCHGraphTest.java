// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RoutingCCHGraphTest {
    @Test
    void keepsBaseGraphAsUnderlyingGraph() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        baseGraph.getNodeAccess().setNode(0, 0, 0);
        baseGraph.getNodeAccess().setNode(1, 1, 1);
        CCHTopology topology = topology(2, support(0, 1));
        CCHMetric metric = new CCHMetric(topology.getArcs());
        CCHStorage storage = CCHStorage.builder(2)
                .nodeOrder(CCHNodeOrder.identity(2))
                .upwardGraph(new int[]{0, 1, 1}, new int[]{1})
                .downwardGraph(new int[]{0, 0, 1}, new int[]{0})
                .baseEdgeMapping(new int[]{7, CCHStorage.NO_ARC}, new boolean[]{false, true})
                .build();

        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, storage, topology, metric, new NoOpWeighting());

        assertSame(baseGraph, routingGraph.getBaseGraph());
        assertSame(storage, routingGraph.getCCHStorage());
        assertSame(topology, routingGraph.getTopology());
        assertSame(metric, routingGraph.getMetric());
        assertEquals(2, routingGraph.getNodes());
        assertEquals(2, routingGraph.getArcs());
        assertEquals(1, storage.getUpArcs());
        assertEquals(1, storage.getDownArcs());
        assertEquals(1, storage.getDownArcId(0));
        assertEquals(1, routingGraph.getShortcuts());
        assertEquals(7, storage.getBaseEdge(0));
        assertFalse(storage.isReverse(0));
        assertEquals(CCHStorage.NO_ARC, storage.getBaseEdge(1));
        assertTrue(storage.isReverse(1));
    }

    @Test
    void isNotAProductionGraphOrClassicCHGraph() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        CCHTopology topology = topology(0);
        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, topology, new CCHMetric(0), new NoOpWeighting());

        assertFalse(routingGraph instanceof Graph);
        assertFalse(routingGraph instanceof RoutingCHGraph);
    }

    @Test
    void rejectsTurnCostWeightingsForV1() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        CCHTopology topology = topology(0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DefaultRoutingCCHGraph(baseGraph, topology, new CCHMetric(0), new TurnCostWeighting()));

        assertTrue(error.getMessage().contains("without turn costs"));
    }

    @Test
    void rejectsInconsistentOrderAndRank() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CCHStorage.builder(2)
                .order(new int[]{1, 0})
                .rank(new int[]{0, 1})
                .build());

        assertTrue(error.getMessage().contains("inverse"));
    }

    @Test
    void acceptsNodeOrderValueObject() {
        CCHStorage storage = CCHStorage.builder(3)
                .nodeOrder(CCHNodeOrder.fromOrder(new int[]{2, 0, 1}))
                .build();

        assertEquals(2, storage.getOrder(0));
        assertEquals(0, storage.getRank(2));
        assertEquals(1, storage.getRank(0));
        assertEquals(2, storage.getRank(1));
    }

    static class NoOpWeighting implements Weighting {
        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return 0;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return 0;
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
            return false;
        }

        @Override
        public String getName() {
            return "noop";
        }
    }

    private static final class TurnCostWeighting extends NoOpWeighting {
        @Override
        public boolean hasTurnCosts() {
            return true;
        }
    }

    private static CCHTopology topology(int nodes, CCHInputEdge... supportEdges) {
        return new CCHTopologyBuilder().build(
                new CCHInputGraph(nodes, Collections.emptyList(), Arrays.asList(supportEdges)),
                CCHNodeOrder.identity(nodes));
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }
}
