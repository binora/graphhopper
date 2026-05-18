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
package com.graphhopper.http;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.cch.CCHGraphHopper;
import com.graphhopper.config.Profile;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphHopperManagedCCHTest {
    @Test
    void profilesCCHUsesCCHGraphHopper() {
        GraphHopperConfig config = configWithProfilesCCH();

        GraphHopperManaged managed = new GraphHopperManaged(config);

        assertInstanceOf(CCHGraphHopper.class, managed.getGraphHopper());
    }

    @Test
    void profilesCCHRejectsGTFS() {
        GraphHopperConfig config = configWithProfilesCCH();
        config.putObject("gtfs.file", "gtfs.zip");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GraphHopperManaged(config));

        assertTrue(error.getMessage().contains("profiles_cch"), error.getMessage());
        assertTrue(error.getMessage().contains("gtfs.file"), error.getMessage());
    }

    private static GraphHopperConfig configWithProfilesCCH() {
        GraphHopperConfig config = new GraphHopperConfig();
        config.setProfiles(List.of(new Profile("profile")));
        config.putObject("graph.location", "target/managed-cch-unused");
        config.putObject("import.osm.ignored_highways", "");
        config.putObject("profiles_cch", List.of(Collections.singletonMap("profile", "profile")));
        return config;
    }
}
