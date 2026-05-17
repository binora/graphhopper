// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class CCHInputArc {
    private final int from;
    private final int to;
    private final int baseEdge;
    private final boolean reverse;
    private final double weight;
    private final long millis;
    private final double distance;

    public CCHInputArc(int from, int to, int baseEdge, boolean reverse, double weight, long millis, double distance) {
        this.from = from;
        this.to = to;
        this.baseEdge = baseEdge;
        this.reverse = reverse;
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
        if (from < 0)
            throw new IllegalArgumentException("from must be >= 0");
        if (to < 0)
            throw new IllegalArgumentException("to must be >= 0");
        if (baseEdge < 0)
            throw new IllegalArgumentException("baseEdge must be >= 0");
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0");
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public int getBaseEdge() {
        return baseEdge;
    }

    public boolean isReverse() {
        return reverse;
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
