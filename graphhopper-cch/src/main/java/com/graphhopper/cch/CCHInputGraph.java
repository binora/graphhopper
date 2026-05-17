// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CCHInputGraph {
    private final int nodes;
    private final List<CCHInputArc> arcs;
    private final List<CCHInputEdge> supportEdges;

    public CCHInputGraph(int nodes, List<CCHInputArc> arcs, List<CCHInputEdge> supportEdges) {
        if (nodes < 0)
            throw new IllegalArgumentException("nodes must be >= 0");
        this.nodes = nodes;
        this.arcs = copy(arcs);
        this.supportEdges = copy(supportEdges);
        for (CCHInputArc arc : this.arcs) {
            if (arc.getFrom() >= nodes || arc.getTo() >= nodes)
                throw new IllegalArgumentException("arc endpoint outside node range");
        }
        for (CCHInputEdge edge : this.supportEdges) {
            if (edge.getNodeA() >= nodes || edge.getNodeB() >= nodes)
                throw new IllegalArgumentException("support edge endpoint outside node range");
        }
    }

    public int getNodes() {
        return nodes;
    }

    public int getArcs() {
        return arcs.size();
    }

    public CCHInputArc getArc(int index) {
        return arcs.get(index);
    }

    public List<CCHInputArc> getAllArcs() {
        return arcs;
    }

    public int getSupportEdges() {
        return supportEdges.size();
    }

    public CCHInputEdge getSupportEdge(int index) {
        return supportEdges.get(index);
    }

    public List<CCHInputEdge> getAllSupportEdges() {
        return supportEdges;
    }

    private static <T> List<T> copy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, "values")));
    }
}
