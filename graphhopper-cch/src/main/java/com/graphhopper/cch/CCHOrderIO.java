// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class CCHOrderIO {
    private CCHOrderIO() {
    }

    /**
     * Writes the undirected CCH support graph in unweighted METIS graph format.
     * METIS uses one-based node ids in adjacency rows.
     */
    public static void writeMetisGraph(CCHInputGraph inputGraph, Path graphFile) throws IOException {
        Objects.requireNonNull(inputGraph, "inputGraph");
        Objects.requireNonNull(graphFile, "graphFile");
        @SuppressWarnings("unchecked")
        TreeSet<Integer>[] adjacency = new TreeSet[inputGraph.getNodes()];
        for (int node = 0; node < inputGraph.getNodes(); node++) {
            adjacency[node] = new TreeSet<>();
        }
        for (CCHInputEdge edge : inputGraph.getAllSupportEdges()) {
            adjacency[edge.getNodeA()].add(edge.getNodeB());
            adjacency[edge.getNodeB()].add(edge.getNodeA());
        }
        int edgeCount = 0;
        for (TreeSet<Integer> neighbors : adjacency) {
            edgeCount += neighbors.size();
        }
        edgeCount /= 2;
        Path parent = graphFile.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(graphFile, StandardCharsets.UTF_8)) {
            writer.write(inputGraph.getNodes() + " " + edgeCount);
            writer.newLine();
            for (int node = 0; node < inputGraph.getNodes(); node++) {
                boolean first = true;
                for (int neighbor : adjacency[node]) {
                    if (!first)
                        writer.write(' ');
                    writer.write(Integer.toString(neighbor + 1));
                    first = false;
                }
                writer.newLine();
            }
        }
    }

    /**
     * Reads a whitespace-separated zero-based node order. Values are original node ids in increasing rank order.
     * Lines may contain comments after {@code #}.
     */
    public static CCHNodeOrder readZeroBasedOrder(Path orderFile, int nodes) throws IOException {
        Objects.requireNonNull(orderFile, "orderFile");
        if (nodes < 0)
            throw new IllegalArgumentException("nodes must be >= 0");
        List<Integer> values = readIntegers(orderFile);
        if (values.size() != nodes)
            throw new IllegalArgumentException("CCH order file '" + orderFile + "' contains " + values.size()
                    + " entries, expected " + nodes);
        int[] order = new int[nodes];
        for (int rank = 0; rank < nodes; rank++) {
            order[rank] = values.get(rank);
        }
        try {
            return CCHNodeOrder.fromOrder(order);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CCH order file '" + orderFile + "' is not a valid zero-based permutation: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Reads a whitespace-separated one-based node order and converts it to GraphHopper's zero-based node ids.
     */
    public static CCHNodeOrder readOneBasedOrder(Path orderFile, int nodes) throws IOException {
        Objects.requireNonNull(orderFile, "orderFile");
        if (nodes < 0)
            throw new IllegalArgumentException("nodes must be >= 0");
        List<Integer> values = readIntegers(orderFile);
        if (values.size() != nodes)
            throw new IllegalArgumentException("CCH order file '" + orderFile + "' contains " + values.size()
                    + " entries, expected " + nodes);
        int[] order = new int[nodes];
        for (int rank = 0; rank < nodes; rank++) {
            order[rank] = values.get(rank) - 1;
        }
        try {
            return CCHNodeOrder.fromOrder(order);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CCH order file '" + orderFile + "' is not a valid one-based permutation: "
                    + e.getMessage(), e);
        }
    }

    private static List<Integer> readIntegers(Path file) throws IOException {
        List<Integer> values = new ArrayList<>();
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = stripComment(rawLine).trim();
            if (line.isEmpty())
                continue;
            for (String token : line.split("\\s+")) {
                try {
                    values.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("CCH order file '" + file + "' has non-integer token '"
                            + token + "' on line " + lineNumber, e);
                }
            }
        }
        return values;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }
}
