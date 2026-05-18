/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.ws.rs.client.Entity;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class CCHCustomizationResourceIT {
    private static final String PROFILE = "profile";
    private static final String DIR = "./target/cch-admin-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app =
            new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("prepare.min_network_size", 0)
                .putObject("import.osm.ignored_highways", "")
                .putObject("datareader.file", "../core/src/test/resources/com/graphhopper/reader/osm/test-osm.xml")
                .putObject("graph.encoded_values", "car_access, car_average_speed")
                .putObject("graph.location", DIR)
                .putObject("profiles_cch", List.of(Collections.singletonMap("profile", PROFILE)))
                .setProfiles(List.of(TestProfiles.accessAndSpeed(PROFILE, "car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void recustomizesConfiguredCCHProfileOverHttp() {
        JsonNode status = clientTarget(app, "/cch/customize").request().get(JsonNode.class);
        assertEquals(1, status.size());
        assertEquals(PROFILE, status.get(0).get("profile").asText());
        assertTrue(status.get(0).get("available").asBoolean());
        assertTrue(status.get(0).get("persisted").asBoolean());
        assertEquals(1, status.get(0).get("metric_generation").asInt());

        JsonNode result = clientTarget(app, "/cch/customize/" + PROFILE)
                .request()
                .post(Entity.text(""), JsonNode.class);
        assertEquals(PROFILE, result.get("profile").asText());
        assertTrue(result.get("persisted").asBoolean());
        assertEquals(2, result.get("metric_generation").asInt());
        assertTrue(result.get("arcs").asInt() > 0);

        JsonNode updatedStatus = clientTarget(app, "/cch/customize").request().get(JsonNode.class);
        assertEquals(2, updatedStatus.get(0).get("metric_generation").asInt());
    }
}
