// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHMetricCandidate {
    private final int cchArc;
    private final double weight;
    private final long millis;
    private final double distance;
    private final CCHMetricProvenance provenance;
    private final long tieBreakKey;

    public CCHMetricCandidate(int cchArc, double weight, long millis, double distance,
                              CCHMetricProvenance provenance, long tieBreakKey) {
        if (cchArc < 0)
            throw new IllegalArgumentException("cchArc must be >= 0");
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0");
        this.cchArc = cchArc;
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.tieBreakKey = tieBreakKey;
    }

    public int getCCHArc() {
        return cchArc;
    }

    public double getWeight() {
        return weight;
    }

    public long getMillis() {
        return millis;
    }

    public double getDistance() {
        return distance;
    }

    public CCHMetricProvenance getProvenance() {
        return provenance;
    }

    public long getTieBreakKey() {
        return tieBreakKey;
    }
}
