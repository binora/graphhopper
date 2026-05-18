// SPDX-License-Identifier: Apache-2.0

/**
 * Experimental Customizable Contraction Hierarchies support for GraphHopper.
 * <p>
 * The v1 module API is intentionally split into three groups:
 * <ul>
 *     <li>Adapter API: {@link com.graphhopper.cch.CCHGraphHopper},
 *     {@link com.graphhopper.cch.CCHGraphHopperConfig}, {@link com.graphhopper.cch.CCHProfile},
 *     and {@link com.graphhopper.cch.RoutingCCHGraph}. These are the entry points applications should use.</li>
 *     <li>Extension boundary: {@link com.graphhopper.cch.CCHMetricSource},
 *     {@link com.graphhopper.cch.CCHMetricCandidate}, and {@link com.graphhopper.cch.CCHMetricProvenance}.
 *     v2 edge-based and turn-cost work should extend CCH through this boundary rather than by special-casing
 *     {@code BaseGraph} inputs in the customizer.</li>
 *     <li>Construction, query, persistence, and diagnostic types. These classes are public so the current module can
 *     test and compose the CCH pipeline, but they are not a stable external compatibility promise yet.</li>
 * </ul>
 * <p>
 * v1 remains node-based and no-turn-cost only. The {@code CCHMetricSource} boundary is the intended place for v2
 * edge-state and turn-cost support.
 */
package com.graphhopper.cch;
