// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.FlexiblePathCalculator;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.RoutingCHGraphImpl;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.jupiter.api.Assertions.*;

class EdgeBasedCCHTurnCostCorrectnessTest {
    private static final double EPSILON = 1.e-7;

    @Test
    void turnRestrictionRouteMatchesFlexibleChAndCchForTowerAndVirtualEndpoints() {
        GHFixture fixture = GHFixture.createTurnRestrictionFixture();

        assertAllModesEqual(fixture, QueryGraph.create(fixture.graph, new ArrayList<>()), 0, 2);

        Snap sourceSnap = snap(0, 0.25, fixture.edge(0));
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, sourceSnap);
        assertAllModesEqual(fixture, queryGraph, sourceSnap.getClosestNode(), 2);
    }

    @Test
    void oneWayTurnRestrictionAndDisconnectedCasesMatchFlexibleDijkstra() {
        RandomFixture oneWay = new RandomFixtureBuilder(5, CCHNodeOrder.identity(10))
                .edge(0, 1, 10, 10, Double.POSITIVE_INFINITY)
                .edge(1, 2, 10, 10, 10)
                .edge(1, 3, 10, 20, 20)
                .edge(3, 2, 10, 20, 20)
                .turn(0, 1, 1, Double.POSITIVE_INFINITY, 0)
                .turn(0, 1, 2, 1, 10)
                .turn(2, 3, 3, 1, 10)
                .build();
        assertMatchesFlexible(oneWay, 0, 2);
        assertMatchesFlexible(oneWay, 2, 0);
        assertMatchesFlexible(oneWay, 0, 4);
    }

    @Test
    void randomizedTurnCostRoutesMatchFlexibleDijkstraWeightsAndProduceLegalPaths() {
        for (int seed = 0; seed < 50; seed++) {
            RandomFixture fixture = randomFixture(seed);
            for (int source = 0; source < fixture.nodes; source++) {
                for (int target = 0; target < fixture.nodes; target++) {
                    Path flexiblePath = fixture.flexiblePath(QueryGraph.create(fixture.graph, new ArrayList<>()), source, target);
                    EdgeBasedCCHQueryResult result = fixture.query.calc(source, target);
                    CCHUnpackedPath unpacked = fixture.unpacker.unpack(result);
                    Path cchPath = fixture.unpacker.toPath(fixture.graph, result);
                    String context = fixture.context(seed, source, target, flexiblePath, cchPath);

                    assertEquals(flexiblePath.isFound(), result.isFound(), context);
                    assertEquals(flexiblePath.isFound(), cchPath.isFound(), context);
                    assertEquals(flexiblePath.isFound(), unpacked.isFound(), context);
                    if (!flexiblePath.isFound())
                        continue;

                    assertEquals(flexiblePath.getWeight(), result.getWeight(), EPSILON, context);
                    assertEquals(flexiblePath.getWeight(), cchPath.getWeight(), EPSILON, context);
                    assertLegalPath(fixture, source, target, cchPath, unpacked, context);
                }
            }
        }
    }

    private static void assertAllModesEqual(GHFixture fixture, QueryGraph queryGraph, int source, int target) {
        Path flexible = fixture.flexiblePath(queryGraph, source, target);
        Path ch = fixture.chPath(queryGraph, source, target);
        Path cch = fixture.cchPath(queryGraph, source, target);
        assertStablePathFieldsEqual(flexible, ch, "flexible vs CH");
        assertStablePathFieldsEqual(flexible, cch, "flexible vs CCH");
    }

    private static void assertMatchesFlexible(RandomFixture fixture, int source, int target) {
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, new ArrayList<>());
        Path flexible = fixture.flexiblePath(queryGraph, source, target);
        Path cch = fixture.unpacker.toPath(fixture.graph, fixture.query.calc(source, target));
        assertStablePathFieldsEqual(flexible, cch, fixture.context(-1, source, target, flexible, cch));
    }

    private static void assertLegalPath(RandomFixture fixture, int source, int target, Path cchPath,
                                        CCHUnpackedPath unpacked, String context) {
        assertTrue(cchPath.isFound(), context);
        assertEquals(source, cchPath.getFromNode(), context);
        assertEquals(target, cchPath.getEndNode(), context);
        assertEquals(source, unpacked.getSource(), context);
        assertEquals(target, unpacked.getTarget(), context);

        IntIndexedContainer nodes = cchPath.calcNodes();
        assertEquals(source, nodes.get(0), context);
        assertEquals(target, nodes.get(nodes.size() - 1), context);
        assertEquals(cchPath.getEdgeCount(), unpacked.getEdgeCount(), context);

        double weight = 0;
        long millis = 0;
        double distance = 0;
        int previousEdge = -1;
        for (int i = 0; i < unpacked.getEdges().size(); i++) {
            CCHUnpackedEdge edge = unpacked.getEdges().get(i);
            assertEquals(nodes.get(i), edge.getFrom(), context);
            assertEquals(nodes.get(i + 1), edge.getTo(), context);
            EdgeIteratorState edgeState = fixture.graph.getEdgeIteratorState(edge.getBaseEdge(), edge.getTo());
            assertNotNull(edgeState, context);
            double edgeWeight = fixture.weighting.calcEdgeWeight(edgeState, false);
            double turnWeight = previousEdge < 0 ? 0 : fixture.weighting.calcTurnWeight(previousEdge, edge.getFrom(), edge.getBaseEdge());
            assertTrue(Double.isFinite(edgeWeight), context);
            assertTrue(Double.isFinite(turnWeight), context);
            weight += edgeWeight + turnWeight;
            millis += fixture.weighting.calcEdgeMillis(edgeState, false)
                    + (previousEdge < 0 ? 0 : fixture.weighting.calcTurnMillis(previousEdge, edge.getFrom(), edge.getBaseEdge()));
            distance += edgeState.getDistance();
            previousEdge = edge.getBaseEdge();
        }
        assertEquals(weight, cchPath.getWeight(), EPSILON, context);
        assertEquals(weight, unpacked.getWeight(), EPSILON, context);
        assertEquals(millis, cchPath.getTime(), context);
        assertEquals(distance, cchPath.getDistance(), EPSILON, context);
    }

    private static void assertStablePathFieldsEqual(Path expected, Path actual, String context) {
        assertEquals(expected.isFound(), actual.isFound(), context);
        if (!expected.isFound())
            return;
        assertEquals(expected.getFromNode(), actual.getFromNode(), context);
        assertEquals(expected.getEndNode(), actual.getEndNode(), context);
        assertEquals(expected.getWeight(), actual.getWeight(), EPSILON, context);
        assertEquals(expected.getTime(), actual.getTime(), context);
        assertEquals(expected.getDistance(), actual.getDistance(), EPSILON, context);
        assertEquals(expected.getEdges(), actual.getEdges(), context);
        assertEquals(expected.calcNodes(), actual.calcNodes(), context);
    }

    private static RandomFixture randomFixture(int seed) {
        Random random = new Random(seed);
        int nodes = 2 + random.nextInt(6);
        RandomFixtureBuilder builder = new RandomFixtureBuilder(nodes, randomOrder(nodes * 2, seed + 10_000));
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 1; b < nodes; b++) {
                if (random.nextInt(100) >= 45)
                    continue;
                int edge = builder.edgeCount();
                double forwardWeight = random.nextInt(100) < 20 ? Double.POSITIVE_INFINITY : 1 + random.nextInt(50);
                double reverseWeight = random.nextInt(100) < 20 ? Double.POSITIVE_INFINITY : 1 + random.nextInt(50);
                if (!Double.isFinite(forwardWeight) && !Double.isFinite(reverseWeight))
                    forwardWeight = 1 + random.nextInt(50);
                builder.edge(a, b, 5 + random.nextInt(80), forwardWeight, reverseWeight);
                if (edge > 0 && random.nextInt(100) < 35) {
                    int via = random.nextBoolean() ? a : b;
                    int in = random.nextInt(edge);
                    int out = edge;
                    builder.turn(in, via, out, random.nextInt(100) < 65 ? Double.POSITIVE_INFINITY : random.nextInt(10), random.nextInt(100));
                }
            }
        }
        return builder.build();
    }

    private static CCHNodeOrder randomOrder(int nodes, int seed) {
        int[] order = new int[nodes];
        for (int i = 0; i < nodes; i++) {
            order[i] = i;
        }
        Random random = new Random(seed);
        for (int i = nodes - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[swap];
            order[swap] = tmp;
        }
        return CCHNodeOrder.fromOrder(order);
    }

    private static Snap snap(double lat, double lon, EdgeIteratorState edge) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(new DistanceCalcEarth());
        return snap;
    }

    private static String stableRoute(Path path) {
        if (!path.isFound())
            return "[found=false]";
        return "[found=true, weight=" + path.getWeight()
                + ", time=" + path.getTime()
                + ", distance=" + path.getDistance()
                + ", edges=" + ints(path.getEdges())
                + ", nodes=" + ints(path.calcNodes())
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

    private static final class GHFixture {
        private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", 100);
        private final EncodingManager encodingManager = EncodingManager.start()
                .add(speedEnc)
                .addTurnCostEncodedValue(turnCostEnc)
                .build();
        private final BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        private final List<EdgeIteratorState> edges = new ArrayList<>();
        private SpeedWeighting weighting;
        private RoutingCHGraph routingCHGraph;
        private EdgeStateCCHTopology edgeTopology;
        private CCHMetric metric;

        private static GHFixture createTurnRestrictionFixture() {
            GHFixture fixture = new GHFixture();
            for (int node = 0; node < 4; node++) {
                fixture.graph.getNodeAccess().setNode(node, 0, node);
            }
            EdgeIteratorState first = fixture.edge(0, 1, 100, 10, 10);
            EdgeIteratorState blocked = fixture.edge(1, 2, 100, 10, 10);
            EdgeIteratorState detourA = fixture.edge(1, 3, 150, 10, 10);
            EdgeIteratorState detourB = fixture.edge(3, 2, 150, 10, 10);
            fixture.restrict(first, 1, blocked);
            fixture.cost(first, 1, detourA, 1);
            fixture.cost(detourA, 3, detourB, 1);
            fixture.prepare(Double.POSITIVE_INFINITY);
            return fixture;
        }

        private EdgeIteratorState edge(int from, int to, double distance, double forwardSpeed, double reverseSpeed) {
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance).set(speedEnc, forwardSpeed, reverseSpeed);
            edges.add(edge);
            return edge;
        }

        private EdgeIteratorState edge(int index) {
            return edges.get(index);
        }

        private void restrict(EdgeIteratorState inEdge, int viaNode, EdgeIteratorState outEdge) {
            cost(inEdge, viaNode, outEdge, Double.POSITIVE_INFINITY);
        }

        private void cost(EdgeIteratorState inEdge, int viaNode, EdgeIteratorState outEdge, double cost) {
            graph.getTurnCostStorage().set(turnCostEnc, inEdge.getEdge(), viaNode, outEdge.getEdge(), cost);
        }

        private void prepare(double uTurnCosts) {
            graph.freeze();
            weighting = new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), uTurnCosts);
            CHConfig chConfig = CHConfig.edgeBased("p", weighting);
            PrepareContractionHierarchies.Result res = PrepareContractionHierarchies.fromGraph(graph, chConfig).doWork();
            routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
            edgeTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));
            metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology,
                    new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology()));
        }

        private Path flexiblePath(QueryGraph queryGraph, int source, int target) {
            return new FlexiblePathCalculator(queryGraph, new RoutingAlgorithmFactorySimple(), weighting,
                    new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI).setTraversalMode(TraversalMode.EDGE_BASED))
                    .calcPaths(source, target, new EdgeRestrictions()).get(0);
        }

        private Path chPath(QueryGraph queryGraph, int source, int target) {
            CHRoutingAlgorithmFactory factory = queryGraph.getNodes() == graph.getNodes()
                    ? new CHRoutingAlgorithmFactory(routingCHGraph)
                    : new CHRoutingAlgorithmFactory(new QueryRoutingCHGraph(routingCHGraph, queryGraph));
            return factory.createAlgo(new PMap().putObject(ALGORITHM, DIJKSTRA_BI)).calcPaths(source, target).get(0);
        }

        private Path cchPath(QueryGraph queryGraph, int source, int target) {
            EdgeBasedCCHQuery query = new EdgeBasedCCHQuery(graph, weighting, edgeTopology, metric);
            EdgeBasedCCHQueryResult result = queryGraph.getNodes() == graph.getNodes()
                    ? query.calc(source, target)
                    : query.calc(queryGraph, source, target);
            return new EdgeBasedCCHPathUnpacker(edgeTopology, metric).toPath(queryGraph, result);
        }
    }

    private static final class RandomFixtureBuilder {
        private final int nodes;
        private final CCHNodeOrder order;
        private final BaseGraph graph = new BaseGraph.Builder(1).create();
        private final List<EdgeSpec> edges = new ArrayList<>();
        private final Map<TurnKey, TurnValue> turns = new HashMap<>();

        private RandomFixtureBuilder(int nodes, CCHNodeOrder order) {
            this.nodes = nodes;
            this.order = order;
            for (int node = 0; node < nodes; node++) {
                graph.getNodeAccess().setNode(node, node, node * 0.01);
            }
        }

        private int edgeCount() {
            return edges.size();
        }

        private RandomFixtureBuilder edge(int from, int to, double distance, double forwardWeight, double reverseWeight) {
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance);
            edges.add(new EdgeSpec(edge.getEdge(), from, to, distance, forwardWeight, reverseWeight));
            return this;
        }

        private RandomFixtureBuilder turn(int inEdge, int viaNode, int outEdge, double weight, long millis) {
            turns.put(new TurnKey(inEdge, viaNode, outEdge), new TurnValue(weight, millis));
            return this;
        }

        private RandomFixture build() {
            TurnCostWeighting weighting = new TurnCostWeighting(edges, turns);
            EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
            CCHNodeOrder actualOrder = order.getNodes() == edgeGraph.getStates() ? order : CCHNodeOrder.identity(edgeGraph.getStates());
            EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, actualOrder);
            CCHMetric metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology,
                    new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology()));
            return new RandomFixture(nodes, graph, edges, turns, weighting,
                    new EdgeBasedCCHQuery(graph, weighting, edgeTopology, metric),
                    new EdgeBasedCCHPathUnpacker(edgeTopology, metric));
        }
    }

    private static final class RandomFixture {
        private final int nodes;
        private final BaseGraph graph;
        private final List<EdgeSpec> edges;
        private final Map<TurnKey, TurnValue> turns;
        private final TurnCostWeighting weighting;
        private final EdgeBasedCCHQuery query;
        private final EdgeBasedCCHPathUnpacker unpacker;

        private RandomFixture(int nodes, BaseGraph graph, List<EdgeSpec> edges, Map<TurnKey, TurnValue> turns,
                              TurnCostWeighting weighting, EdgeBasedCCHQuery query,
                              EdgeBasedCCHPathUnpacker unpacker) {
            this.nodes = nodes;
            this.graph = graph;
            this.edges = edges;
            this.turns = turns;
            this.weighting = weighting;
            this.query = query;
            this.unpacker = unpacker;
        }

        private Path flexiblePath(QueryGraph queryGraph, int source, int target) {
            return new FlexiblePathCalculator(queryGraph, new RoutingAlgorithmFactorySimple(), weighting,
                    new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI).setTraversalMode(TraversalMode.EDGE_BASED))
                    .calcPaths(source, target, new EdgeRestrictions()).get(0);
        }

        private String context(int seed, int source, int target, Path flexible, Path cch) {
            return "seed=" + seed
                    + ", source=" + source
                    + ", target=" + target
                    + "\nedges=" + edges
                    + "\nturns=" + turns
                    + "\nflexible=" + stableRoute(flexible)
                    + "\ncch=" + stableRoute(cch);
        }
    }

    private static final class TurnCostWeighting implements Weighting {
        private final List<EdgeSpec> edges;
        private final Map<TurnKey, TurnValue> turns;

        private TurnCostWeighting(List<EdgeSpec> edges, Map<TurnKey, TurnValue> turns) {
            this.edges = edges;
            this.turns = turns;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            EdgeIteratorState detached = edgeState.detach(false);
            int edgeKey = edgeKey(detached, reverse);
            EdgeSpec spec = edges.get(GHUtility.getEdgeFromEdgeKey(edgeKey));
            return (edgeKey & 1) == 1 ? spec.reverseWeight : spec.forwardWeight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            double weight = calcEdgeWeight(edgeState, reverse);
            return Double.isFinite(weight) ? Math.round(weight * 10) : 0;
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return turns.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).weight;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return turns.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).millis;
        }

        @Override
        public boolean hasTurnCosts() {
            return true;
        }

        @Override
        public String getName() {
            return "turn_cost_random";
        }

        private static int edgeKey(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = edgeState instanceof VirtualEdgeIteratorState
                    ? ((VirtualEdgeIteratorState) edgeState).getOriginalEdgeKey()
                    : edgeState.getEdgeKey();
            return reverse ? GHUtility.reverseEdgeKey(edgeKey) : edgeKey;
        }
    }

    private static final class EdgeSpec {
        private final int edge;
        private final int from;
        private final int to;
        private final double distance;
        private final double forwardWeight;
        private final double reverseWeight;

        private EdgeSpec(int edge, int from, int to, double distance, double forwardWeight, double reverseWeight) {
            this.edge = edge;
            this.from = from;
            this.to = to;
            this.distance = distance;
            this.forwardWeight = forwardWeight;
            this.reverseWeight = reverseWeight;
        }

        @Override
        public String toString() {
            return edge + ":" + from + "->" + to + " f=" + forwardWeight + " r=" + reverseWeight + " d=" + distance;
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

        @Override
        public String toString() {
            return weight + "/" + millis;
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

        @Override
        public String toString() {
            return inEdge + "@" + viaNode + "->" + outEdge;
        }
    }
}
