// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadClassLink;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.cch.CCHRouter.CUSTOMIZABLE_CH_DISABLE;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP;
import static org.junit.jupiter.api.Assertions.*;

class CCHGraphHopperAdapterTest {
    @Test
    void loadsProfilesCCHFromConfig() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();

        CCHGraphHopperConfig config = objectMapper.readValue("{\"profiles_cch\":[{\"profile\":\"car\"}]}",
                CCHGraphHopperConfig.class);

        assertEquals(Collections.singletonList(new CCHProfile("car")), config.getCCHProfiles());
        assertEquals("profiles_cch:\ncar\n", profilesCCHString(config));
    }

    @Test
    void rejectsDuplicateUnknownAndTurnCostCCHProfiles() {
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> new CCHGraphHopper().setCCHProfiles(new CCHProfile("profile"), new CCHProfile("profile")));
        assertTrue(duplicate.getMessage().contains("Duplicate CCH reference"));

        TestCCHGraphHopper unknown = new TestCCHGraphHopper();
        unknown.setProfiles(new Profile("profile"));
        unknown.setCCHProfiles(new CCHProfile("missing"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, unknown::checkProfilesConsistency);
        assertTrue(missing.getMessage().contains("unknown profile 'missing'"));

        TestCCHGraphHopper turnCosts = new TestCCHGraphHopper();
        turnCosts.setProfiles(new Profile("profile").setTurnCostsConfig(TurnCostsConfig.car()));
        turnCosts.setCCHProfiles(new CCHProfile("profile"));
        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class, turnCosts::checkProfilesConsistency);
        assertTrue(unsupported.getMessage().contains("without turn costs"));
    }

    @Test
    void preparesInMemoryCCHAndRoutesThroughCCHByDefault() {
        TestCCHGraphHopper hopper = preparedHopper();

        GHResponse response = hopper.route(request(0, 2));

        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertTrue(response.getDebugInfo().contains("cch-routing"), response.getDebugInfo());
        assertEquals(Collections.singleton("profile"), hopper.getCCHGraphs().keySet());
        ResponsePath path = response.getBest();
        assertEquals(3, path.getRouteWeight(), 1.e-9);
        assertEquals(30, path.getTime());
        assertEquals(3, path.getDistance(), 1.e-9);
    }

    @Test
    void usesConfiguredNodeOrderProvider() {
        AtomicBoolean called = new AtomicBoolean();
        TestCCHGraphHopper hopper = preparedHopper(inputGraph -> {
            called.set(true);
            assertEquals(3, inputGraph.getNodes());
            return CCHNodeOrder.fromOrder(new int[]{1, 0, 2});
        });

        assertTrue(called.get());
        assertArrayEquals(new int[]{1, 0, 2},
                hopper.getCCHGraphs().get("profile").getTopology().getNodeOrder().getOrderArray());
    }

    @Test
    void rejectsInvalidNodeOrderProviderResults() {
        TestCCHGraphHopper wrongNodeCount = unpreparedHopper(inputGraph -> CCHNodeOrder.identity(2));
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class, wrongNodeCount::runPostProcessing);
        assertTrue(mismatch.getMessage().contains("provider returned 2 nodes"), mismatch.getMessage());

        TestCCHGraphHopper nullOrder = unpreparedHopper(inputGraph -> null);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, nullOrder::runPostProcessing);
        assertTrue(missing.getMessage().contains("provider returned null"), missing.getMessage());
    }

    @Test
    void rejectsNodeOrderProviderChangesAfterPreparation() {
        TestCCHGraphHopper hopper = preparedHopper();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> hopper.setCCHNodeOrderProvider(new DeterministicCCHNodeOrderBuilder()));

        assertTrue(error.getMessage().contains("after CCH was prepared"), error.getMessage());
    }

    @Test
    void customizableCHDisableBypassesCCHAndUsesFlexibleRouting() {
        TestCCHGraphHopper hopper = preparedHopper();

        GHResponse response = hopper.route(request(0, 2).putHint(CUSTOMIZABLE_CH_DISABLE, true));

        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertFalse(response.getDebugInfo().contains("cch-routing"), response.getDebugInfo());
        assertTrue(response.getDebugInfo().contains("dijkstrabi-routing"), response.getDebugInfo());
        assertEquals(3, response.getBest().getRouteWeight(), 1.e-9);
    }

    @Test
    void chAndLMDisableDoNotDisableCCH() {
        TestCCHGraphHopper hopper = preparedHopper();

        GHResponse response = hopper.route(request(0, 2)
                .putHint(Parameters.CH.DISABLE, true)
                .putHint(Parameters.Landmark.DISABLE, true));

        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertTrue(response.getDebugInfo().contains("cch-routing"), response.getDebugInfo());
    }

    @Test
    void cchRejectsUnsupportedRequestFeaturesClearly() {
        TestCCHGraphHopper hopper = preparedHopper();

        assertCCHError(hopper, request(0, 2).setHeadings(Collections.singletonList(90.0)), "heading");
        assertCCHError(hopper, request(0, 2).putHint(Parameters.Routing.PASS_THROUGH, true), "pass_through");
        assertCCHError(hopper, request(0, 2).setCustomModel(new CustomModel()), "custom_model");
        assertCCHError(hopper, request(0, 2).setCurbsides(Arrays.asList(Parameters.Curbsides.CURBSIDE_RIGHT, Parameters.Curbsides.CURBSIDE_ANY)), "curbside");
        assertCCHError(hopper, request(0, 2).setAlgorithm(ROUND_TRIP), "round_trip");
        assertCCHError(hopper, request(0, 2).setAlgorithm(ALT_ROUTE), "alternative_route");
    }

    @Test
    void cchRoutesVirtualEndpointsAndDisableFallsBackToFlexible() {
        TestCCHGraphHopper hopper = preparedHopper();
        hopper.snapSourceToVirtualEdgeNode();

        GHResponse cchResponse = hopper.route(request(new GHPoint(0.5, 0.005), point(2)));
        assertFalse(cchResponse.hasErrors(), cchResponse.getErrors().toString());
        assertTrue(cchResponse.getDebugInfo().contains("cch-routing"), cchResponse.getDebugInfo());

        GHResponse flexibleResponse = hopper.route(request(new GHPoint(0.5, 0.005), point(2))
                .putHint(CUSTOMIZABLE_CH_DISABLE, true));
        assertFalse(flexibleResponse.hasErrors(), flexibleResponse.getErrors().toString());
        assertFalse(flexibleResponse.getDebugInfo().contains("cch-routing"), flexibleResponse.getDebugInfo());
        assertEquals(flexibleResponse.getBest().getRouteWeight(), cchResponse.getBest().getRouteWeight(), 1.e-9);
        assertEquals(flexibleResponse.getBest().getTime(), cchResponse.getBest().getTime());
        assertEquals(flexibleResponse.getBest().getDistance(), cchResponse.getBest().getDistance(), 1.e-9);
        assertEquals(flexibleResponse.getBest().getPoints(), cchResponse.getBest().getPoints());
    }

    @Test
    void recustomizesMetricAndAtomicallySwapsRuntimeGraph() throws Exception {
        TestCCHGraphHopper hopper = preparedHopper();
        assertEquals(3, hopper.route(request(0, 2)).getBest().getRouteWeight(), 1.e-9);

        hopper.weighting.setMultiplier(2);
        hopper.weighting.blockDuringCustomization();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CCHCustomizationResult> future = executor.submit(() -> hopper.recustomizeCCHProfile("profile"));
            assertTrue(hopper.weighting.awaitCustomizationStarted());

            CCHCustomizationBusyException busy = assertThrows(CCHCustomizationBusyException.class,
                    () -> hopper.recustomizeCCHProfile("profile"));
            assertTrue(busy.getMessage().contains("already running"), busy.getMessage());
            assertEquals(3, hopper.route(request(0, 2)).getBest().getRouteWeight(), 1.e-9);

            hopper.weighting.releaseCustomization();
            CCHCustomizationResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals("profile", result.getProfile());
            assertFalse(result.isPersisted());
            assertEquals(0, result.getMetricGeneration());
            assertEquals(6, hopper.route(request(0, 2)).getBest().getRouteWeight(), 1.e-9);
            assertEquals(1, hopper.getCCHCustomizationStatus().size());
            assertFalse(hopper.getCCHCustomizationStatus().get(0).isBusy());
        } finally {
            hopper.weighting.releaseCustomization();
            executor.shutdownNow();
        }
    }

    @Test
    void recustomizationRejectsInvalidRequestsWithoutSwappingMetric() {
        TestCCHGraphHopper hopper = preparedHopper();
        assertEquals(3, hopper.route(request(0, 2)).getBest().getRouteWeight(), 1.e-9);

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> hopper.recustomizeCCHProfile("missing"));
        assertTrue(missing.getMessage().contains("unknown profile 'missing'"), missing.getMessage());

        hopper.weighting.setMultiplier(2);
        hopper.setAllowWrites(false);
        IllegalStateException readOnly = assertThrows(IllegalStateException.class,
                () -> hopper.recustomizeCCHProfile("profile"));
        assertTrue(readOnly.getMessage().contains("write access"), readOnly.getMessage());
        assertEquals(3, hopper.route(request(0, 2)).getBest().getRouteWeight(), 1.e-9);
    }

    private static String profilesCCHString(CCHGraphHopperConfig config) {
        StringBuilder builder = new StringBuilder("profiles_cch:\n");
        for (CCHProfile profile : config.getCCHProfiles()) {
            builder.append(profile).append('\n');
        }
        return builder.toString();
    }

    private static void assertCCHError(TestCCHGraphHopper hopper, GHRequest request, String messagePart) {
        GHResponse response = hopper.route(request);
        assertTrue(response.hasErrors());
        assertTrue(response.getErrors().get(0).getMessage().contains(messagePart), response.getErrors().toString());
        assertTrue(response.getErrors().get(0).getMessage().contains(CUSTOMIZABLE_CH_DISABLE), response.getErrors().toString());
    }

    private static TestCCHGraphHopper preparedHopper() {
        TestCCHGraphHopper hopper = unpreparedHopper(new DeterministicCCHNodeOrderBuilder());
        hopper.runPostProcessing();
        return hopper;
    }

    private static TestCCHGraphHopper preparedHopper(CCHNodeOrderProvider nodeOrderProvider) {
        TestCCHGraphHopper hopper = unpreparedHopper(nodeOrderProvider);
        hopper.runPostProcessing();
        return hopper;
    }

    private static TestCCHGraphHopper unpreparedHopper(CCHNodeOrderProvider nodeOrderProvider) {
        EncodingManager encodingManager = new EncodingManager.Builder()
                .add(RoadClass.create())
                .add(RoadEnvironment.create())
                .add(Roundabout.create())
                .add(VehicleAccess.create("car"))
                .add(RoadClassLink.create())
                .add(MaxSpeed.create())
                .add(Subnetwork.create("profile"))
                .build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        NodeAccess nodeAccess = graph.getNodeAccess();
        nodeAccess.setNode(0, 0, 0);
        nodeAccess.setNode(1, 1, 0.01);
        nodeAccess.setNode(2, 2, 0.02);
        graph.edge(0, 1).setDistance(1);
        graph.edge(1, 2).setDistance(2);
        graph.edge(0, 2).setDistance(10);

        TestCCHGraphHopper hopper = new TestCCHGraphHopper();
        hopper.setProfiles(new Profile("profile"));
        hopper.setCCHProfiles(new CCHProfile("profile"));
        hopper.setCCHNodeOrderProvider(nodeOrderProvider);
        hopper.setGraph(graph, encodingManager);
        return hopper;
    }

    private static GHRequest request(int source, int target) {
        return request(point(source), point(target));
    }

    private static GHRequest request(GHPoint source, GHPoint target) {
        return new GHRequest()
                .setPoints(Arrays.asList(source, target))
                .setProfile("profile")
                .setAlgorithm(DIJKSTRA_BI)
                .putHint(Parameters.Routing.INSTRUCTIONS, false);
    }

    private static GHPoint point(int node) {
        return new GHPoint(node, node * 0.01);
    }

    private static final class TestCCHGraphHopper extends CCHGraphHopper {
        private final DistanceWeighting weighting = new DistanceWeighting();

        private void setGraph(BaseGraph graph, EncodingManager encodingManager) {
            this.encodingManager = encodingManager;
            setBaseGraph(graph);
        }

        private void runPostProcessing() {
            postProcessing(false);
        }

        private void snapSourceToVirtualEdgeNode() {
            EdgeIteratorState sourceEdge = getBaseGraph().getEdgeIteratorState(0, 1);
            setLocationIndex(new VirtualSourceLocationIndex(getLocationIndex(), sourceEdge));
        }

        @Override
        protected WeightingFactory createWeightingFactory() {
            return (profile, requestHints, disableTurnCosts) -> weighting;
        }
    }

    private static final class VirtualSourceLocationIndex implements LocationIndex {
        private final LocationIndex delegate;
        private final EdgeIteratorState sourceEdge;

        private VirtualSourceLocationIndex(LocationIndex delegate, EdgeIteratorState sourceEdge) {
            this.delegate = delegate;
            this.sourceEdge = sourceEdge;
        }

        @Override
        public Snap findClosest(double lat, double lon, com.graphhopper.routing.util.EdgeFilter edgeFilter) {
            if (Math.abs(lat - 0.5) < 1.e-12 && Math.abs(lon - 0.005) < 1.e-12 && edgeFilter.accept(sourceEdge)) {
                Snap snap = new Snap(lat, lon);
                snap.setClosestEdge(sourceEdge);
                snap.setClosestNode(sourceEdge.getBaseNode());
                snap.setWayIndex(0);
                snap.setSnappedPosition(Snap.Position.EDGE);
                snap.calcSnappedPoint(new DistanceCalcEarth());
                return snap;
            }
            return delegate.findClosest(lat, lon, edgeFilter);
        }

        @Override
        public void query(BBox queryBBox, Visitor function) {
            delegate.query(queryBBox, function);
        }

        @Override
        public void query(TileFilter tileFilter, Visitor function) {
            delegate.query(tileFilter, function);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class DistanceWeighting implements Weighting {
        private volatile double multiplier = 1;
        private volatile CountDownLatch customizationStarted;
        private volatile CountDownLatch customizationRelease;
        private final AtomicBoolean customizationBlockConsumed = new AtomicBoolean();

        private void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        private void blockDuringCustomization() {
            customizationBlockConsumed.set(false);
            customizationStarted = new CountDownLatch(1);
            customizationRelease = new CountDownLatch(1);
        }

        private boolean awaitCustomizationStarted() throws InterruptedException {
            CountDownLatch latch = customizationStarted;
            return latch != null && latch.await(5, TimeUnit.SECONDS);
        }

        private void releaseCustomization() {
            CountDownLatch latch = customizationRelease;
            if (latch != null)
                latch.countDown();
            customizationStarted = null;
            customizationRelease = null;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 1;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            awaitIfBlocked();
            return edgeState.getDistance() * multiplier;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return Math.round(edgeState.getDistance() * 10 * multiplier);
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

        private void awaitIfBlocked() {
            CountDownLatch started = customizationStarted;
            CountDownLatch release = customizationRelease;
            if (started == null || release == null)
                return;
            if (!customizationBlockConsumed.compareAndSet(false, true))
                return;
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
