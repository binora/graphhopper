// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

@FunctionalInterface
public interface CCHTriangleConsumer {
    void accept(CCHTriangle triangle);
}
