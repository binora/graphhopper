// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHMetricProvenance {
    public enum Type {
        NONE,
        DIRECT,
        SHORTCUT
    }

    private static final CCHMetricProvenance NONE = new CCHMetricProvenance(Type.NONE, CCHStorage.NO_ARC, false,
            CCHStorage.NO_ARC, CCHStorage.NO_ARC);

    private final Type type;
    private final int baseEdge;
    private final boolean reverse;
    private final int firstSkippedArc;
    private final int secondSkippedArc;

    private CCHMetricProvenance(Type type, int baseEdge, boolean reverse, int firstSkippedArc, int secondSkippedArc) {
        this.type = Objects.requireNonNull(type, "type");
        this.baseEdge = baseEdge;
        this.reverse = reverse;
        this.firstSkippedArc = firstSkippedArc;
        this.secondSkippedArc = secondSkippedArc;
    }

    public static CCHMetricProvenance none() {
        return NONE;
    }

    public static CCHMetricProvenance direct(int baseEdge, boolean reverse) {
        if (baseEdge < 0)
            throw new IllegalArgumentException("baseEdge must be >= 0");
        return new CCHMetricProvenance(Type.DIRECT, baseEdge, reverse, CCHStorage.NO_ARC, CCHStorage.NO_ARC);
    }

    public static CCHMetricProvenance shortcut(int firstSkippedArc, int secondSkippedArc) {
        if (firstSkippedArc < 0)
            throw new IllegalArgumentException("firstSkippedArc must be >= 0");
        if (secondSkippedArc < 0)
            throw new IllegalArgumentException("secondSkippedArc must be >= 0");
        return new CCHMetricProvenance(Type.SHORTCUT, CCHStorage.NO_ARC, false, firstSkippedArc, secondSkippedArc);
    }

    public Type getType() {
        return type;
    }

    public boolean isNone() {
        return type == Type.NONE;
    }

    public boolean isDirect() {
        return type == Type.DIRECT;
    }

    public boolean isShortcut() {
        return type == Type.SHORTCUT;
    }

    public int getBaseEdge() {
        return baseEdge;
    }

    public boolean isReverse() {
        return reverse;
    }

    public int getFirstSkippedArc() {
        return firstSkippedArc;
    }

    public int getSecondSkippedArc() {
        return secondSkippedArc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHMetricProvenance))
            return false;
        CCHMetricProvenance that = (CCHMetricProvenance) o;
        return baseEdge == that.baseEdge
                && reverse == that.reverse
                && firstSkippedArc == that.firstSkippedArc
                && secondSkippedArc == that.secondSkippedArc
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, baseEdge, reverse, firstSkippedArc, secondSkippedArc);
    }
}
