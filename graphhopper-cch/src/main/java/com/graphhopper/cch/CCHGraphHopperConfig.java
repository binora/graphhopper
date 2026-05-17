// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopperConfig;

import java.util.ArrayList;
import java.util.List;

public class CCHGraphHopperConfig extends GraphHopperConfig {
    private List<CCHProfile> cchProfiles = new ArrayList<>();

    public CCHGraphHopperConfig() {
    }

    public CCHGraphHopperConfig(GraphHopperConfig otherConfig) {
        super(otherConfig);
        if (otherConfig instanceof CCHGraphHopperConfig) {
            ((CCHGraphHopperConfig) otherConfig).cchProfiles.forEach(p -> cchProfiles.add(new CCHProfile(p)));
        }
    }

    public List<CCHProfile> getCCHProfiles() {
        return cchProfiles;
    }

    @JsonProperty("profiles_cch")
    public CCHGraphHopperConfig setCCHProfiles(List<CCHProfile> cchProfiles) {
        this.cchProfiles = cchProfiles;
        return this;
    }
}
