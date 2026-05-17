// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.querygraph.QueryGraph;

import java.util.List;
import java.util.Objects;

/**
 * GraphHopper routing adapter for CCH queries.
 */
public final class CCHPathCalculator implements PathCalculator {
    private final RoutingCCHGraph routingCCHGraph;
    private final QueryGraph queryGraph;
    private String debug = "";
    private int visitedNodes;

    public CCHPathCalculator(RoutingCCHGraph routingCCHGraph, QueryGraph queryGraph) {
        this.routingCCHGraph = Objects.requireNonNull(routingCCHGraph, "routingCCHGraph");
        this.queryGraph = Objects.requireNonNull(queryGraph, "queryGraph");
    }

    public RoutingCCHGraph getRoutingCCHGraph() {
        return routingCCHGraph;
    }

    public QueryGraph getQueryGraph() {
        return queryGraph;
    }

    @Override
    public List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions) {
        Objects.requireNonNull(edgeRestrictions, "edgeRestrictions");
        debug = ", cch-routing:not-implemented";
        visitedNodes = 0;
        throw new UnsupportedOperationException("CCH query and path unpacking are implemented by later graphhopper-cch beads");
    }

    @Override
    public String getDebugString() {
        return debug;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }
}
