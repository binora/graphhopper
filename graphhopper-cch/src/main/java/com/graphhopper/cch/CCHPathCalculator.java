// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.util.StopWatch;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * GraphHopper routing adapter for CCH queries.
 */
public final class CCHPathCalculator implements PathCalculator {
    private final RoutingCCHGraph routingCCHGraph;
    private final QueryGraph queryGraph;
    private final NodeBasedCCHQuery query;
    private final CCHPathUnpacker pathUnpacker;
    private final String initDebug;
    private String debug = "";
    private int visitedNodes;

    public CCHPathCalculator(RoutingCCHGraph routingCCHGraph, QueryGraph queryGraph) {
        this.routingCCHGraph = Objects.requireNonNull(routingCCHGraph, "routingCCHGraph");
        this.queryGraph = Objects.requireNonNull(queryGraph, "queryGraph");
        StopWatch sw = new StopWatch().start();
        this.query = new NodeBasedCCHQuery(routingCCHGraph.getTopology(), routingCCHGraph.getMetric());
        this.pathUnpacker = new CCHPathUnpacker(routingCCHGraph.getTopology(), routingCCHGraph.getMetric());
        this.initDebug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " us";
        this.debug = initDebug;
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
        checkRestrictions(edgeRestrictions);
        checkBaseGraphEndpoint("from", from);
        checkBaseGraphEndpoint("to", to);

        StopWatch sw = new StopWatch().start();
        debug = initDebug;
        CCHQueryResult result = query.calc(from, to);
        Path path = pathUnpacker.toPath(queryGraph, result);
        visitedNodes = result.getVisitedNodes();
        debug += ", cch-routing:" + sw.stop().getMillis() + " ms";
        return Collections.singletonList(path);
    }

    @Override
    public String getDebugString() {
        return debug;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private static void checkRestrictions(EdgeRestrictions edgeRestrictions) {
        if (!edgeRestrictions.getUnfavoredEdges().isEmpty())
            throw new IllegalArgumentException("Using unfavored edges is currently not supported for graphhopper-cch");
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE)
            throw new IllegalArgumentException("Source/target edge restrictions are currently not supported for graphhopper-cch");
    }

    private void checkBaseGraphEndpoint(String name, int node) {
        if (node < 0 || node >= routingCCHGraph.getNodes())
            throw new IllegalArgumentException("graphhopper-cch currently supports only base graph nodes; " + name
                    + "=" + node + " is outside [0," + routingCCHGraph.getNodes()
                    + "). QueryGraph virtual nodes are not supported yet");
    }
}
