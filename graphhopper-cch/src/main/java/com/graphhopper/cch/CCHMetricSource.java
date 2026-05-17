// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;

public interface CCHMetricSource {
    int getNodes();

    TraversalMode getTraversalMode();

    boolean hasTurnCosts();

    int getCandidates();

    CCHMetricCandidate getCandidate(int index);
}
