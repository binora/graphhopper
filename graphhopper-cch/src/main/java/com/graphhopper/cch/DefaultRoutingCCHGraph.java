// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;

import java.util.Objects;

public final class DefaultRoutingCCHGraph implements RoutingCCHGraph {
    private final BaseGraph baseGraph;
    private final CCHStorage cchStorage;
    private final CCHTopology topology;
    private final CCHMetric metric;
    private final Weighting weighting;

    public DefaultRoutingCCHGraph(BaseGraph baseGraph, CCHTopology topology, CCHMetric metric, Weighting weighting) {
        this(baseGraph, Objects.requireNonNull(topology, "topology").toStorage(), topology, metric, weighting);
    }

    public DefaultRoutingCCHGraph(BaseGraph baseGraph, CCHStorage cchStorage, CCHTopology topology, CCHMetric metric, Weighting weighting) {
        this.baseGraph = Objects.requireNonNull(baseGraph, "baseGraph");
        this.cchStorage = Objects.requireNonNull(cchStorage, "cchStorage");
        this.topology = Objects.requireNonNull(topology, "topology");
        this.metric = Objects.requireNonNull(metric, "metric");
        this.weighting = Objects.requireNonNull(weighting, "weighting");
        if (baseGraph.getNodes() != cchStorage.getNodes())
            throw new IllegalArgumentException("BaseGraph and CCHStorage node counts differ: " + baseGraph.getNodes() + " != " + cchStorage.getNodes());
        if (topology.getNodes() != cchStorage.getNodes())
            throw new IllegalArgumentException("CCHTopology and CCHStorage node counts differ: " + topology.getNodes() + " != " + cchStorage.getNodes());
        if (metric.getArcs() != topology.getArcs())
            throw new IllegalArgumentException("CCHMetric and CCHTopology arc counts differ: " + metric.getArcs() + " != " + topology.getArcs());
        if (cchStorage.getArcs() != topology.getArcs())
            throw new IllegalArgumentException("CCHStorage and CCHTopology arc counts differ: " + cchStorage.getArcs() + " != " + topology.getArcs());
        checkStorageMatchesTopology(cchStorage, topology);
        if (weighting.hasTurnCosts())
            throw new IllegalArgumentException("graphhopper-cch v1 only supports node-based routing without turn costs");
    }

    @Override
    public BaseGraph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public CCHStorage getCCHStorage() {
        return cchStorage;
    }

    @Override
    public CCHTopology getTopology() {
        return topology;
    }

    @Override
    public CCHMetric getMetric() {
        return metric;
    }

    @Override
    public Weighting getWeighting() {
        return weighting;
    }

    private static void checkStorageMatchesTopology(CCHStorage storage, CCHTopology topology) {
        for (int rank = 0; rank < storage.getNodes(); rank++) {
            if (storage.getOrder(rank) != topology.getNodeOrder().getOrder(rank))
                throw new IllegalArgumentException("CCHStorage and CCHTopology order differ at rank " + rank);
        }
        for (int node = 0; node < storage.getNodes(); node++) {
            if (storage.getRank(node) != topology.getNodeOrder().getRank(node))
                throw new IllegalArgumentException("CCHStorage and CCHTopology rank differ at node " + node);
            if (storage.getUpArcStart(node) != topology.getUpArcStart(node) || storage.getUpArcEnd(node) != topology.getUpArcEnd(node))
                throw new IllegalArgumentException("CCHStorage and CCHTopology upward first-out differ at node " + node);
            if (storage.getDownArcStart(node) != topology.getDownArcStart(node) || storage.getDownArcEnd(node) != topology.getDownArcEnd(node))
                throw new IllegalArgumentException("CCHStorage and CCHTopology downward first-out differ at node " + node);
        }
        for (int upArc = 0; upArc < storage.getUpArcs(); upArc++) {
            if (storage.getUpHead(upArc) != topology.getUpHead(upArc))
                throw new IllegalArgumentException("CCHStorage and CCHTopology upward heads differ at arc " + upArc);
        }
        for (int downArc = 0; downArc < storage.getDownArcs(); downArc++) {
            if (storage.getDownHead(downArc) != topology.getDownHead(downArc))
                throw new IllegalArgumentException("CCHStorage and CCHTopology downward heads differ at arc " + downArc);
        }
    }
}
