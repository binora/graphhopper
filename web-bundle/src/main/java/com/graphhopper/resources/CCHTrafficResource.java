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
package com.graphhopper.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopper;
import com.graphhopper.cch.CCHCustomizationBusyException;
import com.graphhopper.cch.CCHGraphHopper;
import com.graphhopper.cch.CCHTrafficCustomizationResult;
import com.graphhopper.cch.CCHTrafficSnapshot;
import com.graphhopper.cch.CCHTrafficSnapshotInfo;
import com.graphhopper.cch.CCHTrafficStatus;
import com.graphhopper.jackson.MultiException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Path("cch/traffic")
@Produces(MediaType.APPLICATION_JSON)
public class CCHTrafficResource {
    private final CCHGraphHopper graphHopper;

    @Inject
    public CCHTrafficResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper instanceof CCHGraphHopper ? (CCHGraphHopper) graphHopper : null;
    }

    @GET
    @Path("status")
    public CCHTrafficStatus getStatus() {
        return requireCCHGraphHopper().getCCHTrafficStatus();
    }

    @POST
    @Path("snapshots")
    @Consumes(MediaType.APPLICATION_JSON)
    public CCHTrafficSnapshotInfo putSnapshot(TrafficSnapshotRequest request) {
        return requireCCHGraphHopper().putCCHTrafficSnapshot(request.toSnapshot());
    }

    @POST
    @Path("snapshots/{id}/activate")
    public CCHTrafficStatus activateSnapshot(@PathParam("id") String id) {
        return requireCCHGraphHopper().activateCCHTrafficSnapshot(id);
    }

    @POST
    @Path("snapshots/{id}/activate-and-customize/{profile}")
    public Response activateSnapshotAndCustomize(@PathParam("id") String id, @PathParam("profile") String profile) {
        try {
            CCHTrafficCustomizationResult result = requireCCHGraphHopper().activateCCHTrafficSnapshotAndRecustomize(profile, id);
            return Response.ok(result).build();
        } catch (CCHCustomizationBusyException busy) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new MultiException(busy))
                    .build();
        }
    }

    private CCHGraphHopper requireCCHGraphHopper() {
        if (graphHopper == null)
            throw new IllegalArgumentException("CCH traffic customization is only available when GraphHopper runs with profiles_cch");
        return graphHopper;
    }

    public static final class TrafficSnapshotRequest {
        private String id;
        private long createdMillis;
        private List<TrafficOverrideRequest> entries = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JsonProperty("created_millis")
        public long getCreatedMillis() {
            return createdMillis;
        }

        @JsonProperty("created_millis")
        public void setCreatedMillis(long createdMillis) {
            this.createdMillis = createdMillis;
        }

        public List<TrafficOverrideRequest> getEntries() {
            return entries;
        }

        public void setEntries(List<TrafficOverrideRequest> entries) {
            this.entries = entries == null ? new ArrayList<>() : entries;
        }

        CCHTrafficSnapshot toSnapshot() {
            CCHTrafficSnapshot.Builder builder = CCHTrafficSnapshot.builder(id);
            if (createdMillis > 0)
                builder.setCreatedMillis(createdMillis);
            for (TrafficOverrideRequest entry : entries) {
                builder.override(entry.edge, entry.reverse, entry.speedKmh,
                        entry.delayMillis == null ? 0 : entry.delayMillis, entry.blocked);
            }
            return builder.build();
        }
    }

    public static final class TrafficOverrideRequest {
        private int edge;
        private boolean reverse;
        private Double speedKmh;
        private Long delayMillis;
        private boolean blocked;

        public int getEdge() {
            return edge;
        }

        public void setEdge(int edge) {
            this.edge = edge;
        }

        public boolean isReverse() {
            return reverse;
        }

        public void setReverse(boolean reverse) {
            this.reverse = reverse;
        }

        @JsonProperty("speed_kmh")
        public Double getSpeedKmh() {
            return speedKmh;
        }

        @JsonProperty("speed_kmh")
        public void setSpeedKmh(Double speedKmh) {
            this.speedKmh = speedKmh;
        }

        @JsonProperty("delay_millis")
        public Long getDelayMillis() {
            return delayMillis;
        }

        @JsonProperty("delay_millis")
        public void setDelayMillis(Long delayMillis) {
            this.delayMillis = delayMillis;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public void setBlocked(boolean blocked) {
            this.blocked = blocked;
        }
    }
}
