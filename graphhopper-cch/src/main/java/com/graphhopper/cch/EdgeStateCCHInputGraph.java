// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.util.GHUtility;

import java.util.Arrays;
import java.util.Objects;

public final class EdgeStateCCHInputGraph {
    public static final int NO_STATE = -1;

    private final int baseNodes;
    private final int baseEdges;
    private final CCHInputGraph inputGraph;
    private final int[] stateEdgeKey;
    private final int[] stateTailNode;
    private final int[] stateHeadNode;
    private final int[] edgeKeyToState;
    private final int[] transitionInEdgeKey;
    private final int[] transitionViaNode;
    private final int[] transitionOutEdgeKey;

    EdgeStateCCHInputGraph(int baseNodes, int baseEdges, CCHInputGraph inputGraph, int[] stateEdgeKey,
                           int[] stateTailNode, int[] stateHeadNode, int[] edgeKeyToState,
                           int[] transitionInEdgeKey, int[] transitionViaNode, int[] transitionOutEdgeKey) {
        if (baseNodes < 0)
            throw new IllegalArgumentException("baseNodes must be >= 0");
        if (baseEdges < 0)
            throw new IllegalArgumentException("baseEdges must be >= 0");
        this.baseNodes = baseNodes;
        this.baseEdges = baseEdges;
        this.inputGraph = Objects.requireNonNull(inputGraph, "inputGraph");
        this.stateEdgeKey = copy(stateEdgeKey);
        this.stateTailNode = copy(stateTailNode);
        this.stateHeadNode = copy(stateHeadNode);
        this.edgeKeyToState = copy(edgeKeyToState);
        this.transitionInEdgeKey = copy(transitionInEdgeKey);
        this.transitionViaNode = copy(transitionViaNode);
        this.transitionOutEdgeKey = copy(transitionOutEdgeKey);
        checkLengths();
        checkValues();
    }

    public int getBaseNodes() {
        return baseNodes;
    }

    public int getBaseEdges() {
        return baseEdges;
    }

    public int getStates() {
        return stateEdgeKey.length;
    }

    public CCHInputGraph getInputGraph() {
        return inputGraph;
    }

    public int getStateForEdgeKey(int edgeKey) {
        if (edgeKey < 0 || edgeKey >= edgeKeyToState.length)
            return NO_STATE;
        return edgeKeyToState[edgeKey];
    }

    public int getStateForBaseEdge(int baseEdge, boolean reverse) {
        if (baseEdge < 0 || baseEdge >= baseEdges)
            return NO_STATE;
        return getStateForEdgeKey(GHUtility.createEdgeKey(baseEdge, reverse));
    }

    public int getStateEdgeKey(int state) {
        checkState(state);
        return stateEdgeKey[state];
    }

    public int getStateBaseEdge(int state) {
        return GHUtility.getEdgeFromEdgeKey(getStateEdgeKey(state));
    }

    public boolean isStateReverse(int state) {
        return (getStateEdgeKey(state) & 1) == 1;
    }

    public int getStateTailNode(int state) {
        checkState(state);
        return stateTailNode[state];
    }

    public int getStateHeadNode(int state) {
        checkState(state);
        return stateHeadNode[state];
    }

    public int getTransitionInEdgeKey(int inputArc) {
        checkInputArc(inputArc);
        return transitionInEdgeKey[inputArc];
    }

    public int getTransitionViaNode(int inputArc) {
        checkInputArc(inputArc);
        return transitionViaNode[inputArc];
    }

    public int getTransitionOutEdgeKey(int inputArc) {
        checkInputArc(inputArc);
        return transitionOutEdgeKey[inputArc];
    }

    public int[] getStateEdgeKeyArray() {
        return stateEdgeKey.clone();
    }

    public int[] getStateTailNodeArray() {
        return stateTailNode.clone();
    }

    public int[] getStateHeadNodeArray() {
        return stateHeadNode.clone();
    }

    public int[] getEdgeKeyToStateArray() {
        return edgeKeyToState.clone();
    }

    public int[] getTransitionInEdgeKeyArray() {
        return transitionInEdgeKey.clone();
    }

    public int[] getTransitionViaNodeArray() {
        return transitionViaNode.clone();
    }

    public int[] getTransitionOutEdgeKeyArray() {
        return transitionOutEdgeKey.clone();
    }

    private void checkLengths() {
        if (stateTailNode.length != getStates() || stateHeadNode.length != getStates())
            throw new IllegalArgumentException("state arrays must have the same length");
        if (edgeKeyToState.length != baseEdges * 2)
            throw new IllegalArgumentException("edgeKeyToState length must be baseEdges * 2");
        int inputArcs = inputGraph.getArcs();
        if (transitionInEdgeKey.length != inputArcs || transitionViaNode.length != inputArcs
                || transitionOutEdgeKey.length != inputArcs) {
            throw new IllegalArgumentException("transition arrays must match input arc count");
        }
        if (inputGraph.getNodes() != getStates())
            throw new IllegalArgumentException("input graph nodes must match edge-state count");
    }

    private void checkValues() {
        boolean[] seenStates = new boolean[getStates()];
        for (int state = 0; state < getStates(); state++) {
            int edgeKey = stateEdgeKey[state];
            if (edgeKey < 0 || edgeKey >= edgeKeyToState.length)
                throw new IllegalArgumentException("state edge key outside range: " + edgeKey);
            if (edgeKeyToState[edgeKey] != state)
                throw new IllegalArgumentException("edge key " + edgeKey + " does not map back to state " + state);
            if (stateTailNode[state] < 0 || stateTailNode[state] >= baseNodes)
                throw new IllegalArgumentException("state tail outside base node range: " + stateTailNode[state]);
            if (stateHeadNode[state] < 0 || stateHeadNode[state] >= baseNodes)
                throw new IllegalArgumentException("state head outside base node range: " + stateHeadNode[state]);
            seenStates[state] = true;
        }
        for (int state : edgeKeyToState) {
            if (state != NO_STATE && (state < 0 || state >= getStates() || !seenStates[state]))
                throw new IllegalArgumentException("edge key maps to invalid state: " + state);
        }
        for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
            CCHInputArc arc = inputGraph.getArc(inputArc);
            int inState = getStateForEdgeKey(transitionInEdgeKey[inputArc]);
            int outState = getStateForEdgeKey(transitionOutEdgeKey[inputArc]);
            if (inState != arc.getFrom() || outState != arc.getTo())
                throw new IllegalArgumentException("transition metadata does not match input arc " + inputArc);
            if (transitionViaNode[inputArc] != stateHeadNode[inState]
                    || transitionViaNode[inputArc] != stateTailNode[outState]) {
                throw new IllegalArgumentException("transition " + inputArc + " is not contiguous at via node");
            }
        }
    }

    private void checkState(int state) {
        if (state < 0 || state >= getStates())
            throw new IllegalArgumentException("state outside [0," + getStates() + "): " + state);
    }

    private void checkInputArc(int inputArc) {
        if (inputArc < 0 || inputArc >= inputGraph.getArcs())
            throw new IllegalArgumentException("inputArc outside [0," + inputGraph.getArcs() + "): " + inputArc);
    }

    private static int[] copy(int[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }

    static int[] filledEdgeKeyToState(int baseEdges) {
        int[] edgeKeyToState = new int[baseEdges * 2];
        Arrays.fill(edgeKeyToState, NO_STATE);
        return edgeKeyToState;
    }
}
