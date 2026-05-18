// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class CoordinateNestedDissectionCCHNodeOrderProvider implements CCHNodeOrderProvider {
    private static final int DEFAULT_LEAF_SIZE = 32;

    private final double[] latitudes;
    private final double[] longitudes;
    private final int leafSize;

    public CoordinateNestedDissectionCCHNodeOrderProvider(BaseGraph graph) {
        this(graph, DEFAULT_LEAF_SIZE);
    }

    public CoordinateNestedDissectionCCHNodeOrderProvider(BaseGraph graph, int leafSize) {
        this(readLatitudes(graph), readLongitudes(graph), leafSize);
    }

    public CoordinateNestedDissectionCCHNodeOrderProvider(double[] latitudes, double[] longitudes) {
        this(latitudes, longitudes, DEFAULT_LEAF_SIZE);
    }

    public CoordinateNestedDissectionCCHNodeOrderProvider(double[] latitudes, double[] longitudes, int leafSize) {
        this.latitudes = Objects.requireNonNull(latitudes, "latitudes").clone();
        this.longitudes = Objects.requireNonNull(longitudes, "longitudes").clone();
        if (this.latitudes.length != this.longitudes.length)
            throw new IllegalArgumentException("latitude and longitude arrays must have the same length");
        if (leafSize < 2)
            throw new IllegalArgumentException("leafSize must be >= 2");
        this.leafSize = leafSize;
    }

    public int getLeafSize() {
        return leafSize;
    }

    @Override
    public CCHNodeOrder build(CCHInputGraph inputGraph) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        if (inputGraph.getNodes() != latitudes.length)
            throw new IllegalArgumentException("coordinate count and input graph node count differ: "
                    + latitudes.length + " != " + inputGraph.getNodes());
        int[][] adjacency = buildAdjacency(inputGraph);
        int[] degree = new int[inputGraph.getNodes()];
        for (int node = 0; node < degree.length; node++) {
            degree[node] = adjacency[node].length;
        }
        int[] nodes = new int[inputGraph.getNodes()];
        Arrays.setAll(nodes, i -> i);
        List<Integer> order = new ArrayList<>(inputGraph.getNodes());
        appendNestedDissectionOrder(nodes, adjacency, degree, order);
        int[] orderArray = new int[order.size()];
        for (int rank = 0; rank < order.size(); rank++) {
            orderArray[rank] = order.get(rank);
        }
        return CCHNodeOrder.fromOrder(orderArray);
    }

    private void appendNestedDissectionOrder(int[] nodes, int[][] adjacency, int[] degree, List<Integer> order) {
        if (nodes.length <= leafSize) {
            appendLeafOrder(nodes, degree, order);
            return;
        }

        boolean splitLatitude = latitudeSpan(nodes) >= longitudeSpan(nodes);
        int[] sorted = sortedByCoordinate(nodes, splitLatitude);
        byte[] side = new byte[latitudes.length];
        int leftTarget = sorted.length / 2;
        for (int i = 0; i < sorted.length; i++) {
            side[sorted[i]] = i < leftTarget ? (byte) 1 : (byte) 2;
        }

        boolean[] separator = new boolean[latitudes.length];
        for (int node : sorted) {
            for (int neighbor : adjacency[node]) {
                if (side[neighbor] != 0 && side[neighbor] != side[node]) {
                    separator[node] = true;
                    separator[neighbor] = true;
                }
            }
        }

        int[] left = filter(sorted, side, separator, (byte) 1);
        int[] right = filter(sorted, side, separator, (byte) 2);
        int[] separatorNodes = filterSeparator(sorted, separator);
        if (left.length == 0 || right.length == 0 || separatorNodes.length == sorted.length) {
            appendLeafOrder(nodes, degree, order);
            return;
        }

        appendNestedDissectionOrder(left, adjacency, degree, order);
        appendNestedDissectionOrder(right, adjacency, degree, order);
        appendLeafOrder(separatorNodes, degree, order);
    }

    private void appendLeafOrder(int[] nodes, int[] degree, List<Integer> order) {
        Integer[] boxed = box(nodes);
        Arrays.sort(boxed, Comparator.comparingInt((Integer node) -> degree[node]).thenComparingInt(node -> node));
        for (int node : boxed) {
            order.add(node);
        }
    }

    private int[] sortedByCoordinate(int[] nodes, boolean latitude) {
        Integer[] boxed = box(nodes);
        Arrays.sort(boxed, Comparator
                .comparingDouble((Integer node) -> latitude ? latitudes[node] : longitudes[node])
                .thenComparingDouble(node -> latitude ? longitudes[node] : latitudes[node])
                .thenComparingInt(node -> node));
        return unbox(boxed);
    }

    private double latitudeSpan(int[] nodes) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int node : nodes) {
            min = Math.min(min, latitudes[node]);
            max = Math.max(max, latitudes[node]);
        }
        return max - min;
    }

    private double longitudeSpan(int[] nodes) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int node : nodes) {
            min = Math.min(min, longitudes[node]);
            max = Math.max(max, longitudes[node]);
        }
        return max - min;
    }

    private static int[] filter(int[] nodes, byte[] side, boolean[] separator, byte targetSide) {
        int count = 0;
        for (int node : nodes) {
            if (!separator[node] && side[node] == targetSide)
                count++;
        }
        int[] result = new int[count];
        int index = 0;
        for (int node : nodes) {
            if (!separator[node] && side[node] == targetSide)
                result[index++] = node;
        }
        return result;
    }

    private static int[] filterSeparator(int[] nodes, boolean[] separator) {
        int count = 0;
        for (int node : nodes) {
            if (separator[node])
                count++;
        }
        int[] result = new int[count];
        int index = 0;
        for (int node : nodes) {
            if (separator[node])
                result[index++] = node;
        }
        return result;
    }

    private static int[][] buildAdjacency(CCHInputGraph inputGraph) {
        @SuppressWarnings("unchecked")
        TreeSet<Integer>[] adjacency = new TreeSet[inputGraph.getNodes()];
        for (int node = 0; node < inputGraph.getNodes(); node++) {
            adjacency[node] = new TreeSet<>();
        }
        for (CCHInputEdge edge : inputGraph.getAllSupportEdges()) {
            adjacency[edge.getNodeA()].add(edge.getNodeB());
            adjacency[edge.getNodeB()].add(edge.getNodeA());
        }
        int[][] result = new int[inputGraph.getNodes()][];
        for (int node = 0; node < inputGraph.getNodes(); node++) {
            result[node] = new int[adjacency[node].size()];
            int index = 0;
            for (int neighbor : adjacency[node]) {
                result[node][index++] = neighbor;
            }
        }
        return result;
    }

    private static Integer[] box(int[] values) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return boxed;
    }

    private static int[] unbox(Integer[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    private static double[] readLatitudes(BaseGraph graph) {
        Objects.requireNonNull(graph, "graph");
        NodeAccess nodeAccess = graph.getNodeAccess();
        double[] latitudes = new double[graph.getNodes()];
        for (int node = 0; node < latitudes.length; node++) {
            latitudes[node] = nodeAccess.getLat(node);
        }
        return latitudes;
    }

    private static double[] readLongitudes(BaseGraph graph) {
        Objects.requireNonNull(graph, "graph");
        NodeAccess nodeAccess = graph.getNodeAccess();
        double[] longitudes = new double[graph.getNodes()];
        for (int node = 0; node < longitudes.length; node++) {
            longitudes[node] = nodeAccess.getLon(node);
        }
        return longitudes;
    }
}
