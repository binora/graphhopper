// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.Router;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadClassLink;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.RoutingCHGraphImpl;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.cch.CCHRouter.CUSTOMIZABLE_CH_DISABLE;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Details.EDGE_ID;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CCHGraphHopperRouteParityTest {
    private static final String PROFILE = "profile";

    @Test
    void shortcutTowerNodeRouteIsByteIdenticalAcrossFlexibleCHAndCCH() {
        assertRouteParity("shortcut-chain",
                GraphFixture.of(3,
                        edge(0, 1, 1, 1, 1, "A"),
                        edge(1, 2, 2, 2, 2, "B"),
                        edge(0, 2, 10, 10, 10, "detour")),
                query(0, 2, false));
    }

    @Test
    void directRouteIsByteIdenticalAcrossFlexibleCHAndCCH() {
        assertRouteParity("direct-wins",
                GraphFixture.of(3,
                        edge(0, 2, 2, 2, 2, "direct"),
                        edge(0, 1, 5, 5, 5, "heavy-a"),
                        edge(1, 2, 5, 5, 5, "heavy-b")),
                query(0, 2, false));
    }

    @Test
    void reverseAsymmetricRouteIsByteIdenticalAcrossFlexibleCHAndCCH() {
        assertRouteParity("reverse-asymmetric",
                GraphFixture.of(3,
                        edge(0, 1, 4, 40, 4, "left"),
                        edge(1, 2, 3, 30, 3, "right"),
                        edge(0, 2, 20, 20, 20, "direct")),
                query(2, 0, false));
    }

    @Test
    void disconnectedRouteErrorIsByteIdenticalAcrossFlexibleCHAndCCH() {
        assertRouteParity("disconnected",
                GraphFixture.of(4,
                        edge(0, 1, 1, 1, 1, "component-a"),
                        edge(2, 3, 1, 1, 1, "component-b")),
                query(0, 3, false));
    }

    @Test
    void instructionRouteIsByteIdenticalAcrossFlexibleCHAndCCH() {
        assertRouteParity("instructions",
                GraphFixture.of(new double[][]{
                                {0, 0},
                                {0, 0.01},
                                {0.01, 0.01}
                        },
                        edge(0, 1, 1, 1, 1, "West Road"),
                        edge(1, 2, 2, 2, 2, "North Road"),
                        edge(0, 2, 20, 20, 20, "Diagonal Road")),
                query(0, 2, true));
    }

    @Test
    void requestHintsSelectCCHThenCHThenFlexible() {
        GraphFixture fixture = GraphFixture.of(3,
                edge(0, 1, 1, 1, 1, "A"),
                edge(1, 2, 2, 2, 2, "B"),
                edge(0, 2, 10, 10, 10, "detour"));
        TestCCHGraphHopper hopper = prepareHopper(fixture);
        QuerySpec query = query(0, 2, false);

        GHResponse cch = hopper.route(request(fixture, query, RouteMode.CCH));
        assertFalse(cch.hasErrors(), cch.getErrors().toString());
        assertTrue(cch.getDebugInfo().contains("cch-routing"), cch.getDebugInfo());

        GHResponse ch = hopper.route(request(fixture, query, RouteMode.CH));
        assertFalse(ch.hasErrors(), ch.getErrors().toString());
        assertFalse(ch.getDebugInfo().contains("cch-routing"), ch.getDebugInfo());
        assertTrue(ch.getDebugInfo().contains("ch-routing"), ch.getDebugInfo());

        GHResponse flexible = hopper.route(request(fixture, query, RouteMode.FLEXIBLE));
        assertFalse(flexible.hasErrors(), flexible.getErrors().toString());
        assertFalse(flexible.getDebugInfo().contains("cch-routing"), flexible.getDebugInfo());
        assertFalse(flexible.getDebugInfo().contains("ch-routing"), flexible.getDebugInfo());
        assertTrue(flexible.getDebugInfo().contains("dijkstrabi-routing"), flexible.getDebugInfo());
    }

    private static void assertRouteParity(String caseName, GraphFixture fixture, QuerySpec query) {
        TestCCHGraphHopper hopper = prepareHopper(fixture);
        Map<RouteMode, CCHStableRoute> routes = new LinkedHashMap<>();
        for (RouteMode mode : RouteMode.values()) {
            GHResponse response = hopper.route(request(fixture, query, mode));
            routes.put(mode, CCHStableRoute.from(fixture.graph, query.sourceNode, query.instructions, response));
        }

        CCHStableRoute flexible = routes.get(RouteMode.FLEXIBLE);
        assertSameBytes(caseName, query, RouteMode.FLEXIBLE, flexible, RouteMode.CH, routes.get(RouteMode.CH));
        assertSameBytes(caseName, query, RouteMode.FLEXIBLE, flexible, RouteMode.CCH, routes.get(RouteMode.CCH));
    }

    private static void assertSameBytes(String caseName, QuerySpec query,
                                        RouteMode expectedMode, CCHStableRoute expected,
                                        RouteMode actualMode, CCHStableRoute actual) {
        if (Arrays.equals(expected.bytes, actual.bytes))
            return;

        String differingField = CCHStableRoute.firstDifferingField(expected, actual);
        fail("Route parity failed"
                + "\ncase=" + caseName
                + "\nprofile=" + PROFILE
                + "\nquery=" + query
                + "\nexpectedMode=" + expectedMode
                + "\nactualMode=" + actualMode
                + "\nfirstDifferingField=" + differingField
                + "\nexpected=\n" + expected.serialized
                + "\nactual=\n" + actual.serialized);
    }

    private static TestCCHGraphHopper prepareHopper(GraphFixture fixture) {
        EncodingManager encodingManager = new EncodingManager.Builder()
                .add(RoadClass.create())
                .add(RoadEnvironment.create())
                .add(Roundabout.create())
                .add(VehicleAccess.create("car"))
                .add(RoadClassLink.create())
                .add(MaxSpeed.create())
                .add(Subnetwork.create(PROFILE))
                .build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        NodeAccess nodeAccess = graph.getNodeAccess();
        for (int node = 0; node < fixture.nodes(); node++) {
            nodeAccess.setNode(node, fixture.lat(node), fixture.lon(node));
        }
        for (int edgeId = 0; edgeId < fixture.edges.size(); edgeId++) {
            EdgeSpec spec = fixture.edges.get(edgeId);
            EdgeIteratorState edge = graph.edge(spec.from, spec.to).setDistance(spec.distance);
            if (!spec.name.isEmpty())
                edge.setKeyValues(Collections.singletonMap(STREET_NAME, new KVStorage.KValue(spec.name)));
            if (!spec.geometry.isEmpty())
                edge.setWayGeometry(spec.geometry);
            if (edge.getEdge() != edgeId)
                throw new IllegalStateException("unexpected edge id " + edge.getEdge() + ", expected " + edgeId);
        }

        TestCCHGraphHopper hopper = new TestCCHGraphHopper(new FixtureWeighting(fixture.edges));
        hopper.setProfiles(new Profile(PROFILE));
        hopper.setCCHProfiles(new CCHProfile(PROFILE));
        hopper.setGraph(graph, encodingManager);
        hopper.runPostProcessing();
        return hopper;
    }

    private static GHRequest request(GraphFixture fixture, QuerySpec query, RouteMode mode) {
        GHRequest request = new GHRequest(fixture.point(query.sourceNode), fixture.point(query.targetNode))
                .setProfile(PROFILE)
                .setAlgorithm(DIJKSTRA_BI)
                .setPathDetails(Arrays.asList(EDGE_ID, EDGE_KEY))
                .putHint(Parameters.Routing.CALC_POINTS, true)
                .putHint(Parameters.Routing.INSTRUCTIONS, query.instructions)
                .putHint(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
        mode.apply(request);
        return request;
    }

    private static QuerySpec query(int source, int target, boolean instructions) {
        return new QuerySpec(source, target, instructions);
    }

    private static EdgeSpec edge(int from, int to, double distance, double forwardWeight, double reverseWeight, String name) {
        return new EdgeSpec(from, to, distance, forwardWeight, reverseWeight, name, PointList.EMPTY);
    }

    private enum RouteMode {
        CCH {
            @Override
            void apply(GHRequest request) {
            }
        },
        CH {
            @Override
            void apply(GHRequest request) {
                request.putHint(CUSTOMIZABLE_CH_DISABLE, true);
            }
        },
        FLEXIBLE {
            @Override
            void apply(GHRequest request) {
                request.putHint(CUSTOMIZABLE_CH_DISABLE, true)
                        .putHint(Parameters.CH.DISABLE, true)
                        .putHint(Parameters.Landmark.DISABLE, true);
            }
        };

        abstract void apply(GHRequest request);
    }

    private static final class GraphFixture {
        private final BaseGraph graph;
        private final double[][] coordinates;
        private final List<EdgeSpec> edges;

        private GraphFixture(BaseGraph graph, double[][] coordinates, List<EdgeSpec> edges) {
            this.graph = graph;
            this.coordinates = coordinates;
            this.edges = edges;
        }

        private static GraphFixture of(int nodes, EdgeSpec... edges) {
            double[][] coordinates = new double[nodes][2];
            for (int node = 0; node < nodes; node++) {
                coordinates[node][0] = node;
                coordinates[node][1] = node * 0.01;
            }
            return of(coordinates, edges);
        }

        private static GraphFixture of(double[][] coordinates, EdgeSpec... edges) {
            EncodingManager encodingManager = new EncodingManager.Builder()
                    .add(RoadClass.create())
                    .add(RoadEnvironment.create())
                    .add(Roundabout.create())
                    .add(VehicleAccess.create("car"))
                    .add(RoadClassLink.create())
                    .add(MaxSpeed.create())
                    .add(Subnetwork.create(PROFILE))
                    .build();
            BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
            NodeAccess nodeAccess = graph.getNodeAccess();
            for (int node = 0; node < coordinates.length; node++) {
                nodeAccess.setNode(node, coordinates[node][0], coordinates[node][1]);
            }
            for (int edgeId = 0; edgeId < edges.length; edgeId++) {
                EdgeSpec spec = edges[edgeId];
                EdgeIteratorState edge = graph.edge(spec.from, spec.to).setDistance(spec.distance);
                if (edge.getEdge() != edgeId)
                    throw new IllegalStateException("unexpected edge id " + edge.getEdge() + ", expected " + edgeId);
            }
            return new GraphFixture(graph, coordinates, Arrays.asList(edges));
        }

        private int nodes() {
            return coordinates.length;
        }

        private double lat(int node) {
            return coordinates[node][0];
        }

        private double lon(int node) {
            return coordinates[node][1];
        }

        private GHPoint point(int node) {
            return new GHPoint(lat(node), lon(node));
        }
    }

    private static final class QuerySpec {
        private final int sourceNode;
        private final int targetNode;
        private final boolean instructions;

        private QuerySpec(int sourceNode, int targetNode, boolean instructions) {
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.instructions = instructions;
        }

        @Override
        public String toString() {
            return "{sourceNode=" + sourceNode
                    + ", targetNode=" + targetNode
                    + ", instructions=" + instructions
                    + '}';
        }
    }

    private static final class EdgeSpec {
        private final int from;
        private final int to;
        private final double distance;
        private final double forwardWeight;
        private final double reverseWeight;
        private final String name;
        private final PointList geometry;

        private EdgeSpec(int from, int to, double distance, double forwardWeight, double reverseWeight,
                         String name, PointList geometry) {
            this.from = from;
            this.to = to;
            this.distance = distance;
            this.forwardWeight = forwardWeight;
            this.reverseWeight = reverseWeight;
            this.name = name;
            this.geometry = geometry;
        }

        private double weight(int traversalFrom, int traversalTo) {
            if (traversalFrom == from && traversalTo == to)
                return forwardWeight;
            if (traversalFrom == to && traversalTo == from)
                return reverseWeight;
            throw new IllegalArgumentException("edge is not adjacent to " + traversalFrom + "->" + traversalTo);
        }

        private long millis(int traversalFrom, int traversalTo) {
            double weight = weight(traversalFrom, traversalTo);
            if (!Double.isFinite(weight))
                return Long.MAX_VALUE;
            return Math.round(weight * 10);
        }
    }

    private static final class FixtureWeighting implements Weighting {
        private final List<EdgeSpec> edges;

        private FixtureWeighting(List<EdgeSpec> edges) {
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
            return "fixture";
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

    private static final class TestCCHGraphHopper extends CCHGraphHopper {
        private final Weighting weighting;
        private Map<String, RoutingCHGraph> testCHGraphs = Collections.emptyMap();

        private TestCCHGraphHopper(Weighting weighting) {
            this.weighting = weighting;
        }

        private void setGraph(BaseGraph graph, EncodingManager encodingManager) {
            this.encodingManager = encodingManager;
            setBaseGraph(graph);
        }

        private void runPostProcessing() {
            postProcessing(false);
        }

        @Override
        protected void postProcessing(boolean closeEarly) {
            initLocationIndex();
            prepareCH();
            loadOrPrepareCCH();
        }

        @Override
        protected WeightingFactory createWeightingFactory() {
            return (profile, requestHints, disableTurnCosts) -> weighting;
        }

        @Override
        protected Router doCreateRouter(BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex,
                                        Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathBuilderFactory,
                                        TranslationMap trMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                                        Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
            return new CCHRouter(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                    trMap, routerConfig, weightingFactory, testCHGraphs, landmarks, getCCHGraphs());
        }

        private void prepareCH() {
            BaseGraph graph = getBaseGraph();
            if (!graph.isFrozen())
                graph.freeze();
            CHConfig chConfig = CHConfig.nodeBased(PROFILE, weighting);
            PrepareContractionHierarchies prepare = PrepareContractionHierarchies.fromGraph(graph, chConfig);
            PrepareContractionHierarchies.Result result = prepare.doWork();
            testCHGraphs = Collections.singletonMap(PROFILE,
                    RoutingCHGraphImpl.fromGraph(graph, result.getCHStorage(), result.getCHConfig()));
        }
    }
}
