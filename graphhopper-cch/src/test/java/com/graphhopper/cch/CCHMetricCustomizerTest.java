// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHMetricCustomizerTest {
    @Test
    void oneWayInputLeavesReverseArcInfinite() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 10, false, 3, 30, 300)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int forward = cchArc(topology, 0, 1);
        int reverse = cchArc(topology, 1, 0);
        assertEquals(3, metric.getWeight(forward), 1.e-9);
        assertEquals(30, metric.getMillis(forward));
        assertEquals(300, metric.getDistance(forward), 1.e-9);
        assertEquals(10, metric.getBaseEdge(forward));
        assertFalse(metric.isReverse(forward));
        assertTrue(Double.isInfinite(metric.getWeight(reverse)));
        assertEquals(CCHMetricProvenance.Type.NONE, metric.getProvenance(reverse).getType());
    }

    @Test
    void bidirectionalInputInitializesBothDirections() {
        CCHInputGraph inputGraph = inputGraph(2,
                Arrays.asList(
                        arc(0, 1, 7, false, 2, 20, 200),
                        arc(1, 0, 7, true, 4, 40, 200)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int forward = cchArc(topology, 0, 1);
        int reverse = cchArc(topology, 1, 0);
        assertEquals(2, metric.getWeight(forward), 1.e-9);
        assertEquals(4, metric.getWeight(reverse), 1.e-9);
        assertFalse(metric.isReverse(forward));
        assertTrue(metric.isReverse(reverse));
        assertEquals(7, metric.getBaseEdge(reverse));
    }

    @Test
    void parallelInputArcsUseMetricOrderThenStableSourceTieBreak() {
        CCHInputGraph inputGraph = inputGraph(2,
                Arrays.asList(
                        arc(0, 1, 10, false, 2, 10, 1),
                        arc(0, 1, 9, false, 2, 5, 10),
                        arc(0, 1, 8, false, 2, 5, 4),
                        arc(0, 1, 7, false, 2, 5, 4)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int forward = cchArc(topology, 0, 1);
        assertEquals(2, metric.getWeight(forward), 1.e-9);
        assertEquals(5, metric.getMillis(forward));
        assertEquals(4, metric.getDistance(forward), 1.e-9);
        assertEquals(7, metric.getBaseEdge(forward));
    }

    @Test
    void fillArcBecomesFiniteViaLowerTriangle() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1, 10, 100),
                        arc(1, 2, 2, false, 2, 20, 200)),
                support(0, 1), support(1, 2));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int fillArc = cchArc(topology, 0, 2);
        assertTrue(topology.isFillArc(fillArc));
        assertEquals(3, metric.getWeight(fillArc), 1.e-9);
        assertEquals(30, metric.getMillis(fillArc));
        assertEquals(300, metric.getDistance(fillArc), 1.e-9);
        assertTrue(metric.isShortcut(fillArc));
        assertEquals(cchArc(topology, 0, 1), metric.getSkippedArc1(fillArc));
        assertEquals(cchArc(topology, 1, 2), metric.getSkippedArc2(fillArc));
    }

    @Test
    void directArcRemainsOnExactShortcutTie() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1, 10, 100),
                        arc(1, 2, 2, false, 1, 10, 100),
                        arc(0, 2, 3, false, 2, 99, 999)),
                support(0, 1), support(1, 2), support(0, 2));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int direct = cchArc(topology, 0, 2);
        assertTrue(metric.isDirect(direct));
        assertEquals(3, metric.getBaseEdge(direct));
        assertEquals(99, metric.getMillis(direct));
        assertEquals(CCHStorage.NO_ARC, metric.getSkippedArc1(direct));
    }

    @Test
    void shortcutWinsWhenStrictlyCheaperThanDirectArc() {
        CCHInputGraph inputGraph = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1, 10, 100),
                        arc(1, 2, 2, false, 1, 10, 100),
                        arc(0, 2, 3, false, 5, 50, 500)),
                support(0, 1), support(1, 2), support(0, 2));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        int target = cchArc(topology, 0, 2);
        assertTrue(metric.isShortcut(target));
        assertEquals(2, metric.getWeight(target), 1.e-9);
        assertEquals(cchArc(topology, 0, 1), metric.getSkippedArc1(target));
        assertEquals(cchArc(topology, 1, 2), metric.getSkippedArc2(target));
        assertEquals(CCHStorage.NO_ARC, metric.getBaseEdge(target));
    }

    @Test
    void repeatedCustomizationDoesNotChangeTopologyArrays() {
        CCHInputGraph firstInput = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1, 10, 100),
                        arc(1, 2, 2, false, 1, 10, 100)),
                support(0, 1), support(1, 2));
        CCHInputGraph secondInput = inputGraph(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 5, 50, 500),
                        arc(1, 2, 2, false, 7, 70, 700)),
                support(0, 1), support(1, 2));
        CCHTopology topology = topology(firstInput, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        int[] upFirstOut = topology.getUpFirstOutArray();
        int[] upHead = topology.getUpHeadArray();
        int[] downFirstOut = topology.getDownFirstOutArray();
        int[] downHead = topology.getDownHeadArray();

        CCHMetricCustomizer customizer = new CCHMetricCustomizer();
        CCHMetric firstMetric = customizer.customize(topology, firstInput);
        CCHMetric secondMetric = customizer.customize(topology, secondInput);

        int fillArc = cchArc(topology, 0, 2);
        assertEquals(2, firstMetric.getWeight(fillArc), 1.e-9);
        assertEquals(12, secondMetric.getWeight(fillArc), 1.e-9);
        assertArrayEquals(upFirstOut, topology.getUpFirstOutArray());
        assertArrayEquals(upHead, topology.getUpHeadArray());
        assertArrayEquals(downFirstOut, topology.getDownFirstOutArray());
        assertArrayEquals(downHead, topology.getDownHeadArray());
    }

    @Test
    void rejectsEdgeBasedAndTurnCostSourcesForV1() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 1, false, 1, 10, 100)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));
        CCHMetricCandidate candidate = new CCHMetricCandidate(cchArc(topology, 0, 1), 1, 10, 100,
                CCHMetricProvenance.direct(1, false), 2);
        CCHMetricCustomizer customizer = new CCHMetricCustomizer();

        IllegalArgumentException edgeBasedError = assertThrows(IllegalArgumentException.class,
                () -> customizer.customize(topology, new SyntheticSource(2, TraversalMode.EDGE_BASED, false, candidate)));
        assertTrue(edgeBasedError.getMessage().contains("node-based"));

        IllegalArgumentException turnCostError = assertThrows(IllegalArgumentException.class,
                () -> customizer.customize(topology, new SyntheticSource(2, TraversalMode.NODE_BASED, true, candidate)));
        assertTrue(turnCostError.getMessage().contains("without turn costs"));
    }

    @Test
    void validatesNullsAndNodeCounts() {
        CCHInputGraph inputGraph = inputGraph(2,
                Collections.singletonList(arc(0, 1, 1, false, 1, 10, 100)),
                support(0, 1));
        CCHTopology topology = topology(inputGraph, CCHNodeOrder.identity(2));
        CCHMetricCustomizer customizer = new CCHMetricCustomizer();

        assertThrows(NullPointerException.class, () -> customizer.customize(null, inputGraph));
        assertThrows(NullPointerException.class, () -> customizer.customize(topology, (CCHInputGraph) null));
        assertThrows(NullPointerException.class, () -> customizer.customize(topology, (CCHMetricSource) null));
        assertThrows(NullPointerException.class, () -> new NodeBasedCCHMetricSource(null, inputGraph));
        assertThrows(NullPointerException.class, () -> new NodeBasedCCHMetricSource(topology, null));
        assertThrows(IllegalArgumentException.class,
                () -> customizer.customize(topology, new SyntheticSource(3, TraversalMode.NODE_BASED, false)));
    }

    @Test
    void customizedArcWeightsMatchRestrictedDirectedDijkstra() {
        CCHInputGraph inputGraph = inputGraph(4,
                Arrays.asList(
                        arc(0, 1, 1, false, 1, 10, 100),
                        arc(1, 0, 1, true, 1, 10, 100),
                        arc(1, 2, 2, false, 2, 20, 200),
                        arc(2, 1, 2, true, 2, 20, 200),
                        arc(0, 3, 3, false, 10, 100, 1000),
                        arc(3, 0, 3, true, 10, 100, 1000),
                        arc(2, 3, 4, false, 20, 200, 2000),
                        arc(3, 2, 4, true, 20, 200, 2000)),
                support(0, 1), support(1, 2), support(0, 3), support(2, 3));
        CCHNodeOrder order = CCHNodeOrder.fromOrder(new int[]{1, 0, 2, 3});
        CCHTopology topology = topology(inputGraph, order);

        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);

        for (int cchArc = 0; cchArc < topology.getArcs(); cchArc++) {
            double expected = restrictedDijkstra(inputGraph, order, topology.getTail(cchArc), topology.getHead(cchArc));
            assertEquals(expected, metric.getWeight(cchArc), 1.e-9, topology.getTail(cchArc) + "->" + topology.getHead(cchArc));
        }
    }

    private static double restrictedDijkstra(CCHInputGraph inputGraph, CCHNodeOrder order, int source, int target) {
        double[] dist = new double[inputGraph.getNodes()];
        boolean[] settled = new boolean[inputGraph.getNodes()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        int minimumEndpointRank = Math.min(order.getRank(source), order.getRank(target));
        while (true) {
            int node = unsettledNodeWithSmallestDistance(dist, settled);
            if (node < 0)
                return Double.POSITIVE_INFINITY;
            if (node == target)
                return dist[node];
            settled[node] = true;
            for (CCHInputArc arc : inputGraph.getAllArcs()) {
                if (arc.getFrom() != node)
                    continue;
                int next = arc.getTo();
                if (next != target && order.getRank(next) >= minimumEndpointRank)
                    continue;
                double nextWeight = dist[node] + arc.getWeight();
                if (nextWeight < dist[next])
                    dist[next] = nextWeight;
            }
        }
    }

    private static int unsettledNodeWithSmallestDistance(double[] dist, boolean[] settled) {
        int bestNode = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int node = 0; node < dist.length; node++) {
            if (!settled[node] && dist[node] < bestDistance) {
                bestNode = node;
                bestDistance = dist[node];
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

    private static CCHInputArc arc(int from, int to, int baseEdge, boolean reverse, double weight, long millis, double distance) {
        return new CCHInputArc(from, to, baseEdge, reverse, weight, millis, distance);
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

    private static final class SyntheticSource implements CCHMetricSource {
        private final int nodes;
        private final TraversalMode traversalMode;
        private final boolean turnCosts;
        private final List<CCHMetricCandidate> candidates;

        private SyntheticSource(int nodes, TraversalMode traversalMode, boolean turnCosts, CCHMetricCandidate... candidates) {
            this.nodes = nodes;
            this.traversalMode = traversalMode;
            this.turnCosts = turnCosts;
            this.candidates = new ArrayList<>(Arrays.asList(candidates));
        }

        @Override
        public int getNodes() {
            return nodes;
        }

        @Override
        public TraversalMode getTraversalMode() {
            return traversalMode;
        }

        @Override
        public boolean hasTurnCosts() {
            return turnCosts;
        }

        @Override
        public int getCandidates() {
            return candidates.size();
        }

        @Override
        public CCHMetricCandidate getCandidate(int index) {
            return candidates.get(index);
        }
    }
}
