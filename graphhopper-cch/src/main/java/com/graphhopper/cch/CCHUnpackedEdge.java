// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class CCHUnpackedEdge {
    private final int baseEdge;
    private final boolean reverse;
    private final int from;
    private final int to;
    private final double weight;
    private final long millis;
    private final double distance;

    public CCHUnpackedEdge(int baseEdge, boolean reverse, int from, int to, double weight, long millis, double distance) {
        if (baseEdge < 0)
            throw new IllegalArgumentException("baseEdge must be >= 0");
        if (from < 0)
            throw new IllegalArgumentException("from must be >= 0");
        if (to < 0)
            throw new IllegalArgumentException("to must be >= 0");
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0");
        this.baseEdge = baseEdge;
        this.reverse = reverse;
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
    }

    public int getBaseEdge() {
        return baseEdge;
    }

    public boolean isReverse() {
        return reverse;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
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
}
