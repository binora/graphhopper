// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class CCHCustomizationStatus {
    private final String profile;
    private final boolean available;
    private final boolean busy;
    private final boolean persisted;
    private final int profileHash;
    private final int metricGeneration;
    private final long topologyFingerprint;
    private final int nodes;
    private final int arcs;

    CCHCustomizationStatus(String profile, boolean available, boolean busy, boolean persisted, int profileHash,
                           int metricGeneration, long topologyFingerprint, int nodes, int arcs) {
        this.profile = profile;
        this.available = available;
        this.busy = busy;
        this.persisted = persisted;
        this.profileHash = profileHash;
        this.metricGeneration = metricGeneration;
        this.topologyFingerprint = topologyFingerprint;
        this.nodes = nodes;
        this.arcs = arcs;
    }

    public String getProfile() {
        return profile;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean isPersisted() {
        return persisted;
    }

    @JsonProperty("profile_hash")
    public int getProfileHash() {
        return profileHash;
    }

    @JsonProperty("metric_generation")
    public int getMetricGeneration() {
        return metricGeneration;
    }

    @JsonProperty("topology_fingerprint")
    public long getTopologyFingerprint() {
        return topologyFingerprint;
    }

    public int getNodes() {
        return nodes;
    }

    public int getArcs() {
        return arcs;
    }
}
