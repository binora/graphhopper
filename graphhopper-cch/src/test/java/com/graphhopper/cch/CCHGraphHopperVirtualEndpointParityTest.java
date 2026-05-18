// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.config.CHProfile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.cch.CCHRouter.CUSTOMIZABLE_CH_DISABLE;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Details.EDGE_ID;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CCHGraphHopperVirtualEndpointParityTest {
    private static final String PROFILE = "profile";
    private static final String OSM = "src/test/resources/com/graphhopper/cch/cch-virtual-endpoints.osm.xml";

    private static final GHPoint A = point(0.0000, 0.0000);
    private static final GHPoint D = point(0.0000, 0.0300);
    private static final GHPoint G = point(0.0200, 0.0100);
    private static final GHPoint I = point(0.0300, 0.0100);

    private static final GHPoint AB_MID = point(0.0000, 0.0050);
    private static final GHPoint BC_NEAR_B = point(0.0000, 0.0120);
    private static final GHPoint BC_NEAR_C = point(0.0000, 0.0180);
    private static final GHPoint CD_MID = point(0.0000, 0.0250);
    private static final GHPoint FG_MID = point(0.0200, 0.0050);
    private static final GHPoint HI_MID = point(0.0300, 0.0050);
    private static final GHPoint JK_MID = point(0.1000, 0.1050);

    @TempDir
    Path tempDir;

    @Test
    void coordinateVirtualEndpointRoutesMatchFlexibleAndCH() {
        CCHGraphHopper hopper = configuredHopper(tempDir.resolve("parity-cache").toString());
        hopper.importOrLoad();
        try {
            for (QuerySpec query : queries()) {
                assertRouteParity(hopper, query);
            }
        } finally {
            hopper.close();
        }
    }

    @Test
    void reloadKeepsVirtualEndpointCCHBytesIdentical() {
        String location = tempDir.resolve("reload-cache").toString();
        CCHGraphHopper fresh = configuredHopper(location);
        fresh.importOrLoad();
        Map<String, CCHStableRoute> freshRoutes = new LinkedHashMap<>();
        try {
            for (QuerySpec query : queries()) {
                GHResponse response = fresh.route(request(query, RouteMode.CCH));
                assertModeUsed(query, RouteMode.CCH, response);
                freshRoutes.put(query.name, stable(fresh, query, response));
            }
        } finally {
            fresh.close();
        }

        CCHGraphHopper reloaded = configuredHopper(location);
        reloaded.setAllowWrites(false);
        reloaded.importOrLoad();
        try {
            for (QuerySpec query : queries()) {
                GHResponse response = reloaded.route(request(query, RouteMode.CCH));
                assertModeUsed(query, RouteMode.CCH, response);
                assertSameBytes("reload-" + query.name, query, RouteMode.CCH, freshRoutes.get(query.name),
                        RouteMode.CCH, stable(reloaded, query, response));
            }
        } finally {
            reloaded.close();
        }
    }

    private static List<QuerySpec> queries() {
        return Arrays.asList(
                query("virtual-source-tower-target", AB_MID, D, false),
                query("tower-source-virtual-target", A, CD_MID, false),
                query("both-virtual-different-edges", AB_MID, CD_MID, false),
                query("both-virtual-same-edge", BC_NEAR_B, BC_NEAR_C, false),
                query("forward-oneway-allowed", FG_MID, G, false),
                query("forward-oneway-forbidden", G, FG_MID, false),
                query("reverse-only-allowed", I, HI_MID, false),
                query("reverse-only-forbidden", HI_MID, I, false),
                query("disconnected-no-path", AB_MID, JK_MID, false),
                query("instructions", AB_MID, D, true));
    }

    private static void assertRouteParity(CCHGraphHopper hopper, QuerySpec query) {
        Map<RouteMode, CCHStableRoute> routes = new LinkedHashMap<>();
        for (RouteMode mode : RouteMode.values()) {
            GHResponse response = hopper.route(request(query, mode));
            assertModeUsed(query, mode, response);
            routes.put(mode, stable(hopper, query, response));
        }

        CCHStableRoute flexible = routes.get(RouteMode.FLEXIBLE);
        assertSameBytes(query.name, query, RouteMode.FLEXIBLE, flexible, RouteMode.CH, routes.get(RouteMode.CH));
        assertSameBytes(query.name, query, RouteMode.FLEXIBLE, flexible, RouteMode.CCH, routes.get(RouteMode.CCH));
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
        hopper.setCCHProfiles(new CCHProfile(PROFILE));
        return hopper;
    }

    private static GHRequest request(QuerySpec query, RouteMode mode) {
        GHRequest request = new GHRequest(query.source, query.target)
                .setProfile(PROFILE)
                .setAlgorithm(DIJKSTRA_BI)
                .setPathDetails(Arrays.asList(EDGE_ID, EDGE_KEY))
                .putHint(Parameters.Routing.CALC_POINTS, true)
                .putHint(Parameters.Routing.INSTRUCTIONS, query.instructions)
                .putHint(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
        mode.apply(request);
        return request;
    }

    private static CCHStableRoute stable(CCHGraphHopper hopper, QuerySpec query, GHResponse response) {
        return CCHStableRoute.from(hopper.getBaseGraph(), query.instructions, response);
    }

    private static void assertModeUsed(QuerySpec query, RouteMode mode, GHResponse response) {
        String debug = response.getDebugInfo();
        if (mode == RouteMode.CCH) {
            assertTrue(debug.contains("cch-routing"), query + "\n" + debug + "\n" + response.getErrors());
        } else if (mode == RouteMode.CH) {
            assertFalse(debug.contains("cch-routing"), query + "\n" + debug);
            assertTrue(debug.contains("ch-routing"), query + "\n" + debug + "\n" + response.getErrors());
        } else {
            assertFalse(debug.contains("cch-routing"), query + "\n" + debug);
            assertFalse(debug.contains("ch-routing"), query + "\n" + debug);
            assertTrue(debug.contains("dijkstrabi-routing"), query + "\n" + debug + "\n" + response.getErrors());
        }
    }

    private static void assertSameBytes(String caseName, QuerySpec query,
                                        RouteMode expectedMode, CCHStableRoute expected,
                                        RouteMode actualMode, CCHStableRoute actual) {
        if (Arrays.equals(expected.bytes, actual.bytes))
            return;

        fail("Route parity failed"
                + "\ncase=" + caseName
                + "\nprofile=" + PROFILE
                + "\nquery=" + query
                + "\nexpectedMode=" + expectedMode
                + "\nactualMode=" + actualMode
                + "\nfirstDifferingField=" + CCHStableRoute.firstDifferingField(expected, actual)
                + "\nexpected=\n" + expected.serialized
                + "\nactual=\n" + actual.serialized);
    }

    private static QuerySpec query(String name, GHPoint source, GHPoint target, boolean instructions) {
        return new QuerySpec(name, source, target, instructions);
    }

    private static GHPoint point(double lat, double lon) {
        return new GHPoint(lat, lon);
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

    private static final class QuerySpec {
        private final String name;
        private final GHPoint source;
        private final GHPoint target;
        private final boolean instructions;

        private QuerySpec(String name, GHPoint source, GHPoint target, boolean instructions) {
            this.name = name;
            this.source = source;
            this.target = target;
            this.instructions = instructions;
        }

        @Override
        public String toString() {
            return "{name=" + name
                    + ", source=" + source
                    + ", target=" + target
                    + ", instructions=" + instructions
                    + '}';
        }
    }
}
