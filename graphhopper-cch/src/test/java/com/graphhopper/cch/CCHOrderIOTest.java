// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHOrderIOTest {
    @TempDir
    Path tempDir;

    @Test
    void writesMetisSupportGraphDeterministically() throws Exception {
        CCHInputGraph inputGraph = undirectedInputGraph(4, edge(2, 3), edge(0, 1), edge(1, 2), edge(0, 1));
        Path graphFile = tempDir.resolve("support.graph");

        CCHOrderIO.writeMetisGraph(inputGraph, graphFile);

        assertEquals(Arrays.asList(
                "4 3",
                "2",
                "1 3",
                "2 4",
                "3"), Files.readAllLines(graphFile, StandardCharsets.UTF_8));
    }

    @Test
    void readsZeroBasedOrderWithComments() throws Exception {
        Path orderFile = tempDir.resolve("order.txt");
        Files.write(orderFile, Arrays.asList(
                "# rank order",
                "2 0",
                "1 # final node"), StandardCharsets.UTF_8);

        CCHNodeOrder order = CCHOrderIO.readZeroBasedOrder(orderFile, 3);

        assertArrayEquals(new int[]{2, 0, 1}, order.getOrderArray());
        assertArrayEquals(new int[]{1, 2, 0}, order.getRankArray());
    }

    @Test
    void readsOneBasedOrder() throws Exception {
        Path orderFile = tempDir.resolve("order-one-based.txt");
        Files.write(orderFile, Arrays.asList("3", "1", "2"), StandardCharsets.UTF_8);

        CCHNodeOrder order = CCHOrderIO.readOneBasedOrder(orderFile, 3);

        assertArrayEquals(new int[]{2, 0, 1}, order.getOrderArray());
    }

    @Test
    void fileProviderUsesInputGraphNodeCount() throws Exception {
        Path orderFile = tempDir.resolve("provider-order.txt");
        Files.write(orderFile, Arrays.asList("1", "0", "2"), StandardCharsets.UTF_8);
        CCHInputGraph inputGraph = undirectedInputGraph(3, edge(0, 1), edge(1, 2));

        CCHNodeOrder order = new FileCCHNodeOrderProvider(orderFile).build(inputGraph);

        assertArrayEquals(new int[]{1, 0, 2}, order.getOrderArray());
    }

    @Test
    void rejectsMalformedOrdersClearly() throws Exception {
        Path duplicate = tempDir.resolve("duplicate.txt");
        Files.write(duplicate, Arrays.asList("0 1 1"), StandardCharsets.UTF_8);
        IllegalArgumentException duplicateError = assertThrows(IllegalArgumentException.class,
                () -> CCHOrderIO.readZeroBasedOrder(duplicate, 3));
        assertTrue(duplicateError.getMessage().contains("valid zero-based permutation"), duplicateError.getMessage());

        Path wrongCount = tempDir.resolve("wrong-count.txt");
        Files.write(wrongCount, Arrays.asList("0 1"), StandardCharsets.UTF_8);
        IllegalArgumentException wrongCountError = assertThrows(IllegalArgumentException.class,
                () -> CCHOrderIO.readZeroBasedOrder(wrongCount, 3));
        assertTrue(wrongCountError.getMessage().contains("contains 2 entries, expected 3"), wrongCountError.getMessage());

        Path nonInteger = tempDir.resolve("non-integer.txt");
        Files.write(nonInteger, Arrays.asList("0 x 1"), StandardCharsets.UTF_8);
        IllegalArgumentException nonIntegerError = assertThrows(IllegalArgumentException.class,
                () -> CCHOrderIO.readZeroBasedOrder(nonInteger, 3));
        assertTrue(nonIntegerError.getMessage().contains("non-integer token 'x'"), nonIntegerError.getMessage());
    }

    @Test
    void providerWrapsIoFailures() {
        FileCCHNodeOrderProvider provider = new FileCCHNodeOrderProvider(tempDir.resolve("missing-order.txt"));
        CCHInputGraph inputGraph = undirectedInputGraph(2, edge(0, 1));

        UncheckedIOException error = assertThrows(UncheckedIOException.class, () -> provider.build(inputGraph));

        assertTrue(error.getMessage().contains("Cannot read CCH order file"), error.getMessage());
    }

    private static CCHInputGraph undirectedInputGraph(int nodes, CCHInputEdge... supportEdges) {
        List<CCHInputArc> arcs = new ArrayList<>();
        for (int edgeId = 0; edgeId < supportEdges.length; edgeId++) {
            CCHInputEdge edge = supportEdges[edgeId];
            arcs.add(new CCHInputArc(edge.getNodeA(), edge.getNodeB(), edgeId, false, 1, 1, 1));
            arcs.add(new CCHInputArc(edge.getNodeB(), edge.getNodeA(), edgeId, true, 1, 1, 1));
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputEdge edge(int a, int b) {
        return new CCHInputEdge(a, b);
    }
}
