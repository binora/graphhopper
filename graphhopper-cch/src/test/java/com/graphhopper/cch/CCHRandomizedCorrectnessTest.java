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
import java.util.Random;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

class CCHRandomizedCorrectnessTest {
    private static final double EPSILON = 1.e-9;

    @Test
    void randomizedRoutesMatchFlexibleDijkstraWeightsAndProduceLegalPaths() {
        for (int seed = 0; seed < 60; seed++) {
            Fixture fixture = randomFixture(seed);
            for (int source = 0; source < fixture.nodes; source++) {
                for (int target = 0; target < fixture.nodes; target++) {
                    Path flexiblePath = fixture.flexiblePath(source, target);
                    CCHQueryResult queryResult = fixture.query.calc(source, target);
                    CCHUnpackedPath unpackedPath = fixture.unpacker.unpack(queryResult);
                    Path cchPath = fixture.cchPath(source, target);

                    String context = fixture.context(seed, source, target, flexiblePath, cchPath);
                    assertEquals(flexiblePath.isFound(), queryResult.isFound(), context);
                    assertEquals(flexiblePath.isFound(), unpackedPath.isFound(), context);
                    assertEquals(flexiblePath.isFound(), cchPath.isFound(), context);
                    if (!flexiblePath.isFound()) {
                        assertTrue(Double.isInfinite(queryResult.getWeight()), context);
                        continue;
                    }

                    assertEquals(flexiblePath.getWeight(), queryResult.getWeight(), EPSILON, context);
                    assertEquals(queryResult.getWeight(), unpackedPath.getWeight(), EPSILON, context);
                    assertEquals(queryResult.getWeight(), cchPath.getWeight(), EPSILON, context);
                    assertLegalPath(fixture, source, target, cchPath, unpackedPath, context);
                }
            }
        }
    }

    @Test
    void uniqueShortestRandomizedRoutesAreByteIdenticalToFlexibleDijkstra() {
        for (int seed = 0; seed < 20; seed++) {
            Fixture fixture = uniqueShortestFixture(seed);
            for (int source = 0; source < fixture.nodes; source++) {
                for (int target = 0; target < fixture.nodes; target++) {
                    Path flexiblePath = fixture.flexiblePath(source, target);
                    Path cchPath = fixture.cchPath(source, target);
                    assertEquals(stableRoute(flexiblePath), stableRoute(cchPath),
                            fixture.context(seed, source, target, flexiblePath, cchPath));
                }
            }
        }
    }

    private static void assertLegalPath(Fixture fixture, int source, int target, Path cchPath,
                                        CCHUnpackedPath unpackedPath, String context) {
        assertTrue(cchPath.isFound(), context);
        assertTrue(unpackedPath.isFound(), context);
        assertEquals(source, cchPath.getFromNode(), context);
        assertEquals(target, cchPath.getEndNode(), context);
        assertEquals(source, unpackedPath.getSource(), context);
        assertEquals(target, unpackedPath.getTarget(), context);

        IntIndexedContainer edgeIds = cchPath.getEdges();
        IntIndexedContainer pathNodes = cchPath.calcNodes();
        assertEquals(edgeIds.size() + 1, pathNodes.size(), context);
        assertEquals(source, pathNodes.get(0), context);
        assertEquals(target, pathNodes.get(pathNodes.size() - 1), context);
        assertEquals(edgeIds.size(), unpackedPath.getEdgeCount(), context);

        double weight = 0;
        long millis = 0;
        double distance = 0;
        List<CCHUnpackedEdge> unpackedEdges = unpackedPath.getEdges();
        for (int i = 0; i < edgeIds.size(); i++) {
            int edgeId = edgeIds.get(i);
            int from = pathNodes.get(i);
            int to = pathNodes.get(i + 1);
            EdgeIteratorState edgeState = fixture.baseGraph.getEdgeIteratorState(edgeId, to);
            assertNotNull(edgeState, context);
            assertEquals(from, edgeState.getBaseNode(), context);
            assertEquals(to, edgeState.getAdjNode(), context);

            EdgeSpec edgeSpec = fixture.edge(edgeId);
            double edgeWeight = edgeSpec.weight(from, to);
            long edgeMillis = edgeSpec.millis(from, to);
            assertTrue(Double.isFinite(edgeWeight), context);

            CCHUnpackedEdge unpackedEdge = unpackedEdges.get(i);
            assertEquals(edgeId, unpackedEdge.getBaseEdge(), context);
            assertEquals(from, unpackedEdge.getFrom(), context);
            assertEquals(to, unpackedEdge.getTo(), context);
            assertEquals(edgeSpec.isReverse(from, to), unpackedEdge.isReverse(), context);
            assertEquals(edgeWeight, unpackedEdge.getWeight(), EPSILON, context);
            assertEquals(edgeMillis, unpackedEdge.getMillis(), context);
            assertEquals(edgeSpec.distance, unpackedEdge.getDistance(), EPSILON, context);

            weight += edgeWeight;
            millis += edgeMillis;
            distance += edgeSpec.distance;
        }

        assertEquals(weight, cchPath.getWeight(), EPSILON, context);
        assertEquals(millis, cchPath.getTime(), context);
        assertEquals(distance, cchPath.getDistance(), EPSILON, context);
        assertEquals(weight, unpackedPath.getWeight(), EPSILON, context);
        assertEquals(millis, unpackedPath.getMillis(), context);
        assertEquals(distance, unpackedPath.getDistance(), EPSILON, context);
    }

    private static Fixture randomFixture(int seed) {
        Random random = new Random(seed);
        int nodes = 2 + random.nextInt(7);
        FixtureBuilder builder = new FixtureBuilder(nodes, randomOrder(nodes, seed + 10_000));
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 1; b < nodes; b++) {
                if (random.nextInt(100) >= 45)
                    continue;
                int parallelEdges = random.nextInt(100) < 25 ? 2 + random.nextInt(2) : 1;
                for (int i = 0; i < parallelEdges; i++) {
                    int directionMode = random.nextInt(4);
                    double forwardWeight = directionMode == 1 ? Double.POSITIVE_INFINITY : 1 + random.nextInt(40);
                    double reverseWeight = directionMode == 0 ? Double.POSITIVE_INFINITY : 1 + random.nextInt(40);
                    if (directionMode == 3)
                        reverseWeight += 0.25;
                    double distance = 1 + random.nextInt(90);
                    builder.addEdge(a, b, distance, forwardWeight, reverseWeight);
                }
            }
        }
        return builder.build();
    }

    private static Fixture uniqueShortestFixture(int seed) {
        Random random = new Random(seed + 50_000);
        int nodes = 3 + random.nextInt(6);
        FixtureBuilder builder = new FixtureBuilder(nodes, randomOrder(nodes, seed + 60_000));
        for (int node = 0; node < nodes - 1; node++) {
            double forwardWeight = 2 + node * 3;
            double reverseWeight = 3 + node * 3;
            builder.addEdge(node, node + 1, 10 + node, forwardWeight, reverseWeight);
        }
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 2; b < nodes; b++) {
                if (random.nextBoolean()) {
                    double heavy = 10_000 + seed * 100 + a * nodes + b;
                    builder.addEdge(a, b, 100 + heavy, heavy, heavy + 0.5);
                }
            }
        }
        return builder.build();
    }

    private static CCHNodeOrder randomOrder(int nodes, int seed) {
        Random random = new Random(seed);
        int[] order = new int[nodes];
        for (int i = 0; i < nodes; i++) {
            order[i] = i;
        }
        for (int i = nodes - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[swap];
            order[swap] = tmp;
        }
        return CCHNodeOrder.fromOrder(order);
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

    private static final class FixtureBuilder {
        private final BaseGraph baseGraph = new BaseGraph.Builder(1).create();
        private final List<EdgeSpec> edges = new ArrayList<>();
        private final int nodes;
        private final CCHNodeOrder order;

        private FixtureBuilder(int nodes, CCHNodeOrder order) {
            this.nodes = nodes;
            this.order = order;
            for (int node = 0; node < nodes; node++) {
                baseGraph.getNodeAccess().setNode(node, node, node * 0.01);
            }
        }

        private void addEdge(int from, int to, double distance, double forwardWeight, double reverseWeight) {
            EdgeIteratorState edge = baseGraph.edge(from, to).setDistance(distance);
            assertEquals(edges.size(), edge.getEdge());
            edges.add(new EdgeSpec(edge.getEdge(), from, to, distance, forwardWeight, reverseWeight));
        }

        private Fixture build() {
            RandomizedWeighting weighting = new RandomizedWeighting(edges);
            CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(baseGraph, weighting);
            CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, order);
            CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
            QueryGraph queryGraph = QueryGraph.create(baseGraph, Collections.emptyList());
            RoutingCCHGraph routingGraph = new DefaultRoutingCCHGraph(baseGraph, topology, metric, weighting);
            return new Fixture(nodes, baseGraph, edges, order, weighting, topology, metric, new NodeBasedCCHQuery(topology, metric),
                    new CCHPathUnpacker(topology, metric), new CCHPathCalculator(routingGraph, queryGraph));
        }
    }

    private static final class Fixture {
        private final int nodes;
        private final BaseGraph baseGraph;
        private final List<EdgeSpec> edges;
        private final CCHNodeOrder order;
        private final RandomizedWeighting weighting;
        private final CCHTopology topology;
        private final CCHMetric metric;
        private final NodeBasedCCHQuery query;
        private final CCHPathUnpacker unpacker;
        private final CCHPathCalculator calculator;

        private Fixture(int nodes, BaseGraph baseGraph, List<EdgeSpec> edges, CCHNodeOrder order,
                        RandomizedWeighting weighting, CCHTopology topology, CCHMetric metric,
                        NodeBasedCCHQuery query, CCHPathUnpacker unpacker, CCHPathCalculator calculator) {
            this.nodes = nodes;
            this.baseGraph = baseGraph;
            this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
            this.order = order;
            this.weighting = weighting;
            this.topology = topology;
            this.metric = metric;
            this.query = query;
            this.unpacker = unpacker;
            this.calculator = calculator;
        }

        private EdgeSpec edge(int edgeId) {
            return edges.get(edgeId);
        }

        private Path flexiblePath(int source, int target) {
            FlexiblePathCalculator flexible = new FlexiblePathCalculator(
                    QueryGraph.create(baseGraph, Collections.emptyList()),
                    new RoutingAlgorithmFactorySimple(),
                    weighting,
                    new AlgorithmOptions()
                            .setAlgorithm(DIJKSTRA_BI)
                            .setTraversalMode(TraversalMode.NODE_BASED));
            return flexible.calcPaths(source, target, new EdgeRestrictions()).get(0);
        }

        private Path cchPath(int source, int target) {
            return calculator.calcPaths(source, target, new EdgeRestrictions()).get(0);
        }

        private String context(int seed, int source, int target, Path flexiblePath, Path cchPath) {
            return "seed=" + seed
                    + ", source=" + source
                    + ", target=" + target
                    + "\norder=" + Arrays.toString(order.getOrderArray())
                    + "\nedges=" + edges
                    + "\nupFirstOut=" + Arrays.toString(topology.getUpFirstOutArray())
                    + "\nupHead=" + Arrays.toString(topology.getUpHeadArray())
                    + "\ndownFirstOut=" + Arrays.toString(topology.getDownFirstOutArray())
                    + "\ndownHead=" + Arrays.toString(topology.getDownHeadArray())
                    + "\nmetricWeights=" + Arrays.toString(metric.getWeightArray())
                    + "\nflexible=" + stableRoute(flexiblePath)
                    + "\ncch=" + stableRoute(cchPath);
        }
    }

    private static final class RandomizedWeighting implements Weighting {
        private final List<EdgeSpec> edges;

        private RandomizedWeighting(List<EdgeSpec> edges) {
            this.edges = edges;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return edge(edgeState).weight(from(edgeState, reverse), to(edgeState, reverse));
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return edge(edgeState).millis(from(edgeState, reverse), to(edgeState, reverse));
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
            return "randomcch";
        }

        private EdgeSpec edge(EdgeIteratorState edgeState) {
            return edges.get(edgeState.getEdge());
        }

        private static int from(EdgeIteratorState edgeState, boolean reverse) {
            return reverse ? edgeState.getAdjNode() : edgeState.getBaseNode();
        }

        private static int to(EdgeIteratorState edgeState, boolean reverse) {
            return reverse ? edgeState.getBaseNode() : edgeState.getAdjNode();
        }
    }

    private static final class EdgeSpec {
        private final int id;
        private final int from;
        private final int to;
        private final double distance;
        private final double forwardWeight;
        private final double reverseWeight;

        private EdgeSpec(int id, int from, int to, double distance, double forwardWeight, double reverseWeight) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.distance = distance;
            this.forwardWeight = forwardWeight;
            this.reverseWeight = reverseWeight;
        }

        private double weight(int traversalFrom, int traversalTo) {
            if (traversalFrom == from && traversalTo == to)
                return forwardWeight;
            if (traversalFrom == to && traversalTo == from)
                return reverseWeight;
            throw new IllegalArgumentException("edge " + id + " is not adjacent to " + traversalFrom + "->" + traversalTo);
        }

        private long millis(int traversalFrom, int traversalTo) {
            double weight = weight(traversalFrom, traversalTo);
            if (!Double.isFinite(weight))
                return Long.MAX_VALUE;
            return Math.round(weight * 10);
        }

        private boolean isReverse(int traversalFrom, int traversalTo) {
            if (traversalFrom == from && traversalTo == to)
                return false;
            if (traversalFrom == to && traversalTo == from)
                return true;
            throw new IllegalArgumentException("edge " + id + " is not adjacent to " + traversalFrom + "->" + traversalTo);
        }

        @Override
        public String toString() {
            return "{id=" + id
                    + ", from=" + from
                    + ", to=" + to
                    + ", distance=" + distance
                    + ", forwardWeight=" + forwardWeight
                    + ", reverseWeight=" + reverseWeight
                    + '}';
        }
    }
}
