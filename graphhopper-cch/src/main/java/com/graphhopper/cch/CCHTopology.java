// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.Objects;

public final class CCHTopology {
    private final CCHNodeOrder nodeOrder;
    private final int[] upFirstOut;
    private final int[] upTail;
    private final int[] upHead;
    private final int[] downFirstOut;
    private final int[] downTail;
    private final int[] downHead;
    private final int[] inputArcToCCHArc;
    private final boolean[] fillArc;
    private final int[] skippedArc1;
    private final int[] skippedArc2;

    CCHTopology(CCHNodeOrder nodeOrder, int[] upFirstOut, int[] upTail, int[] upHead,
                int[] downFirstOut, int[] downTail, int[] downHead, int[] inputArcToCCHArc,
                boolean[] fillArc, int[] skippedArc1, int[] skippedArc2) {
        this.nodeOrder = Objects.requireNonNull(nodeOrder, "nodeOrder");
        this.upFirstOut = copy(upFirstOut);
        this.upTail = copy(upTail);
        this.upHead = copy(upHead);
        this.downFirstOut = copy(downFirstOut);
        this.downTail = copy(downTail);
        this.downHead = copy(downHead);
        this.inputArcToCCHArc = copy(inputArcToCCHArc);
        this.fillArc = Objects.requireNonNull(fillArc, "fillArc").clone();
        this.skippedArc1 = copy(skippedArc1);
        this.skippedArc2 = copy(skippedArc2);
        checkLengths();
    }

    public int getNodes() {
        return nodeOrder.getNodes();
    }

    public CCHNodeOrder getNodeOrder() {
        return nodeOrder;
    }

    public int getUpArcs() {
        return upHead.length;
    }

    public int getDownArcs() {
        return downHead.length;
    }

    public int getArcs() {
        return getUpArcs() + getDownArcs();
    }

    public int getDownArcId(int downArc) {
        if (downArc < 0 || downArc >= getDownArcs())
            throw new IllegalArgumentException("downArc outside [0," + getDownArcs() + "): " + downArc);
        return getUpArcs() + downArc;
    }

    public int getUpArcStart(int node) {
        return upFirstOut[node];
    }

    public int getUpArcEnd(int node) {
        return upFirstOut[node + 1];
    }

    public int getUpTail(int upArc) {
        checkUpArc(upArc);
        return upTail[upArc];
    }

    public int getUpHead(int upArc) {
        checkUpArc(upArc);
        return upHead[upArc];
    }

    public int getDownArcStart(int node) {
        return downFirstOut[node];
    }

    public int getDownArcEnd(int node) {
        return downFirstOut[node + 1];
    }

    public int getDownTail(int downArc) {
        checkDownArc(downArc);
        return downTail[downArc];
    }

    public int getDownHead(int downArc) {
        checkDownArc(downArc);
        return downHead[downArc];
    }

    public int getInputArcCCHArc(int inputArc) {
        if (inputArc < 0 || inputArc >= inputArcToCCHArc.length)
            throw new IllegalArgumentException("inputArc outside [0," + inputArcToCCHArc.length + "): " + inputArc);
        return inputArcToCCHArc[inputArc];
    }

    public boolean isFillArc(int cchArc) {
        checkCCHArc(cchArc);
        return fillArc[cchArc];
    }

    public int getSkippedArc1(int cchArc) {
        checkCCHArc(cchArc);
        return skippedArc1[cchArc];
    }

    public int getSkippedArc2(int cchArc) {
        checkCCHArc(cchArc);
        return skippedArc2[cchArc];
    }

    public int[] getUpFirstOutArray() {
        return upFirstOut.clone();
    }

    public int[] getUpTailArray() {
        return upTail.clone();
    }

    public int[] getUpHeadArray() {
        return upHead.clone();
    }

    public int[] getDownFirstOutArray() {
        return downFirstOut.clone();
    }

    public int[] getDownTailArray() {
        return downTail.clone();
    }

    public int[] getDownHeadArray() {
        return downHead.clone();
    }

    public int[] getInputArcCCHArcArray() {
        return inputArcToCCHArc.clone();
    }

    public CCHStorage toStorage() {
        int[] baseEdge = new int[getArcs()];
        Arrays.fill(baseEdge, CCHStorage.NO_ARC);
        return CCHStorage.builder(getNodes())
                .nodeOrder(nodeOrder)
                .upwardGraph(upFirstOut, upHead)
                .downwardGraph(downFirstOut, downHead)
                .baseEdgeMapping(baseEdge, new boolean[getArcs()])
                .build();
    }

    private void checkLengths() {
        if (upFirstOut.length != getNodes() + 1)
            throw new IllegalArgumentException("upFirstOut length must be nodes + 1");
        if (downFirstOut.length != getNodes() + 1)
            throw new IllegalArgumentException("downFirstOut length must be nodes + 1");
        if (upTail.length != upHead.length)
            throw new IllegalArgumentException("upTail and upHead lengths differ");
        if (downTail.length != downHead.length)
            throw new IllegalArgumentException("downTail and downHead lengths differ");
        if (fillArc.length != getArcs())
            throw new IllegalArgumentException("fillArc length must match total arcs");
        if (skippedArc1.length != getArcs() || skippedArc2.length != getArcs())
            throw new IllegalArgumentException("skipped arc metadata length must match total arcs");
    }

    private void checkUpArc(int upArc) {
        if (upArc < 0 || upArc >= getUpArcs())
            throw new IllegalArgumentException("upArc outside [0," + getUpArcs() + "): " + upArc);
    }

    private void checkDownArc(int downArc) {
        if (downArc < 0 || downArc >= getDownArcs())
            throw new IllegalArgumentException("downArc outside [0," + getDownArcs() + "): " + downArc);
    }

    private void checkCCHArc(int cchArc) {
        if (cchArc < 0 || cchArc >= getArcs())
            throw new IllegalArgumentException("cchArc outside [0," + getArcs() + "): " + cchArc);
    }

    private static int[] copy(int[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }
}
