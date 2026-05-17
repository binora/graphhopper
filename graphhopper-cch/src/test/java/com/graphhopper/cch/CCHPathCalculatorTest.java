// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CCHPathCalculatorTest {
    @Test
    void keepsRoutingOverlayAndQueryGraphSeparate() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        CCHStorage storage = CCHStorage.builder(0).build();
        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, storage, new RoutingCCHGraphTest.NoOpWeighting());
        QueryGraph queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
        CCHPathCalculator calculator = new CCHPathCalculator(routingGraph, queryGraph);

        assertSame(routingGraph, calculator.getRoutingCCHGraph());
        assertSame(queryGraph, calculator.getQueryGraph());
        assertEquals("", calculator.getDebugString());
        assertEquals(0, calculator.getVisitedNodes());
    }

    @Test
    void queryAlgorithmIsExplicitlyNotPartOfTheInterfaceSlice() {
        BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, CCHStorage.builder(0).build(), new RoutingCCHGraphTest.NoOpWeighting());
        CCHPathCalculator calculator = new CCHPathCalculator(routingGraph, QueryGraph.create(baseGraph, Collections.emptyList()));

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> calculator.calcPaths(0, 0, new EdgeRestrictions()));

        assertTrue(error.getMessage().contains("later graphhopper-cch beads"));
        assertEquals(", cch-routing:not-implemented", calculator.getDebugString());
        assertEquals(0, calculator.getVisitedNodes());
    }
}
