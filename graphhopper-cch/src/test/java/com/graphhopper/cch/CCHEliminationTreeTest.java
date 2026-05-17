// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHEliminationTreeTest {
    @Test
    void identityPathOrderBuildsParentChain() {
        CCHTopology topology = topology(inputGraph(3, support(0, 1), support(1, 2)), CCHNodeOrder.identity(3));

        CCHEliminationTree tree = new CCHEliminationTree(topology);

        assertArrayEquals(new int[]{1, 2, CCHEliminationTree.NO_NODE}, tree.getParentArray());
        assertEquals(Arrays.asList(0, 1, 2), ancestors(tree, 0));
    }

    @Test
    void fillTopologyParticipatesInParentChain() {
        CCHTopology topology = topology(inputGraph(3, support(0, 1), support(1, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        CCHEliminationTree tree = new CCHEliminationTree(topology);

        assertArrayEquals(new int[]{2, 0, CCHEliminationTree.NO_NODE}, tree.getParentArray());
        assertEquals(Arrays.asList(1, 0, 2), ancestors(tree, 1));
    }

    @Test
    void disconnectedComponentsProduceMultipleRoots() {
        CCHTopology topology = topology(inputGraph(5, support(0, 1), support(2, 3)), CCHNodeOrder.identity(5));

        CCHEliminationTree tree = new CCHEliminationTree(topology);

        assertArrayEquals(new int[]{1, CCHEliminationTree.NO_NODE, 3, CCHEliminationTree.NO_NODE, CCHEliminationTree.NO_NODE},
                tree.getParentArray());
    }

    @Test
    void parentsAlwaysHaveHigherRank() {
        CCHTopology topology = topology(inputGraph(4, support(0, 1), support(0, 2), support(1, 3), support(2, 3)),
                CCHNodeOrder.fromOrder(new int[]{2, 0, 1, 3}));

        CCHEliminationTree tree = new CCHEliminationTree(topology);

        for (int node = 0; node < topology.getNodes(); node++) {
            int parent = tree.getParent(node);
            if (parent != CCHEliminationTree.NO_NODE)
                assertTrue(topology.getNodeOrder().getRank(parent) > topology.getNodeOrder().getRank(node));
        }
    }

    @Test
    void rejectsInvalidInputs() {
        CCHTopology topology = topology(inputGraph(2, support(0, 1)), CCHNodeOrder.identity(2));
        CCHEliminationTree tree = new CCHEliminationTree(topology);

        assertThrows(NullPointerException.class, () -> new CCHEliminationTree(null));
        assertThrows(IllegalArgumentException.class, () -> tree.getParent(-1));
        assertThrows(IllegalArgumentException.class, () -> tree.getParent(2));
        assertThrows(IllegalArgumentException.class, () -> tree.forEachAncestor(2, node -> {
        }));
        assertThrows(NullPointerException.class, () -> tree.forEachAncestor(0, null));
    }

    private static List<Integer> ancestors(CCHEliminationTree tree, int node) {
        List<Integer> ancestors = new ArrayList<>();
        tree.forEachAncestor(node, ancestors::add);
        return ancestors;
    }

    private static CCHTopology topology(CCHInputGraph inputGraph, CCHNodeOrder order) {
        return new CCHTopologyBuilder().build(inputGraph, order);
    }

    private static CCHInputGraph inputGraph(int nodes, CCHInputEdge... supportEdges) {
        List<CCHInputArc> arcs = new ArrayList<>();
        int baseEdge = 0;
        for (CCHInputEdge edge : supportEdges) {
            arcs.add(arc(edge.getNodeA(), edge.getNodeB(), baseEdge, false, 1));
            arcs.add(arc(edge.getNodeB(), edge.getNodeA(), baseEdge, true, 1));
            baseEdge++;
        }
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputArc arc(int from, int to, int baseEdge, boolean reverse, double weight) {
        return new CCHInputArc(from, to, baseEdge, reverse, weight, 1, 1);
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }
}
