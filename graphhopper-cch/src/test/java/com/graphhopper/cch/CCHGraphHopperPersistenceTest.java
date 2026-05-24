// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.config.CHProfile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static com.graphhopper.cch.CCHRouter.CUSTOMIZABLE_CH_DISABLE;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Details.EDGE_ID;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;
import static org.junit.jupiter.api.Assertions.*;

class CCHGraphHopperPersistenceTest {
    private static final String PROFILE = "profile";
    private static final String OSM = "../core/src/test/resources/com/graphhopper/reader/osm/test-osm.xml";

    @TempDir
    Path tempDir;

    @Test
    void reloadsPersistedCCHAndKeepsRouteBytesIdentical() {
        String location = tempDir.resolve("graph-cache").toString();
        CCHGraphHopper fresh = configuredHopper(location, true);
        fresh.importOrLoad();

        GHResponse freshCCHResponse = fresh.route(request(RouteMode.CCH));
        GHResponse freshCHResponse = fresh.route(request(RouteMode.CH));
        GHResponse freshFlexibleResponse = fresh.route(request(RouteMode.FLEXIBLE));
        assertFalse(freshCCHResponse.hasErrors(), freshCCHResponse.getErrors().toString());
        assertFalse(freshCHResponse.hasErrors(), freshCHResponse.getErrors().toString());
        assertFalse(freshFlexibleResponse.hasErrors(), freshFlexibleResponse.getErrors().toString());
        assertTrue(freshCCHResponse.getDebugInfo().contains("cch-routing"), freshCCHResponse.getDebugInfo());
        assertTrue(freshCHResponse.getDebugInfo().contains("ch-routing"), freshCHResponse.getDebugInfo());

        CCHStableRoute freshCCH = stable(fresh, freshCCHResponse);
        assertSameBytes("fresh flexible vs CH", stable(fresh, freshFlexibleResponse), stable(fresh, freshCHResponse));
        assertSameBytes("fresh flexible vs CCH", stable(fresh, freshFlexibleResponse), freshCCH);
        CCHCustomizationResult recustomized = fresh.recustomizeCCHProfile(PROFILE);
        assertTrue(recustomized.isPersisted());
        assertEquals(2, recustomized.getMetricGeneration());
        GHResponse recustomizedCCHResponse = fresh.route(request(RouteMode.CCH));
        assertFalse(recustomizedCCHResponse.hasErrors(), recustomizedCCHResponse.getErrors().toString());
        CCHStableRoute recustomizedCCH = stable(fresh, recustomizedCCHResponse);
        assertSameBytes("fresh CCH vs recustomized CCH", freshCCH, recustomizedCCH);
        fresh.close();

        CCHGraphHopper reloaded = configuredHopper(location, true);
        reloaded.setAllowWrites(false);
        reloaded.importOrLoad();
        GHResponse reloadedCCHResponse = reloaded.route(request(RouteMode.CCH));
        assertFalse(reloadedCCHResponse.hasErrors(), reloadedCCHResponse.getErrors().toString());
        assertTrue(reloadedCCHResponse.getDebugInfo().contains("cch-routing"), reloadedCCHResponse.getDebugInfo());
        assertEquals(2, reloaded.getCCHCustomizationStatus().get(0).getMetricGeneration());
        assertSameBytes("recustomized CCH vs reloaded CCH", recustomizedCCH, stable(reloaded, reloadedCCHResponse));
        reloaded.close();
    }

    @Test
    void missingPersistedCCHFailsWhenWritesAreDisabled() {
        String location = tempDir.resolve("missing-cch").toString();
        CCHGraphHopper withoutCCH = configuredHopper(location, false);
        withoutCCH.importOrLoad();
        withoutCCH.close();

        CCHGraphHopper readOnlyWithCCH = configuredHopper(location, true);
        readOnlyWithCCH.setAllowWrites(false);
        IllegalStateException error = assertThrows(IllegalStateException.class, readOnlyWithCCH::importOrLoad);
        assertTrue(error.getMessage().contains("CCH topology is missing"), error.getMessage());
        assertTrue(error.getMessage().contains("writes are not allowed"), error.getMessage());
        readOnlyWithCCH.close();
    }

    @Test
    void recustomizationRequiresWriteAccessForPersistedGraphs() {
        String location = tempDir.resolve("readonly-recustomize").toString();
        CCHGraphHopper fresh = configuredHopper(location, true);
        fresh.importOrLoad();
        fresh.close();

        CCHGraphHopper reloaded = configuredHopper(location, true);
        reloaded.setAllowWrites(false);
        reloaded.importOrLoad();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reloaded.recustomizeCCHProfile(PROFILE));
        assertTrue(error.getMessage().contains("write access"), error.getMessage());
        reloaded.close();
    }

    @Test
    void reloadsActiveTrafficSnapshotMetadataWithPersistedMetric() {
        String location = tempDir.resolve("traffic-cch").toString();
        CCHGraphHopper fresh = configuredHopper(location, true);
        fresh.importOrLoad();
        fresh.putCCHTrafficSnapshot(CCHTrafficSnapshot.builder("jam-1")
                .setCreatedMillis(123)
                .delay(0, false, 10)
                .build());
        CCHTrafficCustomizationResult result = fresh.activateCCHTrafficSnapshotAndRecustomize(PROFILE, "jam-1");
        assertTrue(result.getCustomization().isPersisted());
        assertEquals(2, result.getCustomization().getMetricGeneration());
        fresh.close();

        CCHGraphHopper reloaded = configuredHopper(location, true);
        reloaded.setAllowWrites(false);
        reloaded.importOrLoad();
        assertEquals("jam-1", reloaded.getActiveCCHTrafficSnapshot().getId());
        assertEquals(1, reloaded.getActiveCCHTrafficSnapshot().size());
        assertEquals(1, reloaded.getCCHTrafficStatus().getSnapshots().size());
        assertEquals(2, reloaded.getCCHCustomizationStatus().get(0).getMetricGeneration());
        reloaded.close();
    }

    private CCHGraphHopper configuredHopper(String location, boolean withCCH) {
        CCHGraphHopper hopper = new CCHGraphHopper();
        hopper.setGraphHopperLocation(location)
                .setOSMFile(OSM)
                .setEncodedValuesString("car_access, car_average_speed")
                .setProfiles(TestProfiles.accessAndSpeed(PROFILE, "car"))
                .setStoreOnFlush(true);
        hopper.setMinNetworkSize(0);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(PROFILE));
        if (withCCH)
            hopper.setCCHProfiles(new CCHProfile(PROFILE));
        return hopper;
    }

    private static GHRequest request(RouteMode mode) {
        GHRequest request = new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(PROFILE)
                .setAlgorithm(DIJKSTRA_BI)
                .setPathDetails(Arrays.asList(EDGE_ID, EDGE_KEY))
                .putHint(Parameters.Routing.CALC_POINTS, true)
                .putHint(Parameters.Routing.INSTRUCTIONS, false)
                .putHint(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
        mode.apply(request);
        return request;
    }

    private static CCHStableRoute stable(CCHGraphHopper hopper, GHResponse response) {
        return CCHStableRoute.from(hopper.getBaseGraph(), 0, false, response);
    }

    private static void assertSameBytes(String caseName, CCHStableRoute expected, CCHStableRoute actual) {
        if (Arrays.equals(expected.bytes, actual.bytes))
            return;
        fail("Route parity failed"
                + "\ncase=" + caseName
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
