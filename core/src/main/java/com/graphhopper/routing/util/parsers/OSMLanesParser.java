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

package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.IntsRef;

/**
 * https://wiki.openstreetmap.org/wiki/Key:lanes
 */
public class OSMLanesParser implements TagParser {
    private final IntEncodedValue lanesEnc;

    public OSMLanesParser(IntEncodedValue lanesEnc) {
        this.lanesEnc = lanesEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        /*
         Priority handling (simple and explicit):
         1) lanes:forward / lanes:backward (in OSM way direction) take precedence.
         2) Otherwise, use oneway + lanes to assign all lanes to the oneway direction (other side = 0).
         3) Otherwise, for two-way: subtract lanes:both_ways and split the remainder evenly.
         4) Fallback when nothing is tagged: default to 1/1.
         Validation: ignore invalid/negative, clamp to 0..6.
         */
        Integer lanesFwd = parseCount(way.getTag("lanes:forward"));
        Integer lanesBwd = parseCount(way.getTag("lanes:backward"));
        Integer lanesBothWays = parseCount(way.getTag("lanes:both_ways"));
        Integer lanesTotal = parseCount(way.getTag("lanes"));

        boolean onewayFwd = way.hasTag("oneway", "yes", "true", "1");
        boolean onewayBwd = way.hasTag("oneway", "-1");

        // 1) Directional tags present → use them directly
        if (lanesFwd != null || lanesBwd != null) {
            int fwd = lanesFwd != null ? clamp(lanesFwd) : -1;
            int bwd = lanesBwd != null ? clamp(lanesBwd) : -1;
            if (lanesTotal != null) {
                int both = Math.max(0, lanesBothWays != null ? lanesBothWays : 0);
                int dirTotal = Math.max(0, lanesTotal - both);
                if (fwd < 0) fwd = clamp(Math.max(0, dirTotal - Math.max(0, bwd)));
                if (bwd < 0) bwd = clamp(Math.max(0, dirTotal - Math.max(0, fwd)));
            } else {
                if (fwd < 0) fwd = 0;
                if (bwd < 0) bwd = 0;
            }
            setBoth(edgeId, edgeIntAccess, fwd, bwd);
            return;
        }

        // 2) Oneway
        if (onewayFwd) {
            setBoth(edgeId, edgeIntAccess, clamp(lanesTotal != null ? lanesTotal : 1), 0);
            return;
        }
        if (onewayBwd) {
            setBoth(edgeId, edgeIntAccess, 0, clamp(lanesTotal != null ? lanesTotal : 1));
            return;
        }

        // 3) Two-way
        if (lanesTotal != null) {
            int both = Math.max(0, lanesBothWays != null ? lanesBothWays : 0);
            if (lanesTotal == 1 && both == 0) {
                // Single-lane two-way: approximate as 1/1 for heuristics
                setBoth(edgeId, edgeIntAccess, 1, 1);
                return;
            }
            int dirTotal = Math.max(0, lanesTotal - both);
            int half = dirTotal / 2;
            setBoth(edgeId, edgeIntAccess, clamp(half), clamp(dirTotal - half));
            return;
        }

        // 4) No info → default 1/1
        setBoth(edgeId, edgeIntAccess, 1, 1);
    }

    private static Integer parseCount(String raw) {
        if (raw == null) return null;
        String[] toks = raw.split(";|\\.");
        if (toks.length == 0) return null;
        try {
            int val = Integer.parseInt(toks[0].trim());
            if (val < 0) return null;
            return val;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        return Math.min(6, v);
    }

    private void setBoth(int edgeId, EdgeIntAccess edgeIntAccess, int fwd, int bwd) {
        lanesEnc.setInt(false, edgeId, edgeIntAccess, fwd);
        lanesEnc.setInt(true, edgeId, edgeIntAccess, bwd);
    }
}
