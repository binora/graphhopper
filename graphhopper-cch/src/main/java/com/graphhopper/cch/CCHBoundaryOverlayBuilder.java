// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class CCHBoundaryOverlayBuilder {
    private static final Comparator<CCHBoundaryArc> ARC_ORDER = Comparator
            .comparingInt(CCHBoundaryArc::getTailNode)
            .thenComparingInt(CCHBoundaryArc::getHeadNode)
            .thenComparingInt(CCHBoundaryArc::getEdgeId)
            .thenComparingInt(CCHBoundaryArc::getEdgeKey)
            .thenComparingDouble(CCHBoundaryArc::getWeight)
            .thenComparingLong(CCHBoundaryArc::getMillis)
            .thenComparingDouble(CCHBoundaryArc::getDistance)
            .thenComparingInt(CCHBoundaryArc::getOriginalEdgeKey)
            .thenComparing(CCHBoundaryArc::isVirtualEdge);

    public CCHBoundaryOverlay build(int sourceNode, int targetNode, QueryGraph queryGraph,
                                    CCHTopology topology, Weighting weighting) {
        Objects.requireNonNull(queryGraph, "queryGraph");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(weighting, "weighting");
        checkEndpoint("sourceNode", sourceNode, queryGraph);
        checkEndpoint("targetNode", targetNode, queryGraph);
        if (queryGraph.getBaseGraph().getNodes() != topology.getNodes())
            throw new IllegalArgumentException("query graph base node count and CCH topology node count differ: "
                    + queryGraph.getBaseGraph().getNodes() + " != " + topology.getNodes());
        if (weighting.hasTurnCosts())
            throw new IllegalArgumentException("graphhopper-cch boundary overlays only support node-based weightings without turn costs");

        boolean sourceCore = isCoreNode(sourceNode, queryGraph, topology);
        boolean targetCore = isCoreNode(targetNode, queryGraph, topology);
        List<CCHBoundaryArc> sourceOutgoing = sourceCore ? new ArrayList<>() : outgoingArcs(sourceNode, queryGraph, weighting);
        List<CCHBoundaryArc> sourceIncoming = sourceCore ? new ArrayList<>() : incomingArcs(sourceNode, queryGraph, weighting);
        List<CCHBoundaryArc> targetOutgoing = targetCore ? new ArrayList<>() : outgoingArcs(targetNode, queryGraph, weighting);
        List<CCHBoundaryArc> targetIncoming = targetCore ? new ArrayList<>() : incomingArcs(targetNode, queryGraph, weighting);

        List<CCHBoundaryArc> all = sortAndDeduplicate(sourceOutgoing, sourceIncoming, targetOutgoing, targetIncoming);
        return new CCHBoundaryOverlay(
                sourceNode,
                targetNode,
                sourceCore,
                targetCore,
                sourceOutgoing,
                sourceIncoming,
                targetOutgoing,
                targetIncoming,
                filterByEndpoints(all, sourceNode, targetNode),
                filterByEndpoints(all, targetNode, sourceNode),
                all);
    }

    private static List<CCHBoundaryArc> outgoingArcs(int endpoint, QueryGraph queryGraph, Weighting weighting) {
        List<CCHBoundaryArc> arcs = new ArrayList<>();
        EdgeExplorer explorer = queryGraph.createEdgeExplorer();
        EdgeIterator edge = explorer.setBaseNode(endpoint);
        while (edge.next()) {
            CCHBoundaryArc arc = buildArc(queryGraph, weighting, edge, false);
            if (arc != null)
                arcs.add(arc);
        }
        return sortAndDeduplicate(arcs);
    }

    private static List<CCHBoundaryArc> incomingArcs(int endpoint, QueryGraph queryGraph, Weighting weighting) {
        List<CCHBoundaryArc> arcs = new ArrayList<>();
        EdgeExplorer explorer = queryGraph.createEdgeExplorer();
        EdgeIterator edge = explorer.setBaseNode(endpoint);
        while (edge.next()) {
            CCHBoundaryArc arc = buildArc(queryGraph, weighting, edge, true);
            if (arc != null)
                arcs.add(arc);
        }
        return sortAndDeduplicate(arcs);
    }

    private static CCHBoundaryArc buildArc(QueryGraph queryGraph, Weighting weighting,
                                           EdgeIteratorState endpointEdge, boolean reverse) {
        EdgeIteratorState edge = endpointEdge.detach(false);
        double weight = weighting.calcEdgeWeight(edge, reverse);
        if (Double.isNaN(weight))
            throw invalidWeight(edge.getEdge(), reverse, "NaN");
        if (weight == Double.POSITIVE_INFINITY)
            return null;
        if (!Double.isFinite(weight))
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));
        if (weight < 0)
            throw invalidWeight(edge.getEdge(), reverse, String.valueOf(weight));

        long millis = weighting.calcEdgeMillis(edge, reverse);
        if (millis < 0)
            throw new IllegalArgumentException("Negative millis for boundary edge " + edge.getEdge()
                    + ", reverse=" + reverse + ": " + millis);

        double distance = edge.getDistance();
        if (!Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("Invalid distance for boundary edge " + edge.getEdge()
                    + ": " + distance);

        int tail = reverse ? edge.getAdjNode() : edge.getBaseNode();
        int head = reverse ? edge.getBaseNode() : edge.getAdjNode();
        EdgeIteratorState traversalEdge = queryGraph.getEdgeIteratorState(edge.getEdge(), head);
        int originalEdgeKey = traversalEdge instanceof VirtualEdgeIteratorState
                ? ((VirtualEdgeIteratorState) traversalEdge).getOriginalEdgeKey()
                : traversalEdge.getEdgeKey();
        return new CCHBoundaryArc(
                tail,
                head,
                traversalEdge.getEdge(),
                traversalEdge.getEdgeKey(),
                originalEdgeKey,
                weight,
                millis,
                distance,
                queryGraph.isVirtualEdge(traversalEdge.getEdge()));
    }

    private static List<CCHBoundaryArc> filterByEndpoints(List<CCHBoundaryArc> arcs, int tail, int head) {
        List<CCHBoundaryArc> result = new ArrayList<>();
        for (CCHBoundaryArc arc : arcs) {
            if (arc.getTailNode() == tail && arc.getHeadNode() == head)
                result.add(arc);
        }
        return result;
    }

    @SafeVarargs
    private static List<CCHBoundaryArc> sortAndDeduplicate(List<CCHBoundaryArc>... arcLists) {
        TreeSet<CCHBoundaryArc> sorted = new TreeSet<>(ARC_ORDER);
        for (List<CCHBoundaryArc> arcs : arcLists) {
            sorted.addAll(arcs);
        }
        return new ArrayList<>(sorted);
    }

    private static boolean isCoreNode(int node, QueryGraph queryGraph, CCHTopology topology) {
        return node < topology.getNodes() && !queryGraph.isVirtualNode(node);
    }

    private static void checkEndpoint(String name, int node, QueryGraph queryGraph) {
        if (node < 0 || node >= queryGraph.getNodes())
            throw new IllegalArgumentException(name + " outside [0," + queryGraph.getNodes() + "): " + node);
    }

    private static IllegalArgumentException invalidWeight(int edge, boolean reverse, String weight) {
        return new IllegalArgumentException("Invalid boundary weight for edge " + edge + ", reverse=" + reverse + ": " + weight);
    }
}
