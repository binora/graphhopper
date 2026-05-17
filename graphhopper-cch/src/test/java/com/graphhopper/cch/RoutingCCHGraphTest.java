// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutingCCHGraphTest {
    @Test
    void keepsBaseGraphAsUnderlyingGraph() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        baseGraph.getNodeAccess().setNode(0, 0, 0);
        baseGraph.getNodeAccess().setNode(1, 1, 1);
        CCHStorage storage = CCHStorage.builder(2)
                .upwardGraph(new int[]{0, 1, 1}, new int[]{1})
                .downwardGraph(new int[]{0, 0, 1}, new int[]{0})
                .baseEdgeMapping(new int[]{7, CCHStorage.NO_ARC}, new boolean[]{false, true})
                .build();

        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, storage, new NoOpWeighting());

        assertSame(baseGraph, routingGraph.getBaseGraph());
        assertSame(storage, routingGraph.getCCHStorage());
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
        CCHStorage storage = CCHStorage.builder(0).build();
        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, storage, new NoOpWeighting());

        assertFalse(routingGraph instanceof Graph);
        assertFalse(routingGraph instanceof RoutingCHGraph);
    }

    @Test
    void rejectsTurnCostWeightingsForV1() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        CCHStorage storage = CCHStorage.builder(0).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DefaultRoutingCCHGraph(baseGraph, storage, new TurnCostWeighting()));

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
}
