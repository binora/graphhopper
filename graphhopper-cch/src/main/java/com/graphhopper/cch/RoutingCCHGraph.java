// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;

/**
 * Read-only runtime view of a CCH overlay attached to a GraphHopper {@link BaseGraph}.
 */
public interface RoutingCCHGraph {
    BaseGraph getBaseGraph();

    CCHStorage getCCHStorage();

    Weighting getWeighting();

    default int getNodes() {
        return getBaseGraph().getNodes();
    }

    default int getArcs() {
        return getCCHStorage().getArcs();
    }

    default int getShortcuts() {
        return getCCHStorage().getShortcuts();
    }

    default int getRank(int node) {
        return getCCHStorage().getRank(node);
    }

    default int getOrder(int rank) {
        return getCCHStorage().getOrder(rank);
    }
}
