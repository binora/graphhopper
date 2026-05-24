// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHTrafficOverride {
    private final int edge;
    private final boolean reverse;
    private final Double speedKmh;
    private final long delayMillis;
    private final boolean blocked;

    public CCHTrafficOverride(int edge, boolean reverse, Double speedKmh, long delayMillis, boolean blocked) {
        if (edge < 0)
            throw new IllegalArgumentException("edge must be >= 0");
        if (speedKmh != null && (!Double.isFinite(speedKmh) || speedKmh <= 0))
            throw new IllegalArgumentException("speed_kmh must be finite and > 0");
        if (delayMillis < 0)
            throw new IllegalArgumentException("delay_millis must be >= 0");
        if (!blocked && speedKmh == null && delayMillis == 0)
            throw new IllegalArgumentException("traffic override must set speed_kmh, delay_millis, or blocked=true");
        this.edge = edge;
        this.reverse = reverse;
        this.speedKmh = speedKmh;
        this.delayMillis = delayMillis;
        this.blocked = blocked;
    }

    public int getEdge() {
        return edge;
    }

    public boolean isReverse() {
        return reverse;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public long getDelayMillis() {
        return delayMillis;
    }

    public boolean isBlocked() {
        return blocked;
    }

    long key() {
        return key(edge, reverse);
    }

    static long key(int edge, boolean reverse) {
        if (edge < 0)
            throw new IllegalArgumentException("edge must be >= 0");
        return (((long) edge) << 1) | (reverse ? 1L : 0L);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHTrafficOverride))
            return false;
        CCHTrafficOverride that = (CCHTrafficOverride) o;
        return edge == that.edge
                && reverse == that.reverse
                && delayMillis == that.delayMillis
                && blocked == that.blocked
                && Objects.equals(speedKmh, that.speedKmh);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edge, reverse, speedKmh, delayMillis, blocked);
    }
}
