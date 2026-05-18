// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

/**
 * Builds a metric-independent contraction order for a CCH support graph.
 * <p>
 * Implementations may use deterministic local heuristics, imported nested-dissection orders, or graph partitioners.
 * The returned order must be a permutation of all input graph nodes.
 */
public interface CCHNodeOrderProvider {
    CCHNodeOrder build(CCHInputGraph inputGraph);
}
