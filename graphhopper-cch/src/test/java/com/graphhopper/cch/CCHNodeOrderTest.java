// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CCHNodeOrderTest {
    @Test
    void buildsIdentityOrder() {
        CCHNodeOrder order = CCHNodeOrder.identity(4);

        assertEquals(4, order.getNodes());
        assertArrayEquals(new int[]{0, 1, 2, 3}, order.getOrderArray());
        assertArrayEquals(new int[]{0, 1, 2, 3}, order.getRankArray());
        assertEquals(2, order.getOrder(2));
        assertEquals(2, order.getNodeAtRank(2));
        assertEquals(3, order.getRank(3));
    }

    @Test
    void buildsNonIdentityFixedOrderAndRank() {
        CCHNodeOrder order = new FixedCCHNodeOrderBuilder().build(new int[]{2, 0, 3, 1});

        assertArrayEquals(new int[]{2, 0, 3, 1}, order.getOrderArray());
        assertArrayEquals(new int[]{1, 3, 0, 2}, order.getRankArray());
        assertEquals(2, order.getNodeAtRank(0));
        assertEquals(3, order.getRank(1));
    }

    @Test
    void rejectsInvalidOrders() {
        assertThrows(IllegalArgumentException.class, () -> CCHNodeOrder.identity(-1));
        assertThrows(IllegalArgumentException.class, () -> CCHNodeOrder.fromOrder(new int[]{0, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> CCHNodeOrder.fromOrder(new int[]{0, 3, 1}));
        assertThrows(NullPointerException.class, () -> CCHNodeOrder.fromOrder(null));
    }

    @Test
    void rejectsInvalidRankArrays() {
        assertThrows(IllegalArgumentException.class,
                () -> CCHNodeOrder.fromOrderAndRank(new int[]{0, 1, 2}, new int[]{0, 1}));
        assertThrows(IllegalArgumentException.class,
                () -> CCHNodeOrder.fromOrderAndRank(new int[]{0, 1, 2}, new int[]{0, 1, 1}));
        assertThrows(IllegalArgumentException.class,
                () -> CCHNodeOrder.fromOrderAndRank(new int[]{1, 0, 2}, new int[]{0, 1, 2}));
    }

    @Test
    void returnedArraysAreDefensiveCopies() {
        CCHNodeOrder order = CCHNodeOrder.fromOrder(new int[]{1, 0, 2});

        order.getOrderArray()[0] = 2;
        order.getRankArray()[0] = 2;

        assertArrayEquals(new int[]{1, 0, 2}, order.getOrderArray());
        assertArrayEquals(new int[]{1, 0, 2}, order.getRankArray());
    }

    @Test
    void rejectsRankAndNodeLookupsOutsideRange() {
        CCHNodeOrder order = CCHNodeOrder.identity(2);

        assertThrows(IllegalArgumentException.class, () -> order.getOrder(-1));
        assertThrows(IllegalArgumentException.class, () -> order.getOrder(2));
        assertThrows(IllegalArgumentException.class, () -> order.getRank(-1));
        assertThrows(IllegalArgumentException.class, () -> order.getRank(2));
    }
}
