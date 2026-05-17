// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHTriangleEnumeratorTest {
    @Test
    void enumeratesTriangleRolesForIdentityClique() {
        CCHTopology topology = cliqueTopology(CCHNodeOrder.identity(3));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);

        int arc02 = arc(topology, 0, 2);
        assertEquals(Collections.singletonList(triangle(CCHTriangleType.INTERMEDIATE, arc02,
                arc(topology, 0, 1), arc(topology, 1, 2))), enumerator.getIntermediateTriangles(arc02));
        assertTrue(enumerator.getLowerTriangles(arc02).isEmpty());
        assertTrue(enumerator.getUpperTriangles(arc02).isEmpty());

        int arc12 = arc(topology, 1, 2);
        assertEquals(Collections.singletonList(triangle(CCHTriangleType.LOWER, arc12,
                arc(topology, 1, 0), arc(topology, 0, 2))), enumerator.getLowerTriangles(arc12));

        int arc01 = arc(topology, 0, 1);
        assertEquals(Collections.singletonList(triangle(CCHTriangleType.UPPER, arc01,
                arc(topology, 0, 2), arc(topology, 2, 1))), enumerator.getUpperTriangles(arc01));
    }

    @Test
    void enumeratesDirectedWitnessPathForDownwardTarget() {
        CCHTopology topology = cliqueTopology(CCHNodeOrder.identity(3));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        int arc20 = arc(topology, 2, 0);

        List<CCHTriangle> triangles = enumerator.getIntermediateTriangles(arc20);

        assertEquals(Collections.singletonList(triangle(CCHTriangleType.INTERMEDIATE, arc20,
                arc(topology, 2, 1), arc(topology, 1, 0))), triangles);
        assertWitnessPathEndpoints(topology, triangles.get(0));
    }

    @Test
    void pathFillEdgeHasLowerTriangle() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(3, support(0, 1), support(1, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        int fillArc = arc(topology, 0, 2);

        assertTrue(topology.isFillArc(fillArc));
        assertEquals(Collections.singletonList(triangle(CCHTriangleType.LOWER, fillArc,
                arc(topology, 0, 1), arc(topology, 1, 2))), enumerator.getLowerTriangles(fillArc));
    }

    @Test
    void disconnectedGraphHasNoCrossComponentTriangles() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(5, support(0, 1), support(2, 3)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 3, 2, 4}));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);

        for (int arc = 0; arc < topology.getArcs(); arc++) {
            assertTrue(enumerator.getLowerTriangles(arc).isEmpty());
            assertTrue(enumerator.getIntermediateTriangles(arc).isEmpty());
            assertTrue(enumerator.getUpperTriangles(arc).isEmpty());
        }
    }

    @Test
    void enumerationOrderIsDeterministicByWitnessRankThenNodeId() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(4, support(0, 1), support(0, 2), support(0, 3), support(1, 3), support(2, 3)),
                CCHNodeOrder.fromOrder(new int[]{0, 2, 1, 3}));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        int arc03 = arc(topology, 0, 3);

        List<CCHTriangle> first = enumerator.getIntermediateTriangles(arc03);
        List<CCHTriangle> second = enumerator.getIntermediateTriangles(arc03);

        assertEquals(Arrays.asList(
                triangle(CCHTriangleType.INTERMEDIATE, arc03, arc(topology, 0, 2), arc(topology, 2, 3)),
                triangle(CCHTriangleType.INTERMEDIATE, arc03, arc(topology, 0, 1), arc(topology, 1, 3))),
                first);
        assertEquals(first, second);
    }

    @Test
    void consumerEnumerationMatchesListEnumeration() {
        CCHTopology topology = cliqueTopology(CCHNodeOrder.identity(3));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);
        int arc02 = arc(topology, 0, 2);
        List<CCHTriangle> triangles = new ArrayList<>();

        enumerator.forEachIntermediateTriangle(arc02, triangles::add);

        assertEquals(enumerator.getIntermediateTriangles(arc02), triangles);
    }

    @Test
    void rejectsInvalidInputs() {
        CCHTopology topology = cliqueTopology(CCHNodeOrder.identity(3));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);

        assertThrows(NullPointerException.class, () -> new CCHTriangleEnumerator(null));
        assertThrows(IllegalArgumentException.class, () -> enumerator.getLowerTriangles(-1));
        assertThrows(IllegalArgumentException.class, () -> enumerator.getLowerTriangles(topology.getArcs()));
        assertThrows(NullPointerException.class, () -> enumerator.forEachLowerTriangle(0, null));
    }

    @Test
    void returnedTrianglesHaveValidRoleEndpoints() {
        CCHTopology topology = new CCHTopologyBuilder().build(
                inputGraph(4, support(0, 1), support(0, 2), support(0, 3), support(1, 2), support(1, 3), support(2, 3)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2, 3}));
        CCHTriangleEnumerator enumerator = new CCHTriangleEnumerator(topology);

        for (int arc = 0; arc < topology.getArcs(); arc++) {
            assertRoleAndEndpoints(topology, CCHTriangleType.LOWER, enumerator.getLowerTriangles(arc));
            assertRoleAndEndpoints(topology, CCHTriangleType.INTERMEDIATE, enumerator.getIntermediateTriangles(arc));
            assertRoleAndEndpoints(topology, CCHTriangleType.UPPER, enumerator.getUpperTriangles(arc));
        }
    }

    private static void assertRoleAndEndpoints(CCHTopology topology, CCHTriangleType type, List<CCHTriangle> triangles) {
        for (CCHTriangle triangle : triangles) {
            assertEquals(type, triangle.getType());
            assertWitnessPathEndpoints(topology, triangle);
            int targetTailRank = topology.getNodeOrder().getRank(topology.getTail(triangle.getTargetArc()));
            int targetHeadRank = topology.getNodeOrder().getRank(topology.getHead(triangle.getTargetArc()));
            int witnessRank = topology.getNodeOrder().getRank(topology.getHead(triangle.getFirstWitnessArc()));
            int minRank = Math.min(targetTailRank, targetHeadRank);
            int maxRank = Math.max(targetTailRank, targetHeadRank);
            if (type == CCHTriangleType.LOWER)
                assertTrue(witnessRank < minRank);
            else if (type == CCHTriangleType.INTERMEDIATE)
                assertTrue(witnessRank > minRank && witnessRank < maxRank);
            else
                assertTrue(witnessRank > maxRank);
        }
    }

    private static void assertWitnessPathEndpoints(CCHTopology topology, CCHTriangle triangle) {
        assertEquals(topology.getTail(triangle.getTargetArc()), topology.getTail(triangle.getFirstWitnessArc()));
        assertEquals(topology.getHead(triangle.getFirstWitnessArc()), topology.getTail(triangle.getSecondWitnessArc()));
        assertEquals(topology.getHead(triangle.getTargetArc()), topology.getHead(triangle.getSecondWitnessArc()));
    }

    private static CCHTriangle triangle(CCHTriangleType type, int targetArc, int firstWitnessArc, int secondWitnessArc) {
        return new CCHTriangle(type, targetArc, firstWitnessArc, secondWitnessArc);
    }

    private static CCHTopology cliqueTopology(CCHNodeOrder order) {
        return new CCHTopologyBuilder().build(inputGraph(3, support(0, 1), support(0, 2), support(1, 2)), order);
    }

    private static CCHInputGraph inputGraph(int nodes, CCHInputEdge... supportEdges) {
        List<CCHInputArc> arcs = new ArrayList<>();
        int baseEdge = 0;
        for (CCHInputEdge edge : supportEdges) {
            arcs.add(new CCHInputArc(edge.getNodeA(), edge.getNodeB(), baseEdge, false, 1, 1, 1));
            arcs.add(new CCHInputArc(edge.getNodeB(), edge.getNodeA(), baseEdge, true, 1, 1, 1));
            baseEdge++;
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static int arc(CCHTopology topology, int tail, int head) {
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            if (topology.getTail(arc) == tail && topology.getHead(arc) == head)
                return arc;
        }
        throw new AssertionError("missing arc " + tail + "->" + head);
    }
}
