// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable CCH node ordering. {@code order[rank]} returns the original node id at a rank, and {@code rank[node]}
 * returns the contraction rank for an original node id.
 */
public final class CCHNodeOrder {
    private final int[] order;
    private final int[] rank;

    private CCHNodeOrder(int[] order, int[] rank) {
        this.order = order;
        this.rank = rank;
    }

    public static CCHNodeOrder identity(int nodes) {
        if (nodes < 0)
            throw new IllegalArgumentException("nodes must be >= 0");
        int[] order = new int[nodes];
        Arrays.setAll(order, i -> i);
        return fromOrder(order);
    }

    public static CCHNodeOrder fromOrder(int[] order) {
        int[] orderCopy = copy(order);
        checkPermutation("order", orderCopy);
        int[] rank = new int[orderCopy.length];
        for (int i = 0; i < orderCopy.length; i++) {
            rank[orderCopy[i]] = i;
        }
        return new CCHNodeOrder(orderCopy, rank);
    }

    public static CCHNodeOrder fromOrderAndRank(int[] order, int[] rank) {
        int[] orderCopy = copy(order);
        int[] rankCopy = copy(rank);
        if (orderCopy.length != rankCopy.length)
            throw new IllegalArgumentException("order and rank must have the same length");
        checkPermutation("order", orderCopy);
        checkPermutation("rank", rankCopy);
        for (int i = 0; i < orderCopy.length; i++) {
            if (rankCopy[orderCopy[i]] != i)
                throw new IllegalArgumentException("order and rank must be inverse arrays");
        }
        return new CCHNodeOrder(orderCopy, rankCopy);
    }

    public int getNodes() {
        return order.length;
    }

    public int getOrder(int rank) {
        checkRange("rank", rank);
        return order[rank];
    }

    public int getNodeAtRank(int rank) {
        return getOrder(rank);
    }

    public int getRank(int node) {
        checkRange("node", node);
        return rank[node];
    }

    public int[] getOrderArray() {
        return order.clone();
    }

    public int[] getRankArray() {
        return rank.clone();
    }

    private void checkRange(String name, int value) {
        if (value < 0 || value >= order.length)
            throw new IllegalArgumentException(name + " outside [0," + order.length + "): " + value);
    }

    private static int[] copy(int[] values) {
        return Objects.requireNonNull(values, "values").clone();
    }

    private static void checkPermutation(String name, int[] values) {
        boolean[] seen = new boolean[values.length];
        for (int value : values) {
            if (value < 0 || value >= values.length)
                throw new IllegalArgumentException(name + " contains invalid node " + value);
            if (seen[value])
                throw new IllegalArgumentException(name + " contains duplicate node " + value);
            seen[value] = true;
        }
    }
}
