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

import com.graphhopper.GraphHopper;
import com.graphhopper.cch.CCHCustomizationBusyException;
import com.graphhopper.cch.CCHCustomizationResult;
import com.graphhopper.cch.CCHCustomizationStatus;
import com.graphhopper.cch.CCHGraphHopper;
import com.graphhopper.jackson.MultiException;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("cch/customize")
@Produces(MediaType.APPLICATION_JSON)
public class CCHCustomizationResource {
    private final CCHGraphHopper graphHopper;

    @Inject
    public CCHCustomizationResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper instanceof CCHGraphHopper ? (CCHGraphHopper) graphHopper : null;
    }

    @GET
    public List<CCHCustomizationStatus> getStatus() {
        return requireCCHGraphHopper().getCCHCustomizationStatus();
    }

    @POST
    @Path("{profile}")
    public Response customize(@PathParam("profile") String profile) {
        try {
            CCHCustomizationResult result = requireCCHGraphHopper().recustomizeCCHProfile(profile);
            return Response.ok(result).build();
        } catch (CCHCustomizationBusyException busy) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new MultiException(busy))
                    .build();
        }
    }

    private CCHGraphHopper requireCCHGraphHopper() {
        if (graphHopper == null)
            throw new IllegalArgumentException("CCH customization is only available when GraphHopper runs with profiles_cch");
        return graphHopper;
    }
}
