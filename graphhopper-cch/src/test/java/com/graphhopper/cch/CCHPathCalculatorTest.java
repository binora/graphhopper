// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.FlexiblePathCalculator;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

class CCHPathCalculatorTest {
    @Test
    void keepsRoutingOverlayAndQueryGraphSeparate() {
        Fixture fixture = fixture(1, CCHNodeOrder.identity(1));

        assertSame(fixture.routingGraph, fixture.calculator.getRoutingCCHGraph());
        assertSame(fixture.queryGraph, fixture.calculator.getQueryGraph());
        assertSame(fixture.topology, fixture.routingGraph.getTopology());
        assertSame(fixture.metric, fixture.routingGraph.getMetric());
        assertTrue(fixture.calculator.getDebugString().contains("algoInit"));
        assertEquals(0, fixture.calculator.getVisitedNodes());
    }

    @Test
    void sourceEqualsTargetReturnsZeroEdgePath() {
        Fixture fixture = fixture(3, CCHNodeOrder.identity(3));

        Path path = fixture.calculator.calcPaths(2, 2, new EdgeRestrictions()).get(0);

        assertTrue(path.isFound());
        assertEquals(2, path.getFromNode());
        assertEquals(2, path.getEndNode());
        assertEquals(0, path.getEdgeCount());
        assertEquals(IntArrayList.from(2), path.calcNodes());
        assertEquals(1, path.calcPoints().size());
        assertEquals(0, path.getWeight(), 1.e-9);
        assertEquals(0, path.getTime());
        assertEquals(0, path.getDistance(), 1.e-9);
        assertTrue(fixture.calculator.getVisitedNodes() > 0);
        assertTrue(fixture.calculator.getDebugString().contains("cch-routing"));
    }

    @Test
    void directPathReturnsGraphHopperPath() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        fixture.addEdge(0, 1, 7);
        fixture.prepare();

        Path path = fixture.calculator.calcPaths(0, 1, new EdgeRestrictions()).get(0);

        assertTrue(path.isFound());
        assertEquals(0, path.getFromNode());
        assertEquals(1, path.getEndNode());
        assertEquals(IntArrayList.from(0), path.getEdges());
        assertEquals(IntArrayList.from(0, 1), path.calcNodes());
        assertEquals(2, path.calcPoints().size());
        assertEquals(7, path.getWeight(), 1.e-9);
        assertEquals(70, path.getTime());
        assertEquals(7, path.getDistance(), 1.e-9);
    }

    @Test
    void shortcutPathReturnsOriginalBaseEdges() {
        Fixture fixture = fixture(3, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        fixture.addEdge(0, 1, 1);
        fixture.addEdge(1, 2, 2);
        fixture.prepare();

        Path path = fixture.calculator.calcPaths(0, 2, new EdgeRestrictions()).get(0);

        assertTrue(path.isFound());
        assertEquals(IntArrayList.from(0, 1), path.getEdges());
        assertEquals(IntArrayList.from(0, 1, 2), path.calcNodes());
        assertEquals(3, path.calcPoints().size());
        assertEquals(3, path.getWeight(), 1.e-9);
        assertEquals(30, path.getTime());
        assertEquals(3, path.getDistance(), 1.e-9);
    }

    @Test
    void disconnectedRouteReturnsUnfoundPath() {
        Fixture fixture = fixture(4, CCHNodeOrder.identity(4));
        fixture.addEdge(0, 1, 1);
        fixture.addEdge(2, 3, 1);
        fixture.prepare();

        List<Path> paths = fixture.calculator.calcPaths(0, 3, new EdgeRestrictions());

        assertEquals(1, paths.size());
        assertFalse(paths.get(0).isFound());
        assertEquals(0, paths.get(0).getEdgeCount());
    }

    @Test
    void rejectsUnsupportedRestrictions() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        fixture.addEdge(0, 1, 1);
        fixture.prepare();

        EdgeRestrictions unfavored = new EdgeRestrictions();
        unfavored.getUnfavoredEdges().add(0);
        assertUnsupportedRestriction(fixture, unfavored, "unfavored");

        EdgeRestrictions sourceOut = new EdgeRestrictions();
        sourceOut.setSourceOutEdge(0);
        assertUnsupportedRestriction(fixture, sourceOut, "Source/target edge restrictions");

        EdgeRestrictions targetIn = new EdgeRestrictions();
        targetIn.setTargetInEdge(0);
        assertUnsupportedRestriction(fixture, targetIn, "Source/target edge restrictions");
    }

    @Test
    void rejectsVirtualQueryGraphEndpointIds() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        fixture.addEdge(0, 1, 1);
        fixture.prepare();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.calculator.calcPaths(2, 0, new EdgeRestrictions()));

        assertTrue(error.getMessage().contains("virtual QueryGraph nodes"));
    }

    @Test
    void rejectsInconsistentPreparedData() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        fixture.addEdge(0, 1, 1);
        fixture.prepareTopologyOnly();

        assertThrows(IllegalArgumentException.class,
                () -> new DefaultRoutingCCHGraph(fixture.baseGraph, fixture.topology, new CCHMetric(fixture.topology.getArcs() + 1), fixture.weighting));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultRoutingCCHGraph(fixture.baseGraph, CCHStorage.builder(2).build(), fixture.topology, new CCHMetric(fixture.topology.getArcs()), fixture.weighting));
    }

    @Test
    void rejectsTurnCostWeightingsForV1() {
        Fixture fixture = fixture(1, CCHNodeOrder.identity(1));
        fixture.prepareTopologyOnly();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DefaultRoutingCCHGraph(fixture.baseGraph, fixture.topology, new CCHMetric(0), new TurnCostWeighting()));

        assertTrue(error.getMessage().contains("without turn costs"));
    }

    @Test
    void matchesFlexibleDijkstraOnStableTinyRouteFields() {
        Fixture fixture = fixture(4, CCHNodeOrder.fromOrder(new int[]{1, 2, 0, 3}));
        fixture.addEdge(0, 1, 1);
        fixture.addEdge(1, 3, 2);
        fixture.addEdge(0, 2, 5);
        fixture.addEdge(2, 3, 5);
        fixture.prepare();

        Path cchPath = fixture.calculator.calcPaths(0, 3, new EdgeRestrictions()).get(0);
        Path flexiblePath = flexiblePath(fixture, 0, 3);

        assertStablePathFieldsEqual(flexiblePath, cchPath);
    }

    private static void assertUnsupportedRestriction(Fixture fixture, EdgeRestrictions restrictions, String messagePart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.calculator.calcPaths(0, 1, restrictions));
        assertTrue(error.getMessage().contains(messagePart));
    }

    private static Path flexiblePath(Fixture fixture, int source, int target) {
        FlexiblePathCalculator calculator = new FlexiblePathCalculator(
                QueryGraph.create(fixture.baseGraph, Collections.emptyList()),
                new RoutingAlgorithmFactorySimple(),
                fixture.weighting,
                new AlgorithmOptions()
                        .setAlgorithm(DIJKSTRA_BI)
                        .setTraversalMode(TraversalMode.NODE_BASED));
        return calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
    }

    private static void assertStablePathFieldsEqual(Path expected, Path actual) {
        assertEquals(expected.isFound(), actual.isFound());
        assertEquals(expected.getFromNode(), actual.getFromNode());
        assertEquals(expected.getEndNode(), actual.getEndNode());
        assertEquals(expected.getEdges(), actual.getEdges());
        assertEquals(expected.calcNodes(), actual.calcNodes());
        assertEquals(expected.calcPoints().size(), actual.calcPoints().size());
        assertEquals(expected.getWeight(), actual.getWeight(), 1.e-9);
        assertEquals(expected.getTime(), actual.getTime());
        assertEquals(expected.getDistance(), actual.getDistance(), 1.e-9);
    }

    private static Fixture fixture(int nodes, CCHNodeOrder order) {
        return new Fixture(nodes, order);
    }

    private static final class Fixture {
        private final BaseGraph baseGraph;
        private final DistanceWeighting weighting = new DistanceWeighting();
        private final CCHNodeOrder order;
        private QueryGraph queryGraph;
        private CCHInputGraph inputGraph;
        private CCHTopology topology;
        private CCHMetric metric;
        private RoutingCCHGraph routingGraph;
        private CCHPathCalculator calculator;

        private Fixture(int nodes, CCHNodeOrder order) {
            this.order = order;
            baseGraph = new BaseGraph.Builder(1).create();
            for (int node = 0; node < nodes; node++) {
                baseGraph.getNodeAccess().setNode(node, node, node * 0.01);
            }
            prepare();
        }

        private void addEdge(int from, int to, double distance) {
            baseGraph.edge(from, to).setDistance(distance);
        }

        private void prepare() {
            prepareTopologyOnly();
            metric = new CCHMetricCustomizer().customize(topology, inputGraph);
            routingGraph = new DefaultRoutingCCHGraph(baseGraph, topology, metric, weighting);
            queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
            calculator = new CCHPathCalculator(routingGraph, queryGraph);
        }

        private void prepareTopologyOnly() {
            inputGraph = BaseGraphCCHInputBuilder.fromGraph(baseGraph, weighting);
            topology = new CCHTopologyBuilder().build(inputGraph, order);
        }
    }

    private static class DistanceWeighting implements Weighting {
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
            return "distance";
        }
    }

    private static final class TurnCostWeighting extends DistanceWeighting {
        @Override
        public boolean hasTurnCosts() {
            return true;
        }
    }
}
