// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

class EdgeBasedCCHQueryTest {
    @Test
    void matchesFlexibleEdgeBasedDijkstraAndUnpacksTowerRoute() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        int detourA = graph.edge(0, 3).setDistance(50).getEdge();
        int detourB = graph.edge(3, 2).setDistance(50).getEdge();

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(second, false, 20, 200)
                .edge(detourA, false, 50, 500)
                .edge(detourB, false, 50, 500)
                .turn(first, 1, second, 5, 50)
                .turn(detourA, 3, detourB, 1, 10);
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));

        EdgeBasedCCHQueryResult result = fixture.query.calc(0, 2);
        FlexibleResult expected = flexible(graph, weighting, fixture.edgeGraph, 0, 2);
        CCHUnpackedPath unpacked = fixture.unpacker.unpack(result);

        assertTrue(result.isFound());
        assertEquals(expected.weight, result.getWeight(), 1.e-9);
        assertEquals(expected.edgeKeys, edgeKeys(unpacked));
        assertEquals(Arrays.asList(GHUtility.createEdgeKey(first, false), GHUtility.createEdgeKey(second, false)), edgeKeys(unpacked));
        assertEquals(35, unpacked.getWeight(), 1.e-9);
        assertEquals(350, unpacked.getMillis());
        assertEquals(30, unpacked.getDistance(), 1.e-9);
    }

    @Test
    void restrictedTurnChoosesLegalDetourAndNoAccessReturnsNoPath() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int blockedSecond = graph.edge(1, 2).setDistance(10).getEdge();
        int detourA = graph.edge(0, 3).setDistance(20).getEdge();
        int detourB = graph.edge(3, 2).setDistance(20).getEdge();
        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(blockedSecond, false, 10, 100)
                .edge(detourA, false, 20, 200)
                .edge(detourB, false, 20, 200)
                .turn(first, 1, blockedSecond, Double.POSITIVE_INFINITY, 0)
                .turn(detourA, 3, detourB, 1, 10);
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));

        CCHUnpackedPath unpacked = fixture.unpacker.unpack(fixture.query.calc(0, 2));
        assertEquals(Arrays.asList(GHUtility.createEdgeKey(detourA, false), GHUtility.createEdgeKey(detourB, false)), edgeKeys(unpacked));
        assertEquals(41, unpacked.getWeight(), 1.e-9);

        EdgeBasedCCHQueryResult noPath = fixture.query.calc(2, 0);
        assertFalse(noPath.isFound());
        assertFalse(fixture.unpacker.unpack(noPath).isFound());
    }

    @Test
    void shortcutCorePathUnpacksAllBaseEdgeKeysInTraversalOrder() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        int third = graph.edge(2, 3).setDistance(30).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        int secondState = state(edgeGraph, second, false);
        int firstState = state(edgeGraph, first, false);
        int thirdState = state(edgeGraph, third, false);
        CCHNodeOrder order = order(edgeGraph.getStates(), secondState, firstState, thirdState);
        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(second, false, 20, 200)
                .edge(third, false, 30, 300)
                .turn(first, 1, second, 5, 50)
                .turn(second, 2, third, 7, 70);
        Fixture fixture = fixture(graph, weighting, order);

        EdgeBasedCCHQueryResult result = fixture.query.calc(0, 3);
        CCHUnpackedPath unpacked = fixture.unpacker.unpack(result);

        assertTrue(result.isFound());
        assertEquals(Arrays.asList(
                GHUtility.createEdgeKey(first, false),
                GHUtility.createEdgeKey(second, false),
                GHUtility.createEdgeKey(third, false)), edgeKeys(unpacked));
        assertEquals(72, unpacked.getWeight(), 1.e-9);
        assertEquals(720, unpacked.getMillis());
        assertTrue(fixture.metric.isShortcut(fixture.edgeTopology.getTopology().findArc(firstState, thirdState)));
    }

    @Test
    void virtualEndpointsOnDifferentEdgesMatchFlexibleEdgeBasedDijkstra() {
        BaseGraph graph = lineGraph(3);
        EdgeIteratorState firstEdge = graph.edge(0, 1).setDistance(100);
        EdgeIteratorState secondEdge = graph.edge(1, 2).setDistance(200);
        TestWeighting weighting = new TestWeighting()
                .edge(firstEdge.getEdge(), false, 10, 100)
                .edge(secondEdge.getEdge(), false, 20, 200)
                .turn(firstEdge.getEdge(), 1, secondEdge.getEdge(), 5, 50);
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));
        Snap sourceSnap = snap(0, 0.25, firstEdge);
        Snap targetSnap = snap(0, 1.75, secondEdge);
        QueryGraph queryGraph = QueryGraph.create(graph, sourceSnap, targetSnap);

        EdgeBasedCCHQueryResult result = fixture.query.calc(queryGraph, sourceSnap.getClosestNode(), targetSnap.getClosestNode());
        Path cchPath = fixture.unpacker.toPath(queryGraph, result);
        Path flexiblePath = flexiblePath(queryGraph, weighting, sourceSnap.getClosestNode(), targetSnap.getClosestNode());

        assertStablePathFieldsEqual(flexiblePath, cchPath);
        assertTrue(result.isFound());
        assertNotNull(result.getSourceBoundaryArc());
        assertNotNull(result.getTargetBoundaryArc());
        assertEquals(35, result.getWeight(), 1.e-9);
    }

    @Test
    void virtualEndpointsOnSameSplitEdgeUseDirectBoundarySegment() {
        BaseGraph graph = lineGraph(2);
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(100);
        TestWeighting weighting = new TestWeighting()
                .edge(edge.getEdge(), false, 10, 100)
                .edge(edge.getEdge(), true, 10, 100);
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));
        Snap sourceSnap = snap(0, 0.25, edge);
        Snap targetSnap = snap(0, 0.75, edge);
        QueryGraph queryGraph = QueryGraph.create(graph, sourceSnap, targetSnap);

        EdgeBasedCCHQueryResult result = fixture.query.calc(queryGraph, sourceSnap.getClosestNode(), targetSnap.getClosestNode());
        Path cchPath = fixture.unpacker.toPath(queryGraph, result);
        Path flexiblePath = flexiblePath(queryGraph, weighting, sourceSnap.getClosestNode(), targetSnap.getClosestNode());

        assertStablePathFieldsEqual(flexiblePath, cchPath);
        assertTrue(result.isFound());
        assertEquals(1, cchPath.getEdgeCount());
        assertTrue(queryGraph.isVirtualEdge(cchPath.getEdges().get(0)));
        assertNotNull(result.getSourceBoundaryArc());
        assertNull(result.getCoreResult());
    }

    @Test
    void restrictedTurnBeforeVirtualTargetMatchesFlexibleEdgeBasedDijkstra() {
        BaseGraph graph = lineGraph(4);
        EdgeIteratorState firstEdge = graph.edge(0, 1).setDistance(100);
        EdgeIteratorState blockedTargetEdge = graph.edge(1, 2).setDistance(200);
        EdgeIteratorState detourA = graph.edge(1, 3).setDistance(300);
        EdgeIteratorState detourB = graph.edge(3, 2).setDistance(300);
        TestWeighting weighting = new TestWeighting()
                .edge(firstEdge.getEdge(), false, 10, 100)
                .edge(blockedTargetEdge.getEdge(), false, 20, 200)
                .edge(blockedTargetEdge.getEdge(), true, 20, 200)
                .edge(detourA.getEdge(), false, 30, 300)
                .edge(detourB.getEdge(), false, 30, 300)
                .turn(firstEdge.getEdge(), 1, blockedTargetEdge.getEdge(), Double.POSITIVE_INFINITY, 0)
                .turn(firstEdge.getEdge(), 1, detourA.getEdge(), 1, 10)
                .turn(detourA.getEdge(), 3, detourB.getEdge(), 1, 10)
                .turn(detourB.getEdge(), 2, blockedTargetEdge.getEdge(), 1, 10);
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));
        Snap sourceSnap = snap(0, 0.25, firstEdge);
        Snap targetSnap = snap(0, 1.75, blockedTargetEdge);
        QueryGraph queryGraph = QueryGraph.create(graph, sourceSnap, targetSnap);

        EdgeBasedCCHQueryResult result = fixture.query.calc(queryGraph, sourceSnap.getClosestNode(), targetSnap.getClosestNode());
        Path cchPath = fixture.unpacker.toPath(queryGraph, result);
        Path flexiblePath = flexiblePath(queryGraph, weighting, sourceSnap.getClosestNode(), targetSnap.getClosestNode());

        assertStablePathFieldsEqual(flexiblePath, cchPath);
        assertTrue(result.isFound());
        assertEquals(Arrays.asList(
                sourceBoundaryEdgeKey(result),
                GHUtility.createEdgeKey(detourA.getEdge(), false),
                GHUtility.createEdgeKey(detourB.getEdge(), false),
                targetBoundaryEdgeKey(result)), edgeKeys(fixture.unpacker.unpack(result)));
        assertEquals(93, result.getWeight(), 1.e-9);
    }

    @Test
    void sourceEqualsTargetReturnsFoundEmptyPath() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);
        TestWeighting weighting = new TestWeighting();
        Fixture fixture = fixture(graph, weighting, CCHNodeOrder.identity(EdgeStateCCHInputBuilder.fromGraph(graph).getStates()));

        EdgeBasedCCHQueryResult result = fixture.query.calc(0, 0);
        CCHUnpackedPath unpacked = fixture.unpacker.unpack(result);

        assertTrue(result.isFound());
        assertEquals(0, result.getWeight(), 1.e-9);
        assertTrue(unpacked.isFound());
        assertEquals(0, unpacked.getEdgeCount());
    }

    private static Fixture fixture(BaseGraph graph, TestWeighting weighting, CCHNodeOrder order) {
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, order);
        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology());
        CCHMetric metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology, source);
        return new Fixture(edgeGraph, edgeTopology, metric,
                new EdgeBasedCCHQuery(graph, weighting, edgeTopology, metric),
                new EdgeBasedCCHPathUnpacker(edgeTopology, metric));
    }

    private static BaseGraph lineGraph(int nodes) {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        for (int node = 0; node < nodes; node++) {
            graph.getNodeAccess().setNode(node, 0, node);
        }
        return graph;
    }

    private static Snap snap(double lat, double lon, EdgeIteratorState edge) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(new DistanceCalcEarth());
        return snap;
    }

    private static Path flexiblePath(QueryGraph queryGraph, Weighting weighting, int source, int target) {
        FlexiblePathCalculator calculator = new FlexiblePathCalculator(
                queryGraph,
                new RoutingAlgorithmFactorySimple(),
                weighting,
                new AlgorithmOptions()
                        .setAlgorithm(DIJKSTRA_BI)
                        .setTraversalMode(TraversalMode.EDGE_BASED));
        return calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
    }

    private static void assertStablePathFieldsEqual(Path expected, Path actual) {
        assertEquals(expected.isFound(), actual.isFound());
        if (!expected.isFound()) {
            assertFalse(actual.isFound());
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

    private static int sourceBoundaryEdgeKey(EdgeBasedCCHQueryResult result) {
        assertNotNull(result.getSourceBoundaryArc());
        return result.getSourceBoundaryArc().getEdgeKey();
    }

    private static int targetBoundaryEdgeKey(EdgeBasedCCHQueryResult result) {
        assertNotNull(result.getTargetBoundaryArc());
        return result.getTargetBoundaryArc().getEdgeKey();
    }

    private static List<Integer> edgeKeys(CCHUnpackedPath unpacked) {
        List<Integer> edgeKeys = new ArrayList<>();
        for (CCHUnpackedEdge edge : unpacked.getEdges()) {
            edgeKeys.add(edge.getEdgeKey());
        }
        return edgeKeys;
    }

    private static int state(EdgeStateCCHInputGraph edgeGraph, int edge, boolean reverse) {
        return edgeGraph.getStateForEdgeKey(GHUtility.createEdgeKey(edge, reverse));
    }

    private static CCHNodeOrder order(int nodes, int... firstNodes) {
        int[] order = new int[nodes];
        boolean[] used = new boolean[nodes];
        int index = 0;
        for (int node : firstNodes) {
            if (!used[node]) {
                order[index++] = node;
                used[node] = true;
            }
        }
        for (int node = 0; node < nodes; node++) {
            if (!used[node])
                order[index++] = node;
        }
        return CCHNodeOrder.fromOrder(order);
    }

    private static FlexibleResult flexible(BaseGraph graph, Weighting weighting, EdgeStateCCHInputGraph edgeGraph,
                                           int sourceNode, int targetNode) {
        double[] dist = new double[edgeGraph.getStates()];
        long[] millis = new long[edgeGraph.getStates()];
        int[] prev = new int[edgeGraph.getStates()];
        boolean[] settled = new boolean[edgeGraph.getStates()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(millis, Long.MAX_VALUE);
        Arrays.fill(prev, EdgeStateCCHInputGraph.NO_STATE);

        for (int state = 0; state < edgeGraph.getStates(); state++) {
            if (edgeGraph.getStateTailNode(state) != sourceNode)
                continue;
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeGraph.getStateEdgeKey(state));
            double weight = weighting.calcEdgeWeight(edge, false);
            if (!Double.isFinite(weight))
                continue;
            dist[state] = weight;
            millis[state] = weighting.calcEdgeMillis(edge, false);
        }

        while (true) {
            int state = unsettledState(dist, settled);
            if (state < 0)
                break;
            settled[state] = true;
            for (int inputArc = 0; inputArc < edgeGraph.getInputGraph().getArcs(); inputArc++) {
                CCHInputArc arc = edgeGraph.getInputGraph().getArc(inputArc);
                if (arc.getFrom() != state)
                    continue;
                int outEdgeKey = edgeGraph.getTransitionOutEdgeKey(inputArc);
                EdgeIteratorState outEdge = graph.getEdgeIteratorStateForKey(outEdgeKey);
                double edgeWeight = weighting.calcEdgeWeight(outEdge, false);
                double turnWeight = weighting.calcTurnWeight(GHUtility.getEdgeFromEdgeKey(edgeGraph.getTransitionInEdgeKey(inputArc)),
                        edgeGraph.getTransitionViaNode(inputArc), GHUtility.getEdgeFromEdgeKey(outEdgeKey));
                if (!Double.isFinite(edgeWeight) || !Double.isFinite(turnWeight))
                    continue;
                double nextWeight = dist[state] + edgeWeight + turnWeight;
                int next = arc.getTo();
                if (nextWeight < dist[next]) {
                    dist[next] = nextWeight;
                    millis[next] = millis[state] + weighting.calcEdgeMillis(outEdge, false)
                            + weighting.calcTurnMillis(GHUtility.getEdgeFromEdgeKey(edgeGraph.getTransitionInEdgeKey(inputArc)),
                            edgeGraph.getTransitionViaNode(inputArc), GHUtility.getEdgeFromEdgeKey(outEdgeKey));
                    prev[next] = state;
                }
            }
        }

        int best = EdgeStateCCHInputGraph.NO_STATE;
        double bestWeight = Double.POSITIVE_INFINITY;
        for (int state = 0; state < edgeGraph.getStates(); state++) {
            if (edgeGraph.getStateHeadNode(state) == targetNode && dist[state] < bestWeight) {
                best = state;
                bestWeight = dist[state];
            }
        }
        if (best == EdgeStateCCHInputGraph.NO_STATE)
            return new FlexibleResult(Double.POSITIVE_INFINITY, new ArrayList<>());

        List<Integer> edgeKeys = new ArrayList<>();
        int state = best;
        while (state != EdgeStateCCHInputGraph.NO_STATE) {
            edgeKeys.add(0, edgeGraph.getStateEdgeKey(state));
            state = prev[state];
        }
        return new FlexibleResult(bestWeight, edgeKeys);
    }

    private static int unsettledState(double[] dist, boolean[] settled) {
        int bestState = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int state = 0; state < dist.length; state++) {
            if (!settled[state] && dist[state] < bestDistance) {
                bestState = state;
                bestDistance = dist[state];
            }
        }
        return bestState;
    }

    private static final class FlexibleResult {
        private final double weight;
        private final List<Integer> edgeKeys;

        private FlexibleResult(double weight, List<Integer> edgeKeys) {
            this.weight = weight;
            this.edgeKeys = edgeKeys;
        }
    }

    private static final class Fixture {
        private final EdgeStateCCHInputGraph edgeGraph;
        private final EdgeStateCCHTopology edgeTopology;
        private final CCHMetric metric;
        private final EdgeBasedCCHQuery query;
        private final EdgeBasedCCHPathUnpacker unpacker;

        private Fixture(EdgeStateCCHInputGraph edgeGraph, EdgeStateCCHTopology edgeTopology, CCHMetric metric,
                        EdgeBasedCCHQuery query, EdgeBasedCCHPathUnpacker unpacker) {
            this.edgeGraph = edgeGraph;
            this.edgeTopology = edgeTopology;
            this.metric = metric;
            this.query = query;
            this.unpacker = unpacker;
        }
    }

    private static final class TestWeighting implements Weighting {
        private final Map<Integer, EdgeValue> edgeValues = new HashMap<>();
        private final Map<TurnKey, TurnValue> turnValues = new HashMap<>();

        TestWeighting edge(int edge, boolean reverse, double weight, long millis) {
            edgeValues.put(GHUtility.createEdgeKey(edge, reverse), new EdgeValue(weight, millis));
            return this;
        }

        TestWeighting turn(int inEdge, int viaNode, int outEdge, double weight, long millis) {
            turnValues.put(new TurnKey(inEdge, viaNode, outEdge), new TurnValue(weight, millis));
            return this;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return edgeValue(edgeState, reverse).weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return edgeValue(edgeState, reverse).millis;
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return turnValues.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).weight;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return turnValues.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).millis;
        }

        @Override
        public boolean hasTurnCosts() {
            return true;
        }

        @Override
        public String getName() {
            return "edge_based_query_test";
        }

        private EdgeValue edgeValue(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = originalEdgeKey(edgeState.detach(false), reverse);
            return edgeValues.getOrDefault(edgeKey, EdgeValue.INACCESSIBLE);
        }

        private static int originalEdgeKey(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = edgeState instanceof VirtualEdgeIteratorState
                    ? ((VirtualEdgeIteratorState) edgeState).getOriginalEdgeKey()
                    : edgeState.getEdgeKey();
            return reverse ? GHUtility.reverseEdgeKey(edgeKey) : edgeKey;
        }
    }

    private static final class EdgeValue {
        private static final EdgeValue INACCESSIBLE = new EdgeValue(Double.POSITIVE_INFINITY, 0);

        private final double weight;
        private final long millis;

        private EdgeValue(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }
    }

    private static final class TurnValue {
        private static final TurnValue ZERO = new TurnValue(0, 0);

        private final double weight;
        private final long millis;

        private TurnValue(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }
    }

    private static final class TurnKey {
        private final int inEdge;
        private final int viaNode;
        private final int outEdge;

        private TurnKey(int inEdge, int viaNode, int outEdge) {
            this.inEdge = inEdge;
            this.viaNode = viaNode;
            this.outEdge = outEdge;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TurnKey))
                return false;
            TurnKey key = (TurnKey) o;
            return inEdge == key.inEdge && viaNode == key.viaNode && outEdge == key.outEdge;
        }

        @Override
        public int hashCode() {
            int result = inEdge;
            result = 31 * result + viaNode;
            result = 31 * result + outEdge;
            return result;
        }
    }
}
