// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CCHTopologyBuilderTest {
    @Test
    void addsFillEdgeWhenEliminatingPathMiddleFirst() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(3, support(0, 1), support(1, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        assertEquals(3, topology.getUpArcs());
        assertEquals(3, topology.getDownArcs());
        assertTrue(hasUpArc(topology, 0, 2));
        assertTrue(topology.isFillArc(upArc(topology, 0, 2)));
        assertTrue(topology.isFillArc(topology.getDownArcId(downArc(topology, 2, 0))));
        assertEquals(CCHStorage.NO_ARC, topology.getSkippedArc1(upArc(topology, 0, 2)));
        assertEquals(CCHStorage.NO_ARC, topology.getSkippedArc2(upArc(topology, 0, 2)));
    }

    @Test
    void naturalPathOrderAddsNoFillEdge() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(3, support(0, 1), support(1, 2)),
                CCHNodeOrder.identity(3));

        assertEquals(2, topology.getUpArcs());
        assertEquals(2, topology.getDownArcs());
        assertTrue(hasUpArc(topology, 0, 1));
        assertTrue(hasUpArc(topology, 1, 2));
        assertFalse(hasUpArc(topology, 0, 2));
        assertNoFillArcs(topology);
    }

    @Test
    void disconnectedComponentsDoNotCreateCrossComponentFillEdges() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(5, support(0, 1), support(2, 3)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 3, 2, 4}));

        assertEquals(2, topology.getUpArcs());
        assertEquals(2, topology.getDownArcs());
        assertTrue(hasUpArc(topology, 1, 0));
        assertTrue(hasUpArc(topology, 3, 2));
        assertNoFillArcs(topology);
    }

    @Test
    void mapsInputArcsToDirectionalOverlayArcs() {
        CCHInputGraph inputGraph = new CCHInputGraph(3,
                Arrays.asList(
                        arc(0, 1, 0, false),
                        arc(0, 1, 1, false),
                        arc(1, 0, 0, true),
                        arc(1, 2, 2, false),
                        arc(2, 1, 2, true)),
                Arrays.asList(support(0, 1), support(1, 2)));

        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, CCHNodeOrder.identity(3));

        int up01 = upArc(topology, 0, 1);
        int down10 = topology.getDownArcId(downArc(topology, 1, 0));
        int up12 = upArc(topology, 1, 2);
        int down21 = topology.getDownArcId(downArc(topology, 2, 1));
        assertEquals(up01, topology.getInputArcCCHArc(0));
        assertEquals(up01, topology.getInputArcCCHArc(1));
        assertEquals(down10, topology.getInputArcCCHArc(2));
        assertEquals(up12, topology.getInputArcCCHArc(3));
        assertEquals(down21, topology.getInputArcCCHArc(4));
    }

    @Test
    void exposesStorageCompatibleArraysAndPlaceholders() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(3, support(0, 1), support(1, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        CCHStorage storage = topology.toStorage();

        assertEquals(topology.getNodes(), storage.getNodes());
        assertEquals(topology.getUpArcs(), storage.getUpArcs());
        assertEquals(topology.getDownArcs(), storage.getDownArcs());
        for (int node = 0; node < topology.getNodes(); node++) {
            assertEquals(topology.getUpArcStart(node), storage.getUpArcStart(node));
            assertEquals(topology.getUpArcEnd(node), storage.getUpArcEnd(node));
            assertEquals(topology.getDownArcStart(node), storage.getDownArcStart(node));
            assertEquals(topology.getDownArcEnd(node), storage.getDownArcEnd(node));
        }
        for (int upArc = 0; upArc < topology.getUpArcs(); upArc++) {
            assertEquals(topology.getUpHead(upArc), storage.getUpHead(upArc));
            assertEquals(CCHStorage.NO_ARC, storage.getBaseEdge(upArc));
        }
        for (int downArc = 0; downArc < topology.getDownArcs(); downArc++) {
            int cchArc = storage.getDownArcId(downArc);
            assertEquals(topology.getDownHead(downArc), storage.getDownHead(downArc));
            assertEquals(CCHStorage.NO_ARC, storage.getBaseEdge(cchArc));
        }
    }

    @Test
    void rejectsInvalidInputs() {
        CCHTopologyBuilder builder = new CCHTopologyBuilder();
        CCHInputGraph inputGraph = inputGraph(2, support(0, 1));

        assertThrows(NullPointerException.class, () -> builder.build(null, CCHNodeOrder.identity(2)));
        assertThrows(NullPointerException.class, () -> builder.build(inputGraph, null));
        assertThrows(IllegalArgumentException.class, () -> builder.build(inputGraph, CCHNodeOrder.identity(3)));
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                new CCHInputGraph(2, Collections.singletonList(arc(0, 1, 0, false)), Collections.emptyList()),
                CCHNodeOrder.identity(2)));
    }

    @Test
    void randomizedSmallGraphsAreDeterministicAndValid() {
        for (int seed = 0; seed < 20; seed++) {
            CCHInputGraph inputGraph = randomInputGraph(seed);
            CCHNodeOrder order = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
            CCHTopology first = new CCHTopologyBuilder().build(inputGraph, order);
            CCHTopology second = new CCHTopologyBuilder().build(inputGraph, order);

            assertArrayEquals(first.getUpFirstOutArray(), second.getUpFirstOutArray());
            assertArrayEquals(first.getUpTailArray(), second.getUpTailArray());
            assertArrayEquals(first.getUpHeadArray(), second.getUpHeadArray());
            assertArrayEquals(first.getDownFirstOutArray(), second.getDownFirstOutArray());
            assertArrayEquals(first.getDownTailArray(), second.getDownTailArray());
            assertArrayEquals(first.getDownHeadArray(), second.getDownHeadArray());
            assertArrayEquals(first.getInputArcCCHArcArray(), second.getInputArcCCHArcArray());

            assertTopologyInvariants(first, inputGraph);
            assertHigherNeighborsFormCliques(first);
        }
    }

    private static void assertTopologyInvariants(CCHTopology topology, CCHInputGraph inputGraph) {
        for (int upArc = 0; upArc < topology.getUpArcs(); upArc++) {
            assertTrue(topology.getNodeOrder().getRank(topology.getUpTail(upArc)) < topology.getNodeOrder().getRank(topology.getUpHead(upArc)));
        }
        for (int downArc = 0; downArc < topology.getDownArcs(); downArc++) {
            assertTrue(topology.getNodeOrder().getRank(topology.getDownTail(downArc)) > topology.getNodeOrder().getRank(topology.getDownHead(downArc)));
        }
        assertSortedAndDuplicateFree(topology);
        for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
            int cchArc = topology.getInputArcCCHArc(inputArc);
            CCHInputArc arc = inputGraph.getArc(inputArc);
            assertTrue(cchArc >= 0 && cchArc < topology.getArcs());
            assertEquals(arc.getFrom(), tail(topology, cchArc));
            assertEquals(arc.getTo(), head(topology, cchArc));
        }
    }

    private static void assertSortedAndDuplicateFree(CCHTopology topology) {
        for (int node = 0; node < topology.getNodes(); node++) {
            int previousHead = -1;
            int previousRank = -1;
            for (int arc = topology.getUpArcStart(node); arc < topology.getUpArcEnd(node); arc++) {
                int head = topology.getUpHead(arc);
                int rank = topology.getNodeOrder().getRank(head);
                assertTrue(rank > previousRank || rank == previousRank && head > previousHead);
                previousRank = rank;
                previousHead = head;
            }
            previousHead = -1;
            previousRank = -1;
            for (int arc = topology.getDownArcStart(node); arc < topology.getDownArcEnd(node); arc++) {
                int head = topology.getDownHead(arc);
                int rank = topology.getNodeOrder().getRank(head);
                assertTrue(rank > previousRank || rank == previousRank && head > previousHead);
                previousRank = rank;
                previousHead = head;
            }
        }
    }

    private static void assertHigherNeighborsFormCliques(CCHTopology topology) {
        Set<Long> finalEdges = finalEdges(topology);
        for (int node = 0; node < topology.getNodes(); node++) {
            List<Integer> higherNeighbors = new ArrayList<>();
            for (long edge : finalEdges) {
                int a = (int) (edge >>> 32);
                int b = (int) edge;
                if (a == node && topology.getNodeOrder().getRank(b) > topology.getNodeOrder().getRank(node))
                    higherNeighbors.add(b);
                if (b == node && topology.getNodeOrder().getRank(a) > topology.getNodeOrder().getRank(node))
                    higherNeighbors.add(a);
            }
            for (int i = 0; i < higherNeighbors.size(); i++) {
                for (int j = i + 1; j < higherNeighbors.size(); j++) {
                    assertTrue(finalEdges.contains(edgeKey(higherNeighbors.get(i), higherNeighbors.get(j))));
                }
            }
        }
    }

    private static CCHInputGraph randomInputGraph(int seed) {
        Random random = new Random(seed);
        int nodes = 2 + random.nextInt(7);
        List<CCHInputEdge> supportEdges = new ArrayList<>();
        List<CCHInputArc> arcs = new ArrayList<>();
        int baseEdge = 0;
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 1; b < nodes; b++) {
                if (random.nextBoolean()) {
                    supportEdges.add(support(a, b));
                    arcs.add(arc(a, b, baseEdge, false));
                    arcs.add(arc(b, a, baseEdge, true));
                    baseEdge++;
                }
            }
        }
        if (supportEdges.isEmpty()) {
            supportEdges.add(support(0, 1));
            arcs.add(arc(0, 1, baseEdge, false));
            arcs.add(arc(1, 0, baseEdge, true));
        }
        return new CCHInputGraph(nodes, arcs, supportEdges);
    }

    private static CCHInputGraph inputGraph(int nodes, CCHInputEdge... supportEdges) {
        List<CCHInputArc> arcs = new ArrayList<>();
        int baseEdge = 0;
        for (CCHInputEdge edge : supportEdges) {
            arcs.add(arc(edge.getNodeA(), edge.getNodeB(), baseEdge, false));
            arcs.add(arc(edge.getNodeB(), edge.getNodeA(), baseEdge, true));
            baseEdge++;
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputArc arc(int from, int to, int baseEdge, boolean reverse) {
        return new CCHInputArc(from, to, baseEdge, reverse, 1, 1, 1);
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static int upArc(CCHTopology topology, int tail, int head) {
        for (int arc = topology.getUpArcStart(tail); arc < topology.getUpArcEnd(tail); arc++) {
            if (topology.getUpHead(arc) == head)
                return arc;
        }
        throw new AssertionError("missing up arc " + tail + "->" + head);
    }

    private static boolean hasUpArc(CCHTopology topology, int tail, int head) {
        for (int arc = topology.getUpArcStart(tail); arc < topology.getUpArcEnd(tail); arc++) {
            if (topology.getUpHead(arc) == head)
                return true;
        }
        return false;
    }

    private static int downArc(CCHTopology topology, int tail, int head) {
        for (int arc = topology.getDownArcStart(tail); arc < topology.getDownArcEnd(tail); arc++) {
            if (topology.getDownHead(arc) == head)
                return arc;
        }
        throw new AssertionError("missing down arc " + tail + "->" + head);
    }

    private static void assertNoFillArcs(CCHTopology topology) {
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            assertFalse(topology.isFillArc(arc));
        }
    }

    private static Set<Long> finalEdges(CCHTopology topology) {
        Set<Long> edges = new HashSet<>();
        for (int arc = 0; arc < topology.getUpArcs(); arc++) {
            edges.add(edgeKey(topology.getUpTail(arc), topology.getUpHead(arc)));
        }
        return edges;
    }

    private static int tail(CCHTopology topology, int cchArc) {
        if (cchArc < topology.getUpArcs())
            return topology.getUpTail(cchArc);
        return topology.getDownTail(cchArc - topology.getUpArcs());
    }

    private static int head(CCHTopology topology, int cchArc) {
        if (cchArc < topology.getUpArcs())
            return topology.getUpHead(cchArc);
        return topology.getDownHead(cchArc - topology.getUpArcs());
    }

    private static long edgeKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xffffffffL);
    }
}
