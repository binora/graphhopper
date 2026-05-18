// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.graphhopper.cch.CCHRouter.CUSTOMIZABLE_CH_DISABLE;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Details.EDGE_ID;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CCHGraphHopperOSMSmokeTest {
    private static final String PROFILE = "profile";
    private static final String OSM = "src/test/resources/com/graphhopper/cch/cch-virtual-endpoints.osm.xml";
    private static final GHPoint A = new GHPoint(0.0000, 0.0000);
    private static final GHPoint D = new GHPoint(0.0000, 0.0300);

    @TempDir
    Path tempDir;

    @Test
    void importPreparesAllRoutingModesAndFallsBackDeterministically() {
        CCHGraphHopper hopper = configuredHopper(tempDir.resolve("smoke-cache").toString());
        hopper.importOrLoad();
        try {
            assertPreparationsPresent(hopper);

            Map<RouteMode, CCHStableRoute> routes = new LinkedHashMap<>();
            for (RouteMode mode : RouteMode.values()) {
                GHResponse response = hopper.route(request(mode));
                assertModeUsed(mode, response);
                routes.put(mode, stable(hopper, response));
            }

            CCHStableRoute flexible = routes.get(RouteMode.FLEXIBLE);
            assertSameBytes("flexible-vs-lm", RouteMode.FLEXIBLE, flexible, RouteMode.LM, routes.get(RouteMode.LM));
            assertSameBytes("flexible-vs-ch", RouteMode.FLEXIBLE, flexible, RouteMode.CH, routes.get(RouteMode.CH));
            assertSameBytes("flexible-vs-cch", RouteMode.FLEXIBLE, flexible, RouteMode.CCH, routes.get(RouteMode.CCH));

            GHResponse staleDisable = hopper.route(request(RouteMode.CCH).putHint("cch.disable", true));
            assertFalse(staleDisable.hasErrors(), staleDisable.getErrors().toString());
            assertTrue(staleDisable.getDebugInfo().contains("cch-routing"), staleDisable.getDebugInfo());
        } finally {
            hopper.close();
        }
    }

    @Test
    void reloadKeepsSmokeCCHRouteBytesIdentical() {
        String location = tempDir.resolve("reload-cache").toString();
        CCHGraphHopper fresh = configuredHopper(location);
        fresh.importOrLoad();
        CCHStableRoute freshRoute;
        try {
            assertPreparationsPresent(fresh);
            GHResponse response = fresh.route(request(RouteMode.CCH));
            assertModeUsed(RouteMode.CCH, response);
            freshRoute = stable(fresh, response);
        } finally {
            fresh.close();
        }

        CCHGraphHopper reloaded = configuredHopper(location);
        reloaded.setAllowWrites(false);
        reloaded.importOrLoad();
        try {
            assertPreparationsPresent(reloaded);
            GHResponse response = reloaded.route(request(RouteMode.CCH));
            assertModeUsed(RouteMode.CCH, response);
            assertSameBytes("fresh-cch-vs-reloaded-cch", RouteMode.CCH, freshRoute,
                    RouteMode.CCH, stable(reloaded, response));
        } finally {
            reloaded.close();
        }
    }

    private static CCHGraphHopper configuredHopper(String location) {
        CCHGraphHopper hopper = new CCHGraphHopper();
        hopper.setGraphHopperLocation(location)
                .setOSMFile(OSM)
                .setEncodedValuesString("car_access, car_average_speed")
                .setProfiles(TestProfiles.accessAndSpeed(PROFILE, "car"))
                .setStoreOnFlush(true);
        hopper.setMinNetworkSize(0);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(PROFILE));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile(PROFILE).setMaximumLMWeight(2_000));
        hopper.setCCHProfiles(new CCHProfile(PROFILE));
        return hopper;
    }

    private static GHRequest request(RouteMode mode) {
        GHRequest request = new GHRequest(A, D)
                .setProfile(PROFILE)
                .setAlgorithm(DIJKSTRA_BI)
                .setPathDetails(Arrays.asList(EDGE_ID, EDGE_KEY))
                .putHint(Parameters.Routing.CALC_POINTS, true)
                .putHint(Parameters.Routing.INSTRUCTIONS, false)
                .putHint(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
        mode.apply(request);
        return request;
    }

    private static void assertPreparationsPresent(CCHGraphHopper hopper) {
        assertTrue(hopper.getCCHGraphs().containsKey(PROFILE), hopper.getCCHGraphs().keySet().toString());
        assertTrue(hopper.getCHGraphs().containsKey(PROFILE), hopper.getCHGraphs().keySet().toString());
        assertTrue(hopper.getLandmarks().containsKey(PROFILE), hopper.getLandmarks().keySet().toString());
    }

    private static void assertModeUsed(RouteMode mode, GHResponse response) {
        assertFalse(response.hasErrors(), response.getErrors().toString());
        String debug = response.getDebugInfo();
        if (mode == RouteMode.CCH) {
            assertTrue(debug.contains("cch-routing"), debug);
        } else if (mode == RouteMode.CH) {
            assertFalse(debug.contains("cch-routing"), debug);
            assertTrue(debug.contains("ch-routing"), debug);
        } else if (mode == RouteMode.LM) {
            assertFalse(debug.contains("cch-routing"), debug);
            assertFalse(debug.contains("ch-routing"), debug);
            assertTrue(debug.contains("landmarks-routing"), debug);
        } else {
            assertFalse(debug.contains("cch-routing"), debug);
            assertFalse(debug.contains("ch-routing"), debug);
            assertFalse(debug.contains("landmarks-routing"), debug);
            assertTrue(debug.contains("dijkstrabi-routing"), debug);
        }
    }

    private static CCHStableRoute stable(CCHGraphHopper hopper, GHResponse response) {
        return CCHStableRoute.from(hopper.getBaseGraph(), false, response);
    }

    private static void assertSameBytes(String caseName,
                                        RouteMode expectedMode, CCHStableRoute expected,
                                        RouteMode actualMode, CCHStableRoute actual) {
        if (Arrays.equals(expected.bytes, actual.bytes))
            return;

        fail("Route parity failed"
                + "\ncase=" + caseName
                + "\nprofile=" + PROFILE
                + "\nexpectedMode=" + expectedMode
                + "\nactualMode=" + actualMode
                + "\nfirstDifferingField=" + CCHStableRoute.firstDifferingField(expected, actual)
                + "\nexpected=\n" + expected.serialized
                + "\nactual=\n" + actual.serialized);
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
        LM {
            @Override
            void apply(GHRequest request) {
                request.putHint(CUSTOMIZABLE_CH_DISABLE, true)
                        .putHint(Parameters.CH.DISABLE, true)
                        .setAlgorithm(ASTAR_BI);
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
}
