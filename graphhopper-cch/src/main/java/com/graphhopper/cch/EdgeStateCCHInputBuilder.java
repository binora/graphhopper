// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class EdgeStateCCHInputBuilder {
    private EdgeStateCCHInputBuilder() {
    }

    public static EdgeStateCCHInputGraph fromGraph(BaseGraph graph) {
        Objects.requireNonNull(graph, "graph");

        int[] edgeKeyToState = EdgeStateCCHInputGraph.filledEdgeKeyToState(graph.getEdges());
        List<Integer> stateEdgeKeys = new ArrayList<>();
        List<Integer> stateTailNodes = new ArrayList<>();
        List<Integer> stateHeadNodes = new ArrayList<>();
        List<Integer>[] incomingByHead = lists(graph.getNodes());
        List<Integer>[] outgoingByTail = lists(graph.getNodes());

        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            if (edge.getBaseNode() == edge.getAdjNode())
                continue;
            addState(edgeKeyToState, stateEdgeKeys, stateTailNodes, stateHeadNodes, incomingByHead, outgoingByTail,
                    edge.getEdge(), false, edge.getBaseNode(), edge.getAdjNode());
            addState(edgeKeyToState, stateEdgeKeys, stateTailNodes, stateHeadNodes, incomingByHead, outgoingByTail,
                    edge.getEdge(), true, edge.getAdjNode(), edge.getBaseNode());
        }

        List<CCHInputArc> arcs = new ArrayList<>();
        TreeSet<CCHInputEdge> supportEdges = new TreeSet<>();
        List<Integer> transitionInEdgeKeys = new ArrayList<>();
        List<Integer> transitionViaNodes = new ArrayList<>();
        List<Integer> transitionOutEdgeKeys = new ArrayList<>();
        for (int viaNode = 0; viaNode < graph.getNodes(); viaNode++) {
            Collections.sort(incomingByHead[viaNode]);
            Collections.sort(outgoingByTail[viaNode]);
            for (int inState : incomingByHead[viaNode]) {
                for (int outState : outgoingByTail[viaNode]) {
                    if (inState == outState)
                        continue;
                    int outEdgeKey = stateEdgeKeys.get(outState);
                    int outBaseEdge = GHUtility.getEdgeFromEdgeKey(outEdgeKey);
                    boolean outReverse = (outEdgeKey & 1) == 1;
                    arcs.add(new CCHInputArc(inState, outState, outBaseEdge, outReverse, 0, 0, 0));
                    supportEdges.add(new CCHInputEdge(inState, outState));
                    transitionInEdgeKeys.add(stateEdgeKeys.get(inState));
                    transitionViaNodes.add(viaNode);
                    transitionOutEdgeKeys.add(outEdgeKey);
                }
            }
        }

        CCHInputGraph inputGraph = new CCHInputGraph(stateEdgeKeys.size(), arcs, new ArrayList<>(supportEdges));
        return new EdgeStateCCHInputGraph(
                graph.getNodes(),
                graph.getEdges(),
                inputGraph,
                toIntArray(stateEdgeKeys),
                toIntArray(stateTailNodes),
                toIntArray(stateHeadNodes),
                edgeKeyToState,
                toIntArray(transitionInEdgeKeys),
                toIntArray(transitionViaNodes),
                toIntArray(transitionOutEdgeKeys));
    }

    private static void addState(int[] edgeKeyToState, List<Integer> stateEdgeKeys, List<Integer> stateTailNodes,
                                 List<Integer> stateHeadNodes, List<Integer>[] incomingByHead,
                                 List<Integer>[] outgoingByTail, int edge, boolean reverse, int tail, int head) {
        int edgeKey = GHUtility.createEdgeKey(edge, reverse);
        int state = stateEdgeKeys.size();
        edgeKeyToState[edgeKey] = state;
        stateEdgeKeys.add(edgeKey);
        stateTailNodes.add(tail);
        stateHeadNodes.add(head);
        outgoingByTail[tail].add(state);
        incomingByHead[head].add(state);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] lists(int size) {
        List<Integer>[] lists = new List[size];
        for (int i = 0; i < size; i++) {
            lists[i] = new ArrayList<>();
        }
        return lists;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}
