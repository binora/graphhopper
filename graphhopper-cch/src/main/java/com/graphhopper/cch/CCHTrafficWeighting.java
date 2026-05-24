// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.weighting.AbstractAdjustedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

public final class CCHTrafficWeighting extends AbstractAdjustedWeighting {
    private final CCHTrafficSnapshot snapshot;

    public CCHTrafficWeighting(Weighting superWeighting, CCHTrafficSnapshot snapshot) {
        super(superWeighting);
        this.snapshot = snapshot == null ? CCHTrafficSnapshot.empty() : snapshot;
    }

    public CCHTrafficSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public double calcMinWeightPerDistance() {
        return snapshot.isEmpty() ? superWeighting.calcMinWeightPerDistance() : 0;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        CCHTrafficOverride override = findOverride(edgeState, reverse);
        if (override == null)
            return superWeighting.calcEdgeWeight(edgeState, reverse);
        if (override.isBlocked())
            return Double.POSITIVE_INFINITY;
        double baseWeight = superWeighting.calcEdgeWeight(edgeState, reverse);
        if (baseWeight == Double.POSITIVE_INFINITY)
            return Double.POSITIVE_INFINITY;
        if (!Double.isFinite(baseWeight) || baseWeight < 0)
            return baseWeight;
        long baseMillis = superWeighting.calcEdgeMillis(edgeState, reverse);
        long trafficMillis = calcTrafficMillis(edgeState, reverse, override, baseMillis);
        if (trafficMillis == baseMillis)
            return baseWeight;
        if (baseMillis > 0)
            return baseWeight * ((double) trafficMillis / baseMillis);
        return baseWeight + trafficMillis / 1000d;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        CCHTrafficOverride override = findOverride(edgeState, reverse);
        if (override == null)
            return superWeighting.calcEdgeMillis(edgeState, reverse);
        if (override.isBlocked())
            return Long.MAX_VALUE;
        return calcTrafficMillis(edgeState, reverse, override, superWeighting.calcEdgeMillis(edgeState, reverse));
    }

    @Override
    public String getName() {
        return superWeighting.getName();
    }

    private CCHTrafficOverride findOverride(EdgeIteratorState edgeState, boolean reverse) {
        int edge = edgeState.getEdge();
        boolean baseReverse = reverse;
        if (edgeState instanceof VirtualEdgeIteratorState) {
            int originalEdgeKey = ((VirtualEdgeIteratorState) edgeState).getOriginalEdgeKey();
            if (reverse)
                originalEdgeKey = GHUtility.reverseEdgeKey(originalEdgeKey);
            edge = GHUtility.getEdgeFromEdgeKey(originalEdgeKey);
            baseReverse = (originalEdgeKey & 1) == 1;
        }
        return snapshot.getOverride(edge, baseReverse);
    }

    private static long calcTrafficMillis(EdgeIteratorState edgeState, boolean reverse, CCHTrafficOverride override,
                                          long baseMillis) {
        long millis = override.getSpeedKmh() == null
                ? baseMillis
                : Math.round(edgeState.getDistance() * 3600d / override.getSpeedKmh());
        return saturatedAdd(millis, override.getDelayMillis());
    }

    private static long saturatedAdd(long left, long right) {
        if (left < 0)
            return left;
        if (Long.MAX_VALUE - left < right)
            return Long.MAX_VALUE;
        return left + right;
    }
}
