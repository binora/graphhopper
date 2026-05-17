// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeSet;

public final class BaseGraphCCHSupportBuilder {
    private BaseGraphCCHSupportBuilder() {
    }

    public static CCHInputGraph fromGraph(BaseGraph graph) {
        Objects.requireNonNull(graph, "graph");
        TreeSet<CCHInputEdge> supportEdges = new TreeSet<>();
        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            int baseNode = edge.getBaseNode();
            int adjNode = edge.getAdjNode();
            if (baseNode != adjNode)
                supportEdges.add(new CCHInputEdge(baseNode, adjNode));
        }
        return new CCHInputGraph(graph.getNodes(), Collections.emptyList(), new ArrayList<>(supportEdges));
    }
}
