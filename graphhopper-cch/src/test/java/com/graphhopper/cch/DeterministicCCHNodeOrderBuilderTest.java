// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicCCHNodeOrderBuilderTest {
    @Test
    void ordersByUndirectedSupportDegreeThenNodeId() {
        CCHInputGraph inputGraph = inputGraph(6,
                new CCHInputEdge(1, 2),
                new CCHInputEdge(1, 3),
                new CCHInputEdge(2, 3),
                new CCHInputEdge(4, 1));

        CCHNodeOrder order = new DeterministicCCHNodeOrderBuilder().build(inputGraph);

        assertArrayEquals(new int[]{0, 5, 4, 2, 3, 1}, order.getOrderArray());
        assertArrayEquals(new int[]{0, 5, 3, 4, 2, 1}, order.getRankArray());
    }

    @Test
    void handlesDisconnectedGraphDeterministically() {
        CCHInputGraph inputGraph = inputGraph(4);

        CCHNodeOrder order = new DeterministicCCHNodeOrderBuilder().build(inputGraph);

        assertArrayEquals(new int[]{0, 1, 2, 3}, order.getOrderArray());
        assertArrayEquals(new int[]{0, 1, 2, 3}, order.getRankArray());
    }

    @Test
    void repeatedBuildsProduceSameOrder() {
        CCHInputGraph inputGraph = inputGraph(5,
                new CCHInputEdge(0, 4),
                new CCHInputEdge(3, 4),
                new CCHInputEdge(1, 2));
        DeterministicCCHNodeOrderBuilder builder = new DeterministicCCHNodeOrderBuilder();

        CCHNodeOrder first = builder.build(inputGraph);
        CCHNodeOrder second = builder.build(inputGraph);

        assertArrayEquals(first.getOrderArray(), second.getOrderArray());
        assertArrayEquals(first.getRankArray(), second.getRankArray());
    }

    @Test
    void rejectsNullInputGraph() {
        assertThrows(NullPointerException.class, () -> new DeterministicCCHNodeOrderBuilder().build(null));
    }

    private static CCHInputGraph inputGraph(int nodes, CCHInputEdge... supportEdges) {
        return new CCHInputGraph(nodes, Collections.emptyList(), Arrays.asList(supportEdges));
    }
}
