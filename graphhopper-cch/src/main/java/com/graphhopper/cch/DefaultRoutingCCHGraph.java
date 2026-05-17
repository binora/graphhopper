// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;

import java.util.Objects;

public final class DefaultRoutingCCHGraph implements RoutingCCHGraph {
    private final BaseGraph baseGraph;
    private final CCHStorage cchStorage;
    private final Weighting weighting;

    public DefaultRoutingCCHGraph(BaseGraph baseGraph, CCHStorage cchStorage, Weighting weighting) {
        this.baseGraph = Objects.requireNonNull(baseGraph, "baseGraph");
        this.cchStorage = Objects.requireNonNull(cchStorage, "cchStorage");
        this.weighting = Objects.requireNonNull(weighting, "weighting");
        if (baseGraph.getNodes() != cchStorage.getNodes())
            throw new IllegalArgumentException("BaseGraph and CCHStorage node counts differ: " + baseGraph.getNodes() + " != " + cchStorage.getNodes());
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
    public Weighting getWeighting() {
        return weighting;
    }
}
