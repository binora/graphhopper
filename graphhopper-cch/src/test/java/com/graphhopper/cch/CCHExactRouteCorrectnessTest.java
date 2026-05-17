// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.carrotsearch.hppc.IntIndexedContainer;
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
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

class CCHExactRouteCorrectnessTest {
    @Test
    void pathGraphMiddleFirstHasExactTopologyMetricQueryAndRouteParity() {
        Fixture fixture = baseGraphFixture(3, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                edge(0, 1, 1),
                edge(1, 2, 2));
        CCHTopology topology = fixture.topology;
        CCHTriangleEnumerator triangles = new CCHTriangleEnumerator(topology);

        assertEquals(3, topology.getUpArcs());
        assertEquals(3, topology.getDownArcs());
        int fillArc = arc(topology, 0, 2);
        assertTrue(topology.isFillArc(fillArc));
        assertEquals(Collections.singletonList(triangle(CCHTriangleType.LOWER, fillArc,
                arc(topology, 0, 1), arc(topology, 1, 2))), triangles.getLowerTriangles(fillArc));

        CCHMetric metric = fixture.metric;
        assertTrue(metric.isShortcut(fillArc));
        assertEquals(3, metric.getWeight(fillArc), 1.e-9);
        assertEquals(30, metric.getMillis(fillArc));
        assertEquals(3, metric.getDistance(fillArc), 1.e-9);
        assertEquals(arc(topology, 0, 1), metric.getSkippedArc1(fillArc));
        assertEquals(arc(topology, 1, 2), metric.getSkippedArc2(fillArc));

        CCHQueryResult result = fixture.query.calc(0, 2);
        assertTrue(result.isFound());
        assertEquals(3, result.getWeight(), 1.e-9);
        assertEquals(2, result.getMeetingNode());
        assertEquals(fillArc, result.getForwardPredecessorArcArray()[2]);

        CCHUnpackedPath unpacked = fixture.unpacker.unpack(result);
        assertUnpackedEdges(unpacked, edgeStep(0, false, 0, 1, 1), edgeStep(1, false, 1, 2, 2));

        Path cchPath = fixture.calculator.calcPaths(0, 2, new EdgeRestrictions()).get(0);
        Path flexiblePath = flexiblePath(fixture, 0, 2);
        assertEquals(stableRoute(flexiblePath), stableRoute(cchPath));
    }

    @Test
    void disconnectedFixtureHasNoCrossComponentTrianglesOrRoute() {
        Fixture fixture = baseGraphFixture(5, CCHNodeOrder.fromOrder(new int[]{1, 0, 3, 2, 4}),
                edge(0, 1, 1),
                edge(2, 3, 1));
        CCHTriangleEnumerator triangles = new CCHTriangleEnumerator(fixture.topology);

        assertEquals(2, fixture.topology.getUpArcs());
        assertEquals(2, fixture.topology.getDownArcs());
        for (int cchArc = 0; cchArc < fixture.topology.getArcs(); cchArc++) {
            assertTrue(triangles.getLowerTriangles(cchArc).isEmpty());
            assertTrue(triangles.getIntermediateTriangles(cchArc).isEmpty());
            assertTrue(triangles.getUpperTriangles(cchArc).isEmpty());
            assertFalse(fixture.topology.isFillArc(cchArc));
        }

        CCHQueryResult result = fixture.query.calc(0, 3);
        assertFalse(result.isFound());
        assertTrue(Double.isInfinite(result.getWeight()));

        Path cchPath = fixture.calculator.calcPaths(0, 3, new EdgeRestrictions()).get(0);
        Path flexiblePath = flexiblePath(fixture, 0, 3);
        assertEquals(stableRoute(flexiblePath), stableRoute(cchPath));
    }

    @Test
    void oneWayFixtureKeepsReverseOverlayArcInaccessible() {
        Fixture fixture = manualFixture(2, CCHNodeOrder.identity(2),
                Collections.singletonList(inputArc(0, 1, 0, false, 4, 40, 4)),
                Collections.singletonList(edge(0, 1, 4)),
                support(0, 1));

        int forward = arc(fixture.topology, 0, 1);
        int reverse = arc(fixture.topology, 1, 0);
        assertTrue(fixture.metric.isDirect(forward));
        assertEquals(0, fixture.metric.getBaseEdge(forward));
        assertFalse(fixture.metric.isReverse(forward));
        assertTrue(Double.isInfinite(fixture.metric.getWeight(reverse)));
        assertEquals(CCHMetricProvenance.Type.NONE, fixture.metric.getProvenance(reverse).getType());

        CCHUnpackedPath forwardPath = fixture.unpacker.unpack(fixture.query.calc(0, 1));
        assertUnpackedEdges(forwardPath, edgeStep(0, false, 0, 1, 4));

        CCHQueryResult reverseResult = fixture.query.calc(1, 0);
        assertFalse(reverseResult.isFound());
        assertFalse(fixture.calculator.calcPaths(1, 0, new EdgeRestrictions()).get(0).isFound());
    }

    @Test
    void bidirectionalAsymmetricFixturePreservesReverseFlagAndMetrics() {
        Fixture fixture = manualFixture(2, CCHNodeOrder.identity(2),
                Arrays.asList(
                        inputArc(0, 1, 0, false, 2, 20, 2),
                        inputArc(1, 0, 0, true, 5, 50, 2)),
                Collections.singletonList(edge(0, 1, 2)),
                support(0, 1));

        CCHUnpackedPath forward = fixture.unpacker.unpack(fixture.query.calc(0, 1));
        CCHUnpackedPath reverse = fixture.unpacker.unpack(fixture.query.calc(1, 0));

        assertUnpackedEdges(forward, edgeStep(0, false, 0, 1, 2));
        assertEquals(20, forward.getMillis());
        assertEquals(2, forward.getDistance(), 1.e-9);
        assertUnpackedEdges(reverse, edgeStep(0, true, 1, 0, 5));
        assertEquals(50, reverse.getMillis());
        assertEquals(2, reverse.getDistance(), 1.e-9);

        Path reversePath = fixture.calculator.calcPaths(1, 0, new EdgeRestrictions()).get(0);
        assertEquals("[found=true, from=1, to=0, weight=5.0, time=50, distance=2.0, edges=[0], nodes=[1,0], points=[1.0:0.01,0.0:0.0]]",
                stableRoute(reversePath));
    }

    @Test
    void equalDistanceFixtureKeepsDirectProvenanceAndExactRoute() {
        Fixture fixture = baseGraphFixture(3, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                edge(0, 2, 2),
                edge(0, 1, 1),
                edge(1, 2, 1));

        int target = arc(fixture.topology, 0, 2);
        assertTrue(fixture.metric.isDirect(target));
        assertEquals(0, fixture.metric.getBaseEdge(target));
        assertEquals(CCHStorage.NO_ARC, fixture.metric.getSkippedArc1(target));
        assertEquals(CCHStorage.NO_ARC, fixture.metric.getSkippedArc2(target));

        CCHUnpackedPath unpacked = fixture.unpacker.unpack(fixture.query.calc(0, 2));
        assertUnpackedEdges(unpacked, edgeStep(0, false, 0, 2, 2));

        Path cchPath = fixture.calculator.calcPaths(0, 2, new EdgeRestrictions()).get(0);
        assertEquals("[found=true, from=0, to=2, weight=2.0, time=20, distance=2.0, edges=[0], nodes=[0,2], points=[0.0:0.0,2.0:0.02]]",
                stableRoute(cchPath));
    }

    @Test
    void metricChangesDoNotChangeTopologyArrays() {
        CCHNodeOrder order = CCHNodeOrder.fromOrder(new int[]{1, 0, 2});
        CCHInputGraph first = inputGraph(3,
                Arrays.asList(
                        inputArc(0, 1, 0, false, 1, 10, 1),
                        inputArc(1, 2, 1, false, 2, 20, 2)),
                support(0, 1), support(1, 2));
        CCHInputGraph second = inputGraph(3,
                Arrays.asList(
                        inputArc(0, 1, 0, false, 4, 40, 4),
                        inputArc(1, 2, 1, false, 5, 50, 5)),
                support(0, 1), support(1, 2));
        CCHTopology topology = new CCHTopologyBuilder().build(first, order);
        int[] upFirstOut = topology.getUpFirstOutArray();
        int[] upHead = topology.getUpHeadArray();
        int[] downFirstOut = topology.getDownFirstOutArray();
        int[] downHead = topology.getDownHeadArray();

        CCHMetric firstMetric = new CCHMetricCustomizer().customize(topology, first);
        CCHMetric secondMetric = new CCHMetricCustomizer().customize(topology, new NodeBasedCCHMetricSource(topology, second));

        assertEquals(3, firstMetric.getWeight(arc(topology, 0, 2)), 1.e-9);
        assertEquals(9, secondMetric.getWeight(arc(topology, 0, 2)), 1.e-9);
        assertArrayEquals(upFirstOut, topology.getUpFirstOutArray());
        assertArrayEquals(upHead, topology.getUpHeadArray());
        assertArrayEquals(downFirstOut, topology.getDownFirstOutArray());
        assertArrayEquals(downHead, topology.getDownHeadArray());
    }

    private static Fixture baseGraphFixture(int nodes, CCHNodeOrder order, EdgeSpec... edges) {
        BaseGraph baseGraph = baseGraph(nodes, Arrays.asList(edges));
        CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(baseGraph, new DistanceWeighting());
        return fixture(baseGraph, inputGraph, order);
    }

    private static Fixture manualFixture(int nodes, CCHNodeOrder order, List<CCHInputArc> arcs, List<EdgeSpec> baseEdges,
                                         CCHInputEdge... supportEdges) {
        return fixture(baseGraph(nodes, baseEdges), inputGraph(nodes, arcs, supportEdges), order);
    }

    private static Fixture fixture(BaseGraph baseGraph, CCHInputGraph inputGraph, CCHNodeOrder order) {
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, order);
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
        QueryGraph queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
        DefaultRoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, topology, metric, new DistanceWeighting());
        return new Fixture(baseGraph, inputGraph, topology, metric, new NodeBasedCCHQuery(topology, metric),
                new CCHPathUnpacker(topology, metric), new CCHPathCalculator(routingGraph, queryGraph));
    }

    private static BaseGraph baseGraph(int nodes, List<EdgeSpec> edges) {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        for (int node = 0; node < nodes; node++) {
            graph.getNodeAccess().setNode(node, node, node * 0.01);
        }
        for (int expectedEdgeId = 0; expectedEdgeId < edges.size(); expectedEdgeId++) {
            EdgeSpec edge = edges.get(expectedEdgeId);
            EdgeIteratorState edgeState = graph.edge(edge.from, edge.to).setDistance(edge.distance);
            assertEquals(expectedEdgeId, edgeState.getEdge());
        }
        return graph;
    }

    private static Path flexiblePath(Fixture fixture, int source, int target) {
        FlexiblePathCalculator calculator = new FlexiblePathCalculator(
                QueryGraph.create(fixture.baseGraph, Collections.emptyList()),
                new RoutingAlgorithmFactorySimple(),
                new DistanceWeighting(),
                new AlgorithmOptions()
                        .setAlgorithm(DIJKSTRA_BI)
                        .setTraversalMode(TraversalMode.NODE_BASED));
        return calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
    }

    private static String stableRoute(Path path) {
        if (!path.isFound())
            return "[found=false]";
        return "[found=true"
                + ", from=" + path.getFromNode()
                + ", to=" + path.getEndNode()
                + ", weight=" + path.getWeight()
                + ", time=" + path.getTime()
                + ", distance=" + path.getDistance()
                + ", edges=" + ints(path.getEdges())
                + ", nodes=" + ints(path.calcNodes())
                + ", points=" + points(path.calcPoints())
                + "]";
    }

    private static String ints(IntIndexedContainer values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0)
                builder.append(',');
            builder.append(values.get(i));
        }
        return builder.append(']').toString();
    }

    private static String points(PointList points) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0)
                builder.append(',');
            builder.append(points.getLat(i)).append(':').append(points.getLon(i));
        }
        return builder.append(']').toString();
    }

    private static void assertUnpackedEdges(CCHUnpackedPath path, EdgeStep... expectedEdges) {
        assertTrue(path.isFound());
        assertEquals(expectedEdges.length, path.getEdgeCount());
        List<CCHUnpackedEdge> edges = path.getEdges();
        double weight = 0;
        for (int i = 0; i < expectedEdges.length; i++) {
            EdgeStep expected = expectedEdges[i];
            CCHUnpackedEdge actual = edges.get(i);
            assertEquals(expected.baseEdge, actual.getBaseEdge());
            assertEquals(expected.reverse, actual.isReverse());
            assertEquals(expected.from, actual.getFrom());
            assertEquals(expected.to, actual.getTo());
            assertEquals(expected.weight, actual.getWeight(), 1.e-9);
            weight += expected.weight;
        }
        assertEquals(weight, path.getWeight(), 1.e-9);
    }

    private static CCHInputGraph inputGraph(int nodes, List<CCHInputArc> arcs, CCHInputEdge... supportEdges) {
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputArc inputArc(int from, int to, int baseEdge, boolean reverse, double weight, long millis, double distance) {
        return new CCHInputArc(from, to, baseEdge, reverse, weight, millis, distance);
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static EdgeSpec edge(int from, int to, double distance) {
        return new EdgeSpec(from, to, distance);
    }

    private static EdgeStep edgeStep(int baseEdge, boolean reverse, int from, int to, double weight) {
        return new EdgeStep(baseEdge, reverse, from, to, weight);
    }

    private static CCHTriangle triangle(CCHTriangleType type, int targetArc, int firstWitnessArc, int secondWitnessArc) {
        return new CCHTriangle(type, targetArc, firstWitnessArc, secondWitnessArc);
    }

    private static int arc(CCHTopology topology, int tail, int head) {
        for (int cchArc = 0; cchArc < topology.getArcs(); cchArc++) {
            if (topology.getTail(cchArc) == tail && topology.getHead(cchArc) == head)
                return cchArc;
        }
        throw new AssertionError("missing CCH arc " + tail + "->" + head);
    }

    private static final class Fixture {
        private final BaseGraph baseGraph;
        private final CCHInputGraph inputGraph;
        private final CCHTopology topology;
        private final CCHMetric metric;
        private final NodeBasedCCHQuery query;
        private final CCHPathUnpacker unpacker;
        private final CCHPathCalculator calculator;

        private Fixture(BaseGraph baseGraph, CCHInputGraph inputGraph, CCHTopology topology, CCHMetric metric,
                        NodeBasedCCHQuery query, CCHPathUnpacker unpacker, CCHPathCalculator calculator) {
            this.baseGraph = baseGraph;
            this.inputGraph = inputGraph;
            this.topology = topology;
            this.metric = metric;
            this.query = query;
            this.unpacker = unpacker;
            this.calculator = calculator;
        }
    }

    private static final class EdgeSpec {
        private final int from;
        private final int to;
        private final double distance;

        private EdgeSpec(int from, int to, double distance) {
            this.from = from;
            this.to = to;
            this.distance = distance;
        }
    }

    private static final class EdgeStep {
        private final int baseEdge;
        private final boolean reverse;
        private final int from;
        private final int to;
        private final double weight;

        private EdgeStep(int baseEdge, boolean reverse, int from, int to, double weight) {
            this.baseEdge = baseEdge;
            this.reverse = reverse;
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
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
}
