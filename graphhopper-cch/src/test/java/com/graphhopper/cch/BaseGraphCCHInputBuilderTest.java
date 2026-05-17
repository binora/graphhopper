// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseGraphCCHInputBuilderTest {
    @Test
    void emitsDirectedArcsInBaseGraphOrder() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int bidirectional = graph.edge(0, 1).setDistance(10).getEdge();
        int forwardOnly = graph.edge(1, 2).setDistance(20).getEdge();
        int reverseOnly = graph.edge(2, 3).setDistance(30).getEdge();
        int inaccessible = graph.edge(3, 4).setDistance(40).getEdge();

        TestWeighting weighting = new TestWeighting()
                .set(bidirectional, false, 1.5, 15)
                .set(bidirectional, true, 2.5, 25)
                .set(forwardOnly, false, 3.5, 35)
                .set(reverseOnly, true, 4.5, 45)
                .set(inaccessible, false, Double.POSITIVE_INFINITY, 0)
                .set(inaccessible, true, Double.POSITIVE_INFINITY, 0);

        CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(graph, weighting);

        assertEquals(5, inputGraph.getNodes());
        assertEquals(4, inputGraph.getArcs());
        assertArc(inputGraph.getArc(0), 0, 1, bidirectional, false, 1.5, 15, 10);
        assertArc(inputGraph.getArc(1), 1, 0, bidirectional, true, 2.5, 25, 10);
        assertArc(inputGraph.getArc(2), 1, 2, forwardOnly, false, 3.5, 35, 20);
        assertArc(inputGraph.getArc(3), 3, 2, reverseOnly, true, 4.5, 45, 30);

        assertEquals(3, inputGraph.getSupportEdges());
        assertSupportEdge(inputGraph.getSupportEdge(0), 0, 1);
        assertSupportEdge(inputGraph.getSupportEdge(1), 1, 2);
        assertSupportEdge(inputGraph.getSupportEdge(2), 2, 3);
    }

    @Test
    void deDuplicatesSupportEdgesButPreservesParallelArcs() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(2, 0).setDistance(10).getEdge();
        int second = graph.edge(0, 2).setDistance(20).getEdge();

        TestWeighting weighting = new TestWeighting()
                .set(first, false, 1, 10)
                .set(second, false, 2, 20);

        CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(graph, weighting);

        assertEquals(2, inputGraph.getArcs());
        assertArc(inputGraph.getArc(0), 2, 0, first, false, 1, 10, 10);
        assertArc(inputGraph.getArc(1), 0, 2, second, false, 2, 20, 20);
        assertEquals(1, inputGraph.getSupportEdges());
        assertSupportEdge(inputGraph.getSupportEdge(0), 0, 2);
    }

    @Test
    void baseGraphRejectsLoopsBeforeAdapterSeesThem() {
        BaseGraph graph = new BaseGraph.Builder(1).create();

        assertThrows(IllegalArgumentException.class, () -> graph.edge(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CCHInputEdge(1, 1));
    }

    @Test
    void rejectsTurnCostWeighting() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BaseGraphCCHInputBuilder.fromGraph(graph, new TestWeighting().withTurnCosts()));

        assertTrue(error.getMessage().contains("without turn costs"));
    }

    @Test
    void rejectsNaNAndNegativeWeights() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int edge = graph.edge(0, 1).setDistance(10).getEdge();

        IllegalArgumentException nanError = assertThrows(IllegalArgumentException.class,
                () -> BaseGraphCCHInputBuilder.fromGraph(graph, new TestWeighting().set(edge, false, Double.NaN, 0)));
        assertTrue(nanError.getMessage().contains("NaN"));

        IllegalArgumentException negativeError = assertThrows(IllegalArgumentException.class,
                () -> BaseGraphCCHInputBuilder.fromGraph(graph, new TestWeighting().set(edge, false, -1, 0)));
        assertTrue(negativeError.getMessage().contains("-1.0"));
    }

    @Test
    void rejectsNegativeMillis() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int edge = graph.edge(0, 1).setDistance(10).getEdge();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BaseGraphCCHInputBuilder.fromGraph(graph, new TestWeighting().set(edge, false, 1, -1)));

        assertTrue(error.getMessage().contains("Negative millis"));
    }

    @Test
    void inputGraphListsAreImmutable() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int edge = graph.edge(0, 1).setDistance(10).getEdge();
        CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(graph, new TestWeighting().set(edge, false, 1, 10));

        assertThrows(UnsupportedOperationException.class, () -> inputGraph.getAllArcs().clear());
        assertThrows(UnsupportedOperationException.class, () -> inputGraph.getAllSupportEdges().clear());
    }

    private static void assertArc(CCHInputArc arc, int from, int to, int baseEdge, boolean reverse,
                                  double weight, long millis, double distance) {
        assertEquals(from, arc.getFrom());
        assertEquals(to, arc.getTo());
        assertEquals(baseEdge, arc.getBaseEdge());
        assertEquals(reverse, arc.isReverse());
        assertEquals(weight, arc.getWeight(), 1.e-9);
        assertEquals(millis, arc.getMillis());
        assertEquals(distance, arc.getDistance(), 1.e-9);
    }

    private static void assertSupportEdge(CCHInputEdge edge, int nodeA, int nodeB) {
        assertEquals(nodeA, edge.getNodeA());
        assertEquals(nodeB, edge.getNodeB());
    }

    private static final class TestWeighting implements Weighting {
        private final Map<Key, Value> values = new HashMap<>();
        private boolean turnCosts;

        TestWeighting set(int edge, boolean reverse, double weight, long millis) {
            values.put(new Key(edge, reverse), new Value(weight, millis));
            return this;
        }

        TestWeighting withTurnCosts() {
            turnCosts = true;
            return this;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return value(edgeState, reverse).weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return value(edgeState, reverse).millis;
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
            return turnCosts;
        }

        @Override
        public String getName() {
            return "test";
        }

        private Value value(EdgeIteratorState edgeState, boolean reverse) {
            return values.getOrDefault(new Key(edgeState.getEdge(), reverse), Value.INACCESSIBLE);
        }
    }

    private static final class Key {
        private final int edge;
        private final boolean reverse;

        private Key(int edge, boolean reverse) {
            this.edge = edge;
            this.reverse = reverse;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Key))
                return false;
            Key key = (Key) o;
            return edge == key.edge && reverse == key.reverse;
        }

        @Override
        public int hashCode() {
            return 31 * edge + Boolean.hashCode(reverse);
        }
    }

    private static final class Value {
        private static final Value INACCESSIBLE = new Value(Double.POSITIVE_INFINITY, 0);

        private final double weight;
        private final long millis;

        private Value(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }
    }
}
