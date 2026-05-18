// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;

/**
 * Metric customization input boundary.
 * <p>
 * Implementations emit deterministic metric candidates for already-built CCH overlay arcs. The customizer must depend
 * on this interface rather than concrete {@link CCHInputGraph} or {@link com.graphhopper.storage.BaseGraph} inputs so
 * v2 can add edge-state and turn-cost metric sources without rewriting node-based customization.
 */
public interface CCHMetricSource {
    int getNodes();

    TraversalMode getTraversalMode();

    boolean hasTurnCosts();

    int getCandidates();

    CCHMetricCandidate getCandidate(int index);
}
