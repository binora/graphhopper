// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.FlexiblePathCalculator;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    void rejectsEndpointsOutsideQueryGraph() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        fixture.addEdge(0, 1, 1);
        fixture.prepare();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.calculator.calcPaths(2, 0, new EdgeRestrictions()));

        assertTrue(error.getMessage().contains("outside query graph nodes"));
    }

    @Test
    void virtualSourceToTowerTargetMatchesFlexibleDijkstra() {
        Fixture fixture = fixture(3, CCHNodeOrder.identity(3));
        EdgeIteratorState sourceEdge = fixture.addEdge(0, 1, 1);
        fixture.addEdge(1, 2, 2);
        fixture.prepare();
        Snap sourceSnap = snap(0.25, 0.0025, sourceEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.baseGraph, sourceSnap);
        fixture.useQueryGraph(queryGraph);

        assertMatchesFlexible(fixture, sourceSnap.getClosestNode(), 2);
        assertTrue(fixture.calculator.calcPaths(sourceSnap.getClosestNode(), 2, new EdgeRestrictions()).get(0).getEdges().get(0) >= fixture.baseGraph.getEdges());
    }

    @Test
    void towerSourceToVirtualTargetMatchesFlexibleDijkstra() {
        Fixture fixture = fixture(3, CCHNodeOrder.identity(3));
        fixture.addEdge(0, 1, 1);
        EdgeIteratorState targetEdge = fixture.addEdge(1, 2, 2);
        fixture.prepare();
        Snap targetSnap = snap(1.75, 0.0175, targetEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.baseGraph, targetSnap);
        fixture.useQueryGraph(queryGraph);

        assertMatchesFlexible(fixture, 0, targetSnap.getClosestNode());
    }

    @Test
    void virtualEndpointsOnDifferentEdgesMatchFlexibleDijkstra() {
        Fixture fixture = fixture(3, CCHNodeOrder.identity(3));
        EdgeIteratorState sourceEdge = fixture.addEdge(0, 1, 1);
        EdgeIteratorState targetEdge = fixture.addEdge(1, 2, 2);
        fixture.prepare();
        Snap sourceSnap = snap(0.25, 0.0025, sourceEdge);
        Snap targetSnap = snap(1.75, 0.0175, targetEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.baseGraph, sourceSnap, targetSnap);
        fixture.useQueryGraph(queryGraph);

        assertMatchesFlexible(fixture, sourceSnap.getClosestNode(), targetSnap.getClosestNode());
    }

    @Test
    void virtualEndpointsOnSameSplitEdgeUseDirectVirtualSegment() {
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2));
        EdgeIteratorState edge = fixture.addEdge(0, 1, 1);
        fixture.prepare();
        Snap sourceSnap = snap(0.25, 0.0025, edge);
        Snap targetSnap = snap(0.75, 0.0075, edge);
        QueryGraph queryGraph = QueryGraph.create(fixture.baseGraph, sourceSnap, targetSnap);
        fixture.useQueryGraph(queryGraph);

        Path cchPath = fixture.calculator.calcPaths(sourceSnap.getClosestNode(), targetSnap.getClosestNode(), new EdgeRestrictions()).get(0);
        assertMatchesFlexible(fixture, sourceSnap.getClosestNode(), targetSnap.getClosestNode());
        assertTrue(cchPath.isFound());
        assertEquals(1, cchPath.getEdgeCount());
        assertTrue(cchPath.getEdges().get(0) >= fixture.baseGraph.getEdges());
    }

    @Test
    void directedVirtualEndpointAccessMatchesFlexibleDijkstra() {
        DirectedWeighting weighting = new DirectedWeighting();
        Fixture fixture = fixture(2, CCHNodeOrder.identity(2), weighting);
        EdgeIteratorState edge = fixture.addEdge(0, 1, 1);
        weighting.set(edge.getEdge(), false, true).set(edge.getEdge(), true, false);
        fixture.prepare();
        Snap snap = snap(0.25, 0.0025, edge);
        QueryGraph queryGraph = QueryGraph.create(fixture.baseGraph, snap);
        fixture.useQueryGraph(queryGraph);

        assertTrue(fixture.calculator.calcPaths(snap.getClosestNode(), 1, new EdgeRestrictions()).get(0).isFound());

        weighting.set(edge.getEdge(), false, false).set(edge.getEdge(), true, true);
        fixture.prepare();
        snap = snap(0.25, 0.0025, edge);
        queryGraph = QueryGraph.create(fixture.baseGraph, snap);
        fixture.useQueryGraph(queryGraph);

        assertTrue(fixture.calculator.calcPaths(1, snap.getClosestNode(), new EdgeRestrictions()).get(0).isFound());

        weighting.set(edge.getEdge(), false, false).set(edge.getEdge(), true, false);
        fixture.prepare();
        snap = snap(0.25, 0.0025, edge);
        queryGraph = QueryGraph.create(fixture.baseGraph, snap);
        fixture.useQueryGraph(queryGraph);

        assertFalse(fixture.calculator.calcPaths(snap.getClosestNode(), 1, new EdgeRestrictions()).get(0).isFound());
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
        return flexiblePath(fixture, QueryGraph.create(fixture.baseGraph, Collections.emptyList()), source, target);
    }

    private static Path flexiblePath(Fixture fixture, QueryGraph queryGraph, int source, int target) {
        FlexiblePathCalculator calculator = new FlexiblePathCalculator(
                queryGraph,
                new RoutingAlgorithmFactorySimple(),
                fixture.weighting,
                new AlgorithmOptions()
                        .setAlgorithm(DIJKSTRA_BI)
                        .setTraversalMode(TraversalMode.NODE_BASED));
        return calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
    }

    private static void assertMatchesFlexible(Fixture fixture, int source, int target) {
        Path cchPath = fixture.calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
        Path flexiblePath = flexiblePath(fixture, fixture.queryGraph, source, target);
        assertStablePathFieldsEqual(flexiblePath, cchPath);
    }

    private static void assertStablePathFieldsEqual(Path expected, Path actual) {
        assertEquals(expected.isFound(), actual.isFound());
        if (!expected.isFound()) {
            assertFalse(actual.isFound());
            assertEquals(expected.getEdgeCount(), actual.getEdgeCount());
            return;
        }
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
        return fixture(nodes, order, new DistanceWeighting());
    }

    private static Fixture fixture(int nodes, CCHNodeOrder order, Weighting weighting) {
        return new Fixture(nodes, order, weighting);
    }

    private static Snap snap(double lat, double lon, EdgeIteratorState edge) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(new DistanceCalcEarth());
        return snap;
    }

    private static final class Fixture {
        private final BaseGraph baseGraph;
        private final Weighting weighting;
        private final CCHNodeOrder order;
        private QueryGraph queryGraph;
        private CCHInputGraph inputGraph;
        private CCHTopology topology;
        private CCHMetric metric;
        private RoutingCCHGraph routingGraph;
        private CCHPathCalculator calculator;

        private Fixture(int nodes, CCHNodeOrder order, Weighting weighting) {
            this.order = order;
            this.weighting = weighting;
            baseGraph = new BaseGraph.Builder(1).create();
            for (int node = 0; node < nodes; node++) {
                baseGraph.getNodeAccess().setNode(node, node, node * 0.01);
            }
            prepare();
        }

        private EdgeIteratorState addEdge(int from, int to, double distance) {
            return baseGraph.edge(from, to).setDistance(distance);
        }

        private void prepare() {
            prepareTopologyOnly();
            metric = new CCHMetricCustomizer().customize(topology, inputGraph);
            routingGraph = new DefaultRoutingCCHGraph(baseGraph, topology, metric, weighting);
            queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
            calculator = new CCHPathCalculator(routingGraph, queryGraph);
        }

        private void useQueryGraph(QueryGraph queryGraph) {
            this.queryGraph = queryGraph;
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

    private static final class DirectedWeighting extends DistanceWeighting {
        private final Map<Key, Boolean> access = new HashMap<>();

        private DirectedWeighting set(int edge, boolean reverse, boolean accessible) {
            access.put(new Key(edge, reverse), accessible);
            return this;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = originalEdgeKey(edgeState, reverse);
            boolean traversalReverse = (edgeKey & 1) == 1;
            boolean accessible = access.getOrDefault(new Key(edgeKey / 2, traversalReverse), true);
            return accessible ? edgeState.getDistance() : Double.POSITIVE_INFINITY;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            double weight = calcEdgeWeight(edgeState, reverse);
            return Double.isFinite(weight) ? Math.round(edgeState.getDistance() * 10) : Long.MAX_VALUE;
        }

        private static int originalEdgeKey(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = edgeState instanceof VirtualEdgeIteratorState
                    ? ((VirtualEdgeIteratorState) edgeState).getOriginalEdgeKey()
                    : edgeState.getEdgeKey();
            return reverse ? GHUtility.reverseEdgeKey(edgeKey) : edgeKey;
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
}
