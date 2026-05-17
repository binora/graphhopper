// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class BaseGraphCCHInputBuilder {
    private BaseGraphCCHInputBuilder() {
    }

    public static CCHInputGraph fromGraph(BaseGraph graph, Weighting weighting) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(weighting, "weighting");
        if (weighting.hasTurnCosts())
            throw new IllegalArgumentException("graphhopper-cch v1 only supports node-based routing without turn costs");

        List<CCHInputArc> arcs = new ArrayList<>();
        TreeSet<CCHInputEdge> supportEdges = new TreeSet<>();
        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            int baseNode = edge.getBaseNode();
            int adjNode = edge.getAdjNode();
            if (baseNode == adjNode)
                continue;

            boolean hasForward = addArcIfAccessible(arcs, edge, weighting, baseNode, adjNode, false);
            boolean hasReverse = addArcIfAccessible(arcs, edge, weighting, adjNode, baseNode, true);
            if (hasForward || hasReverse)
                supportEdges.add(new CCHInputEdge(baseNode, adjNode));
        }
        return new CCHInputGraph(graph.getNodes(), arcs, new ArrayList<>(supportEdges));
    }

    private static boolean addArcIfAccessible(List<CCHInputArc> arcs, AllEdgesIterator edge, Weighting weighting,
                                              int from, int to, boolean reverse) {
        double weight = weighting.calcEdgeWeight(edge, reverse);
        if (Double.isNaN(weight))
            throw invalidWeight(edge.getEdge(), reverse, "NaN");
        if (weight == Double.POSITIVE_INFINITY)
            return false;
        if (!Double.isFinite(weight))
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));
        if (weight < 0)
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));

        long millis = weighting.calcEdgeMillis(edge, reverse);
        if (millis < 0)
            throw new IllegalArgumentException("Negative millis for edge " + edge.getEdge() + ", reverse=" + reverse + ": " + millis);
        arcs.add(new CCHInputArc(from, to, edge.getEdge(), reverse, weight, millis, edge.getDistance()));
        return true;
    }

    private static IllegalArgumentException invalidWeight(int edge, boolean reverse, String weight) {
        return new IllegalArgumentException("Invalid weight for edge " + edge + ", reverse=" + reverse + ": " + weight);
    }
}
