// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

/**
 * Builds a CCH order from an explicit node sequence. This is used for paper examples, tiny correctness fixtures, and
 * future imported nested-dissection orders.
 */
public final class FixedCCHNodeOrderBuilder {
    public CCHNodeOrder build(int[] order) {
        return CCHNodeOrder.fromOrder(order);
    }
}
