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

class NodeBasedCCHQueryTest {
    @Test
    void sourceEqualsTargetReturnsZero() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 4),
                        arc(1, 0, 1, true, 4)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(3));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(2, 2);

        assertTrue(result.isFound());
        assertEquals(0, result.getWeight(), 1.e-9);
        assertEquals(2, result.getSource());
        assertEquals(2, result.getTarget());
        assertEquals(2, result.getMeetingNode());
        assertTrue(result.getForwardSettledArray()[2]);
        assertTrue(result.getBackwardSettledArray()[2]);
    }

    @Test
    void oneWayGraphFindsOnlyAllowedDirection() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 1, false, 3)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
        NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, metric);

        CCHQueryResult forward = query.calc(0, 1);
        CCHQueryResult reverse = query.calc(1, 0);

        assertTrue(forward.isFound());
        assertEquals(3, forward.getWeight(), 1.e-9);
        assertEquals(1, forward.getMeetingNode());
        assertFalse(reverse.isFound());
        assertEquals(CCHQueryResult.NO_NODE, reverse.getMeetingNode());
        assertTrue(Double.isInfinite(reverse.getWeight()));
    }

    @Test
    void fillShortcutProvidesShortestDistance() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1),
                        arc(1, 2, 2, false, 2)),
                support(0, 1), support(1, 2));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(0, 2);

        int fillArc = cchArc(topology, 0, 2);
        assertTrue(result.isFound());
        assertEquals(3, result.getWeight(), 1.e-9);
        assertEquals(2, result.getMeetingNode());
        assertEquals(fillArc, result.getForwardPredecessorArcArray()[2]);
        assertEquals(CCHStorage.NO_ARC, result.getBackwardPredecessorArcArray()[2]);
    }

    @Test
    void shortcutBeatsMoreExpensiveDirectEdge() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1),
                        arc(1, 2, 2, false, 1),
                        arc(0, 2, 3, false, 5)),
                support(0, 1), support(1, 2), support(0, 2));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(0, 2);

        assertTrue(result.isFound());
        assertEquals(2, result.getWeight(), 1.e-9);
        assertEquals(cchArc(topology, 0, 2), result.getForwardPredecessorArcArray()[2]);
    }

    @Test
    void disconnectedGraphReturnsNoPath() {
        CCHInputGraph inputGraph = inputGraph(4,
                Arrays.asList(
                        arc(0, 1, 1, false, 1),
                        arc(2, 3, 2, false, 1)),
                support(0, 1), support(2, 3));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(4));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(0, 3);

        assertFalse(result.isFound());
        assertEquals(CCHQueryResult.NO_NODE, result.getMeetingNode());
        assertTrue(Double.isInfinite(result.getWeight()));
    }

    @Test
    void resultArraysAreDefensiveCopies() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 1, false, 3)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
        CCHQueryResult result = new NodeBasedCCHQuery(topology, metric).calc(0, 1);

        result.getForwardDistanceArray()[1] = 99;
        result.getForwardPredecessorArcArray()[1] = 99;
        result.getForwardSettledArray()[1] = false;

        assertEquals(3, result.getForwardDistanceArray()[1], 1.e-9);
        assertEquals(cchArc(topology, 0, 1), result.getForwardPredecessorArcArray()[1]);
        assertTrue(result.getForwardSettledArray()[1]);
    }

    @Test
    void validatesInputs() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 1, false, 3)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        assertThrows(NullPointerException.class, () -> new NodeBasedCCHQuery(null, metric));
        assertThrows(NullPointerException.class, () -> new NodeBasedCCHQuery(topology, null));
        assertThrows(NullPointerException.class, () -> new NodeBasedCCHQuery(topology, metric, null));
        assertThrows(IllegalArgumentException.class, () -> new NodeBasedCCHQuery(topology, new CCHMetric(1)));
        NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, metric);
        assertThrows(IllegalArgumentException.class, () -> query.calc(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> query.calc(0, 2));
    }

    @Test
    void randomizedSmallGraphsMatchFlexibleDijkstra() {
        for (int seed = 0; seed < 50; seed++) {
            CCHInputGraph inputGraph = randomInputGraph(seed);
            CCHNodeOrder order = randomOrder(inputGraph.getNodes(), seed + 1000);
            CCHTopology topology = topology(inputGraph, order);
            CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
            NodeBasedCCHQuery query = new NodeBasedCCHQuery(topology, metric);

            for (int source = 0; source < inputGraph.getNodes(); source++) {
                for (int target = 0; target < inputGraph.getNodes(); target++) {
                    double expected = dijkstra(inputGraph, source, target);
                    CCHQueryResult result = query.calc(source, target);
                    assertEquals(expected, result.getWeight(), 1.e-9,
                            "seed=" + seed + ", source=" + source + ", target=" + target);
                    assertEquals(Double.isFinite(expected), result.isFound(),
                            "seed=" + seed + ", source=" + source + ", target=" + target);
                }
            }
        }
    }

    private static CCHInputGraph randomInputGraph(int seed) {
        Random random = new Random(seed);
        int nodes = 2 + random.nextInt(7);
        List<CCHInputArc> arcs = new ArrayList<>();
        Set<CCHInputEdge> supportEdges = new HashSet<>();
        int baseEdge = 0;
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 1; b < nodes; b++) {
                if (random.nextInt(100) >= 45)
                    continue;
                boolean forward = random.nextBoolean();
                boolean backward = random.nextBoolean();
                if (!forward && !backward)
                    forward = true;
                int parallelArcs = 1 + random.nextInt(3);
                supportEdges.add(support(a, b));
                for (int i = 0; i < parallelArcs; i++) {
                    if (forward)
                        arcs.add(arc(a, b, baseEdge++, false, 1 + random.nextInt(20)));
                    if (backward)
                        arcs.add(arc(b, a, baseEdge++, true, 1 + random.nextInt(20)));
                }
            }
        }
        return new CCHInputGraph(nodes, arcs, new ArrayList<>(supportEdges));
    }

    private static CCHNodeOrder randomOrder(int nodes, int seed) {
        Random random = new Random(seed);
        int[] order = new int[nodes];
        for (int i = 0; i < nodes; i++) {
            order[i] = i;
        }
        for (int i = nodes - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[swap];
            order[swap] = tmp;
        }
        return CCHNodeOrder.fromOrder(order);
    }

    private static double dijkstra(CCHInputGraph inputGraph, int source, int target) {
        double[] distance = new double[inputGraph.getNodes()];
        boolean[] settled = new boolean[inputGraph.getNodes()];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        distance[source] = 0;
        while (true) {
            int node = unsettledNodeWithSmallestDistance(distance, settled);
            if (node < 0)
                return Double.POSITIVE_INFINITY;
            if (node == target)
                return distance[node];
            settled[node] = true;
            for (CCHInputArc arc : inputGraph.getAllArcs()) {
                if (arc.getFrom() != node)
                    continue;
                double nextDistance = distance[node] + arc.getWeight();
                if (nextDistance < distance[arc.getTo()])
                    distance[arc.getTo()] = nextDistance;
            }
        }
    }

    private static int unsettledNodeWithSmallestDistance(double[] distance, boolean[] settled) {
        int bestNode = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int node = 0; node < distance.length; node++) {
            if (!settled[node] && distance[node] < bestDistance) {
                bestNode = node;
                bestDistance = distance[node];
            }
        }
        return bestNode;
    }

    private static CCHTopology topology(CCHInputGraph inputGraph, CCHNodeOrder order) {
        return new CCHTopologyBuilder().build(inputGraph, order);
    }

    private static CCHInputGraph inputGraph(int nodes, List<CCHInputArc> arcs, CCHInputEdge... supportEdges) {
        return new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
    }

    private static CCHInputArc arc(int from, int to, int baseEdge, boolean reverse, double weight) {
        return new CCHInputArc(from, to, baseEdge, reverse, weight, Math.round(weight * 10), weight * 100);
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static int cchArc(CCHTopology topology, int tail, int head) {
        for (int arc = 0; arc < topology.getArcs(); arc++) {
            if (topology.getTail(arc) == tail && topology.getHead(arc) == head)
                return arc;
        }
        throw new AssertionError("missing CCH arc " + tail + "->" + head);
    }
}
