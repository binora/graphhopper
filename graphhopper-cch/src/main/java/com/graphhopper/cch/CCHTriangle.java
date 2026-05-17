// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHTriangle {
    private final CCHTriangleType type;
    private final int targetArc;
    private final int firstWitnessArc;
    private final int secondWitnessArc;

    public CCHTriangle(CCHTriangleType type, int targetArc, int firstWitnessArc, int secondWitnessArc) {
        this.type = Objects.requireNonNull(type, "type");
        this.targetArc = targetArc;
        this.firstWitnessArc = firstWitnessArc;
        this.secondWitnessArc = secondWitnessArc;
    }

    public CCHTriangleType getType() {
        return type;
    }

    public int getTargetArc() {
        return targetArc;
    }

    public int getFirstWitnessArc() {
        return firstWitnessArc;
    }

    public int getSecondWitnessArc() {
        return secondWitnessArc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHTriangle))
            return false;
        CCHTriangle that = (CCHTriangle) o;
        return targetArc == that.targetArc
                && firstWitnessArc == that.firstWitnessArc
                && secondWitnessArc == that.secondWitnessArc
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, targetArc, firstWitnessArc, secondWitnessArc);
    }

    @Override
    public String toString() {
        return "CCHTriangle{" +
                "type=" + type +
                ", targetArc=" + targetArc +
                ", firstWitnessArc=" + firstWitnessArc +
                ", secondWitnessArc=" + secondWitnessArc +
                '}';
    }
}
