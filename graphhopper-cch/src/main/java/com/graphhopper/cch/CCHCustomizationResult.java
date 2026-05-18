// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class CCHCustomizationResult {
    private final String profile;
    private final boolean persisted;
    private final int profileHash;
    private final int metricGeneration;
    private final long topologyFingerprint;
    private final int nodes;
    private final int arcs;
    private final long elapsedMillis;

    CCHCustomizationResult(String profile, boolean persisted, int profileHash, int metricGeneration,
                           long topologyFingerprint, int nodes, int arcs, long elapsedMillis) {
        this.profile = profile;
        this.persisted = persisted;
        this.profileHash = profileHash;
        this.metricGeneration = metricGeneration;
        this.topologyFingerprint = topologyFingerprint;
        this.nodes = nodes;
        this.arcs = arcs;
        this.elapsedMillis = elapsedMillis;
    }

    public String getProfile() {
        return profile;
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

    @JsonProperty("elapsed_millis")
    public long getElapsedMillis() {
        return elapsedMillis;
    }
}
