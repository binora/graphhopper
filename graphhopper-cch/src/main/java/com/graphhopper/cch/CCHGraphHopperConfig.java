// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopperConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CCHGraphHopperConfig extends GraphHopperConfig {
    private List<CCHProfile> cchProfiles = new ArrayList<>();

    public CCHGraphHopperConfig() {
    }

    public CCHGraphHopperConfig(GraphHopperConfig otherConfig) {
        super(otherConfig);
        if (otherConfig instanceof CCHGraphHopperConfig) {
            ((CCHGraphHopperConfig) otherConfig).cchProfiles.forEach(p -> cchProfiles.add(new CCHProfile(p)));
        } else {
            Object rawProfiles = otherConfig.asPMap().getObject("profiles_cch", null);
            if (rawProfiles != null)
                cchProfiles.addAll(parseProfilesCCH(rawProfiles));
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

    public boolean hasCCHProfiles() {
        return !cchProfiles.isEmpty();
    }

    public static boolean hasProfilesCCH(GraphHopperConfig config) {
        if (config instanceof CCHGraphHopperConfig)
            return !((CCHGraphHopperConfig) config).getCCHProfiles().isEmpty();
        Object rawProfiles = config.asPMap().getObject("profiles_cch", null);
        return rawProfiles != null && !parseProfilesCCH(rawProfiles).isEmpty();
    }

    private static List<CCHProfile> parseProfilesCCH(Object rawProfiles) {
        if (!(rawProfiles instanceof List))
            throw new IllegalArgumentException("profiles_cch must be a list");
        List<CCHProfile> parsed = new ArrayList<>();
        for (Object rawProfile : (List<?>) rawProfiles) {
            if (rawProfile instanceof CCHProfile) {
                parsed.add(new CCHProfile((CCHProfile) rawProfile));
            } else if (rawProfile instanceof Map) {
                Object profile = ((Map<?, ?>) rawProfile).get("profile");
                if (!(profile instanceof String))
                    throw new IllegalArgumentException("profiles_cch entries must contain a string 'profile'");
                parsed.add(new CCHProfile((String) profile));
            } else if (rawProfile instanceof String) {
                parsed.add(new CCHProfile((String) rawProfile));
            } else {
                throw new IllegalArgumentException("profiles_cch entries must be objects with a 'profile' field");
            }
        }
        return parsed;
    }
}
