// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.Objects;

/**
 * Customized per-arc CCH metric and unpacking provenance.
 * <p>
 * This type stores the result of metric customization. The direct/shortcut provenance API is intentionally small so
 * future edge-state and turn-cost metrics can add richer provenance behind the same customization boundary.
 */
public final class CCHMetric {
    private static final byte NONE = 0;
    private static final byte DIRECT = 1;
    private static final byte SHORTCUT = 2;
    private static final byte EDGE_TRANSITION = 3;

    private final double[] weight;
    private final long[] millis;
    private final double[] distance;
    private final byte[] provenanceType;
    private final int[] baseEdge;
    private final boolean[] reverse;
    private final int[] skippedArc1;
    private final int[] skippedArc2;
    private final long[] tieBreakKey;
    private final int[] incomingEdgeKey;
    private final int[] viaNode;
    private final int[] outgoingEdgeKey;
    private final int[] outgoingBaseEdge;
    private final boolean[] outgoingReverse;
    private final double[] turnWeight;
    private final long[] turnMillis;
    private final double[] edgeWeight;
    private final long[] edgeMillis;
    private final double[] edgeDistance;

    public CCHMetric(int arcs) {
        if (arcs < 0)
            throw new IllegalArgumentException("arcs must be >= 0");
        weight = new double[arcs];
        millis = new long[arcs];
        distance = new double[arcs];
        provenanceType = new byte[arcs];
        baseEdge = new int[arcs];
        reverse = new boolean[arcs];
        skippedArc1 = new int[arcs];
        skippedArc2 = new int[arcs];
        tieBreakKey = new long[arcs];
        incomingEdgeKey = new int[arcs];
        viaNode = new int[arcs];
        outgoingEdgeKey = new int[arcs];
        outgoingBaseEdge = new int[arcs];
        outgoingReverse = new boolean[arcs];
        turnWeight = new double[arcs];
        turnMillis = new long[arcs];
        edgeWeight = new double[arcs];
        edgeMillis = new long[arcs];
        edgeDistance = new double[arcs];
        Arrays.fill(weight, Double.POSITIVE_INFINITY);
        Arrays.fill(millis, Long.MAX_VALUE);
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(baseEdge, CCHStorage.NO_ARC);
        Arrays.fill(skippedArc1, CCHStorage.NO_ARC);
        Arrays.fill(skippedArc2, CCHStorage.NO_ARC);
        Arrays.fill(tieBreakKey, Long.MAX_VALUE);
        Arrays.fill(incomingEdgeKey, CCHStorage.NO_ARC);
        Arrays.fill(viaNode, CCHStorage.NO_ARC);
        Arrays.fill(outgoingEdgeKey, CCHStorage.NO_ARC);
        Arrays.fill(outgoingBaseEdge, CCHStorage.NO_ARC);
    }

    private CCHMetric(double[] weight, long[] millis, double[] distance, byte[] provenanceType,
                      int[] baseEdge, boolean[] reverse, int[] skippedArc1, int[] skippedArc2,
                      long[] tieBreakKey) {
        this.weight = copy(weight);
        this.millis = Objects.requireNonNull(millis, "millis").clone();
        this.distance = copy(distance);
        this.provenanceType = Objects.requireNonNull(provenanceType, "provenanceType").clone();
        this.baseEdge = Objects.requireNonNull(baseEdge, "baseEdge").clone();
        this.reverse = Objects.requireNonNull(reverse, "reverse").clone();
        this.skippedArc1 = Objects.requireNonNull(skippedArc1, "skippedArc1").clone();
        this.skippedArc2 = Objects.requireNonNull(skippedArc2, "skippedArc2").clone();
        this.tieBreakKey = Objects.requireNonNull(tieBreakKey, "tieBreakKey").clone();
        incomingEdgeKey = new int[weight.length];
        viaNode = new int[weight.length];
        outgoingEdgeKey = new int[weight.length];
        outgoingBaseEdge = new int[weight.length];
        outgoingReverse = new boolean[weight.length];
        turnWeight = new double[weight.length];
        turnMillis = new long[weight.length];
        edgeWeight = new double[weight.length];
        edgeMillis = new long[weight.length];
        edgeDistance = new double[weight.length];
        Arrays.fill(incomingEdgeKey, CCHStorage.NO_ARC);
        Arrays.fill(viaNode, CCHStorage.NO_ARC);
        Arrays.fill(outgoingEdgeKey, CCHStorage.NO_ARC);
        Arrays.fill(outgoingBaseEdge, CCHStorage.NO_ARC);
        checkRawLengths();
        checkRawValues();
    }

    static CCHMetric fromRaw(double[] weight, long[] millis, double[] distance, byte[] provenanceType,
                             int[] baseEdge, boolean[] reverse, int[] skippedArc1, int[] skippedArc2,
                             long[] tieBreakKey) {
        return new CCHMetric(weight, millis, distance, provenanceType, baseEdge, reverse, skippedArc1, skippedArc2, tieBreakKey);
    }

    public int getArcs() {
        return weight.length;
    }

    public double getWeight(int cchArc) {
        checkArc(cchArc);
        return weight[cchArc];
    }

    public long getMillis(int cchArc) {
        checkArc(cchArc);
        return millis[cchArc];
    }

    public double getDistance(int cchArc) {
        checkArc(cchArc);
        return distance[cchArc];
    }

    public boolean isFinite(int cchArc) {
        return Double.isFinite(getWeight(cchArc));
    }

    public CCHMetricProvenance getProvenance(int cchArc) {
        checkArc(cchArc);
        switch (provenanceType[cchArc]) {
            case DIRECT:
                return CCHMetricProvenance.direct(baseEdge[cchArc], reverse[cchArc]);
            case SHORTCUT:
                return CCHMetricProvenance.shortcut(skippedArc1[cchArc], skippedArc2[cchArc]);
            case EDGE_TRANSITION:
                return CCHMetricProvenance.edgeTransition(incomingEdgeKey[cchArc], viaNode[cchArc],
                        outgoingEdgeKey[cchArc], outgoingBaseEdge[cchArc], outgoingReverse[cchArc],
                        turnWeight[cchArc], turnMillis[cchArc], edgeWeight[cchArc], edgeMillis[cchArc],
                        edgeDistance[cchArc]);
            case NONE:
                return CCHMetricProvenance.none();
            default:
                throw new IllegalStateException("unknown provenance type: " + provenanceType[cchArc]);
        }
    }

    public boolean isDirect(int cchArc) {
        checkArc(cchArc);
        return provenanceType[cchArc] == DIRECT;
    }

    public boolean isShortcut(int cchArc) {
        checkArc(cchArc);
        return provenanceType[cchArc] == SHORTCUT;
    }

    public boolean isEdgeTransition(int cchArc) {
        checkArc(cchArc);
        return provenanceType[cchArc] == EDGE_TRANSITION;
    }

    public int getBaseEdge(int cchArc) {
        checkArc(cchArc);
        return baseEdge[cchArc];
    }

    public boolean isReverse(int cchArc) {
        checkArc(cchArc);
        return reverse[cchArc];
    }

    public int getSkippedArc1(int cchArc) {
        checkArc(cchArc);
        return skippedArc1[cchArc];
    }

    public int getSkippedArc2(int cchArc) {
        checkArc(cchArc);
        return skippedArc2[cchArc];
    }

    public int getIncomingEdgeKey(int cchArc) {
        checkArc(cchArc);
        return incomingEdgeKey[cchArc];
    }

    public int getViaNode(int cchArc) {
        checkArc(cchArc);
        return viaNode[cchArc];
    }

    public int getOutgoingEdgeKey(int cchArc) {
        checkArc(cchArc);
        return outgoingEdgeKey[cchArc];
    }

    public double getTurnWeight(int cchArc) {
        checkArc(cchArc);
        return turnWeight[cchArc];
    }

    public long getTurnMillis(int cchArc) {
        checkArc(cchArc);
        return turnMillis[cchArc];
    }

    public double getEdgeWeight(int cchArc) {
        checkArc(cchArc);
        return edgeWeight[cchArc];
    }

    public long getEdgeMillis(int cchArc) {
        checkArc(cchArc);
        return edgeMillis[cchArc];
    }

    public double[] getWeightArray() {
        return weight.clone();
    }

    public long[] getMillisArray() {
        return millis.clone();
    }

    public double[] getDistanceArray() {
        return distance.clone();
    }

    byte[] getProvenanceTypeArray() {
        return provenanceType.clone();
    }

    int[] getBaseEdgeArray() {
        return baseEdge.clone();
    }

    boolean[] getReverseArray() {
        return reverse.clone();
    }

    int[] getSkippedArc1Array() {
        return skippedArc1.clone();
    }

    int[] getSkippedArc2Array() {
        return skippedArc2.clone();
    }

    long[] getTieBreakKeyArray() {
        return tieBreakKey.clone();
    }

    void setDirect(CCHMetricCandidate candidate) {
        int cchArc = candidate.getCCHArc();
        checkArc(cchArc);
        CCHMetricProvenance provenance = candidate.getProvenance();
        if (!provenance.isDirect())
            throw new IllegalArgumentException("direct metric candidates must use direct provenance");
        weight[cchArc] = candidate.getWeight();
        millis[cchArc] = candidate.getMillis();
        distance[cchArc] = candidate.getDistance();
        provenanceType[cchArc] = DIRECT;
        baseEdge[cchArc] = provenance.getBaseEdge();
        reverse[cchArc] = provenance.isReverse();
        skippedArc1[cchArc] = CCHStorage.NO_ARC;
        skippedArc2[cchArc] = CCHStorage.NO_ARC;
        tieBreakKey[cchArc] = candidate.getTieBreakKey();
    }

    void setEdgeTransition(CCHMetricCandidate candidate) {
        int cchArc = candidate.getCCHArc();
        checkArc(cchArc);
        CCHMetricProvenance provenance = candidate.getProvenance();
        if (!provenance.isEdgeTransition())
            throw new IllegalArgumentException("edge-based metric candidates must use edge-transition provenance");
        weight[cchArc] = candidate.getWeight();
        millis[cchArc] = candidate.getMillis();
        distance[cchArc] = candidate.getDistance();
        provenanceType[cchArc] = EDGE_TRANSITION;
        baseEdge[cchArc] = CCHStorage.NO_ARC;
        reverse[cchArc] = false;
        skippedArc1[cchArc] = CCHStorage.NO_ARC;
        skippedArc2[cchArc] = CCHStorage.NO_ARC;
        tieBreakKey[cchArc] = candidate.getTieBreakKey();
        incomingEdgeKey[cchArc] = provenance.getIncomingEdgeKey();
        viaNode[cchArc] = provenance.getViaNode();
        outgoingEdgeKey[cchArc] = provenance.getOutgoingEdgeKey();
        outgoingBaseEdge[cchArc] = provenance.getOutgoingBaseEdge();
        outgoingReverse[cchArc] = provenance.isOutgoingReverse();
        turnWeight[cchArc] = provenance.getTurnWeight();
        turnMillis[cchArc] = provenance.getTurnMillis();
        edgeWeight[cchArc] = provenance.getEdgeWeight();
        edgeMillis[cchArc] = provenance.getEdgeMillis();
        edgeDistance[cchArc] = provenance.getEdgeDistance();
    }

    void setShortcut(int cchArc, double weight, long millis, double distance, int firstSkippedArc, int secondSkippedArc) {
        checkArc(cchArc);
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0");
        if (firstSkippedArc < 0)
            throw new IllegalArgumentException("firstSkippedArc must be >= 0");
        if (secondSkippedArc < 0)
            throw new IllegalArgumentException("secondSkippedArc must be >= 0");
        this.weight[cchArc] = weight;
        this.millis[cchArc] = millis;
        this.distance[cchArc] = distance;
        provenanceType[cchArc] = SHORTCUT;
        baseEdge[cchArc] = CCHStorage.NO_ARC;
        reverse[cchArc] = false;
        skippedArc1[cchArc] = firstSkippedArc;
        skippedArc2[cchArc] = secondSkippedArc;
        tieBreakKey[cchArc] = Long.MAX_VALUE;
        incomingEdgeKey[cchArc] = CCHStorage.NO_ARC;
        viaNode[cchArc] = CCHStorage.NO_ARC;
        outgoingEdgeKey[cchArc] = CCHStorage.NO_ARC;
        outgoingBaseEdge[cchArc] = CCHStorage.NO_ARC;
        outgoingReverse[cchArc] = false;
        turnWeight[cchArc] = 0;
        turnMillis[cchArc] = 0;
        edgeWeight[cchArc] = 0;
        edgeMillis[cchArc] = 0;
        edgeDistance[cchArc] = 0;
    }

    boolean directCandidateIsBetter(CCHMetricCandidate candidate) {
        return metricCandidateIsBetter(candidate);
    }

    boolean edgeTransitionCandidateIsBetter(CCHMetricCandidate candidate) {
        return metricCandidateIsBetter(candidate);
    }

    private boolean metricCandidateIsBetter(CCHMetricCandidate candidate) {
        int cchArc = candidate.getCCHArc();
        checkArc(cchArc);
        int byWeight = Double.compare(candidate.getWeight(), weight[cchArc]);
        if (byWeight != 0)
            return byWeight < 0;
        int byMillis = Long.compare(candidate.getMillis(), millis[cchArc]);
        if (byMillis != 0)
            return byMillis < 0;
        int byDistance = Double.compare(candidate.getDistance(), distance[cchArc]);
        if (byDistance != 0)
            return byDistance < 0;
        return candidate.getTieBreakKey() < tieBreakKey[cchArc];
    }

    boolean shortcutCandidateIsBetter(int cchArc, double candidateWeight, int firstSkippedArc, int secondSkippedArc) {
        checkArc(cchArc);
        int byWeight = Double.compare(candidateWeight, weight[cchArc]);
        if (byWeight != 0)
            return byWeight < 0;
        if (provenanceType[cchArc] == DIRECT || provenanceType[cchArc] == EDGE_TRANSITION)
            return false;
        if (provenanceType[cchArc] == NONE)
            return true;
        if (firstSkippedArc != skippedArc1[cchArc])
            return firstSkippedArc < skippedArc1[cchArc];
        return secondSkippedArc < skippedArc2[cchArc];
    }

    private void checkArc(int cchArc) {
        if (cchArc < 0 || cchArc >= weight.length)
            throw new IllegalArgumentException("cchArc outside [0," + weight.length + "): " + cchArc);
    }

    private void checkRawLengths() {
        int arcs = weight.length;
        if (millis.length != arcs || distance.length != arcs || provenanceType.length != arcs
                || baseEdge.length != arcs || reverse.length != arcs || skippedArc1.length != arcs
                || skippedArc2.length != arcs || tieBreakKey.length != arcs) {
            throw new IllegalArgumentException("raw CCH metric arrays must all have length " + arcs);
        }
    }

    private void checkRawValues() {
        for (int arc = 0; arc < weight.length; arc++) {
            byte type = provenanceType[arc];
            if (type != NONE && type != DIRECT && type != SHORTCUT)
                throw new IllegalArgumentException("unknown CCH metric provenance type at arc " + arc + ": " + type);
            if (Double.isNaN(weight[arc]) || weight[arc] < 0)
                throw new IllegalArgumentException("invalid CCH metric weight at arc " + arc + ": " + weight[arc]);
            if (millis[arc] < 0)
                throw new IllegalArgumentException("invalid CCH metric millis at arc " + arc + ": " + millis[arc]);
            if (Double.isNaN(distance[arc]) || distance[arc] < 0)
                throw new IllegalArgumentException("invalid CCH metric distance at arc " + arc + ": " + distance[arc]);
            if (type == DIRECT && baseEdge[arc] < 0)
                throw new IllegalArgumentException("direct CCH metric arc " + arc + " must reference a base edge");
            if (type == SHORTCUT && (skippedArc1[arc] < 0 || skippedArc2[arc] < 0))
                throw new IllegalArgumentException("shortcut CCH metric arc " + arc + " must reference skipped arcs");
        }
    }

    private static double[] copy(double[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }
}
