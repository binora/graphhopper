// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBasedCCHMetricCustomizerTest {
    @Test
    void initializesFiniteEdgeTransitionsAndLeavesRestrictedTurnsInfinite() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = edgeTopology(edgeGraph);

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(first, true, 11, 110)
                .edge(second, false, 20, 200)
                .edge(second, true, 21, 210)
                .turn(first, 1, second, 5, 50)
                .turn(second, 1, first, Double.POSITIVE_INFINITY, 0);
        CCHMetric metric = customize(graph, weighting, edgeGraph, edgeTopology);

        int allowedArc = edgeTopology.getTopology().findArc(state(edgeGraph, first, false), state(edgeGraph, second, false));
        assertTrue(metric.isEdgeTransition(allowedArc));
        assertEquals(25, metric.getWeight(allowedArc), 1.e-9);
        assertEquals(250, metric.getMillis(allowedArc));
        assertEquals(5, metric.getTurnWeight(allowedArc), 1.e-9);
        assertEquals(20, metric.getEdgeWeight(allowedArc), 1.e-9);
        assertEquals(GHUtility.createEdgeKey(first, false), metric.getIncomingEdgeKey(allowedArc));
        assertEquals(GHUtility.createEdgeKey(second, false), metric.getOutgoingEdgeKey(allowedArc));

        int restrictedArc = edgeTopology.getTopology().findArc(state(edgeGraph, second, true), state(edgeGraph, first, true));
        assertTrue(Double.isInfinite(metric.getWeight(restrictedArc)));
        assertEquals(CCHMetricProvenance.Type.NONE, metric.getProvenance(restrictedArc).getType());
    }

    @Test
    void lowerTriangleCustomizationCreatesEdgeStateShortcut() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        int third = graph.edge(2, 3).setDistance(30).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        int firstState = state(edgeGraph, first, false);
        int secondState = state(edgeGraph, second, false);
        int thirdState = state(edgeGraph, third, false);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, order(edgeGraph.getStates(), secondState, firstState, thirdState));

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(second, false, 20, 200)
                .edge(third, false, 30, 300)
                .turn(first, 1, second, 5, 50)
                .turn(second, 2, third, 7, 70);
        CCHMetric metric = customize(graph, weighting, edgeGraph, edgeTopology);

        int shortcutArc = edgeTopology.getTopology().findArc(firstState, thirdState);
        assertNotEquals(CCHStorage.NO_ARC, shortcutArc);
        assertTrue(edgeTopology.getTopology().isFillArc(shortcutArc));
        assertTrue(metric.isShortcut(shortcutArc));
        assertEquals(62, metric.getWeight(shortcutArc), 1.e-9);
        assertEquals(620, metric.getMillis(shortcutArc));
        assertEquals(50, metric.getDistance(shortcutArc), 1.e-9);
        assertEquals(edgeTopology.getTopology().findArc(firstState, secondState), metric.getSkippedArc1(shortcutArc));
        assertEquals(edgeTopology.getTopology().findArc(secondState, thirdState), metric.getSkippedArc2(shortcutArc));
    }

    @Test
    void customizedArcWeightsMatchRestrictedEdgeStateDijkstra() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        int third = graph.edge(1, 3).setDistance(30).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHNodeOrder order = order(edgeGraph.getStates(), state(edgeGraph, second, true), state(edgeGraph, first, false));
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, order);

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(first, true, 11, 110)
                .edge(second, false, 20, 200)
                .edge(second, true, 21, 210)
                .edge(third, false, 30, 300)
                .edge(third, true, 31, 310)
                .turn(first, 1, second, 3, 30)
                .turn(first, 1, third, 5, 50)
                .turn(second, 1, third, 7, 70)
                .turn(third, 1, second, Double.POSITIVE_INFINITY, 0);
        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology());
        CCHMetric metric = new EdgeBasedCCHMetricCustomizer().customize(edgeTopology, source);

        double[] directWeights = directWeights(edgeTopology.getTopology(), edgeGraph, source);
        for (int cchArc = 0; cchArc < edgeTopology.getTopology().getArcs(); cchArc++) {
            double expected = restrictedDijkstra(edgeTopology.getTopology(), edgeGraph, directWeights,
                    edgeTopology.getTopology().getTail(cchArc), edgeTopology.getTopology().getHead(cchArc));
            assertEquals(expected, metric.getWeight(cchArc), 1.e-9,
                    edgeTopology.getTopology().getTail(cchArc) + "->" + edgeTopology.getTopology().getHead(cchArc));
        }
    }

    @Test
    void rejectsNodeBasedSourcesForEdgeCustomization() {
        BaseGraph edgeBase = new BaseGraph.Builder(1).create();
        edgeBase.edge(0, 1).setDistance(1);
        EdgeStateCCHTopology edgeTopology = edgeTopology(EdgeStateCCHInputBuilder.fromGraph(edgeBase));
        CCHInputGraph inputGraph = new CCHInputGraph(2,
                Arrays.asList(new CCHInputArc(0, 1, 0, false, 1, 10, 1)),
                Arrays.asList(new CCHInputEdge(0, 1)));
        CCHTopology nodeTopology = new CCHTopologyBuilder().build(inputGraph, CCHNodeOrder.identity(2));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EdgeBasedCCHMetricCustomizer().customize(
                        edgeTopology,
                        new NodeBasedCCHMetricSource(nodeTopology, inputGraph)));

        assertTrue(error.getMessage().contains("edge-based"), error.getMessage());
    }

    private static CCHMetric customize(BaseGraph graph, Weighting weighting, EdgeStateCCHInputGraph edgeGraph,
                                       EdgeStateCCHTopology edgeTopology) {
        return new EdgeBasedCCHMetricCustomizer().customize(edgeTopology,
                new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, edgeTopology.getTopology()));
    }

    private static EdgeStateCCHTopology edgeTopology(EdgeStateCCHInputGraph edgeGraph) {
        return new EdgeStateCCHTopologyBuilder().build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));
    }

    private static int state(EdgeStateCCHInputGraph edgeGraph, int edge, boolean reverse) {
        return edgeGraph.getStateForEdgeKey(GHUtility.createEdgeKey(edge, reverse));
    }

    private static CCHNodeOrder order(int nodes, int... firstNodes) {
        int[] order = new int[nodes];
        boolean[] used = new boolean[nodes];
        int index = 0;
        for (int node : firstNodes) {
            if (!used[node]) {
                order[index++] = node;
                used[node] = true;
            }
        }
        for (int node = 0; node < nodes; node++) {
            if (!used[node])
                order[index++] = node;
        }
        return CCHNodeOrder.fromOrder(order);
    }

    private static double[] directWeights(CCHTopology topology, EdgeStateCCHInputGraph edgeGraph,
                                          EdgeBasedCCHMetricSource source) {
        double[] directWeights = new double[edgeGraph.getInputGraph().getArcs()];
        Arrays.fill(directWeights, Double.POSITIVE_INFINITY);
        for (int inputArc = 0; inputArc < edgeGraph.getInputGraph().getArcs(); inputArc++) {
            int cchArc = topology.getInputArcCCHArc(inputArc);
            for (int i = 0; i < source.getCandidates(); i++) {
                CCHMetricCandidate candidate = source.getCandidate(i);
                if (candidate.getCCHArc() == cchArc) {
                    directWeights[inputArc] = Math.min(directWeights[inputArc], candidate.getWeight());
                }
            }
        }
        return directWeights;
    }

    private static double restrictedDijkstra(CCHTopology topology, EdgeStateCCHInputGraph edgeGraph,
                                             double[] directWeights, int source, int target) {
        double[] dist = new double[edgeGraph.getStates()];
        boolean[] settled = new boolean[edgeGraph.getStates()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        int minimumEndpointRank = Math.min(topology.getNodeOrder().getRank(source), topology.getNodeOrder().getRank(target));
        while (true) {
            int state = unsettledStateWithSmallestDistance(dist, settled);
            if (state < 0)
                return Double.POSITIVE_INFINITY;
            if (state == target)
                return dist[state];
            settled[state] = true;
            CCHInputGraph inputGraph = edgeGraph.getInputGraph();
            for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
                CCHInputArc arc = inputGraph.getArc(inputArc);
                if (arc.getFrom() != state || !Double.isFinite(directWeights[inputArc]))
                    continue;
                int next = arc.getTo();
                if (next != target && topology.getNodeOrder().getRank(next) >= minimumEndpointRank)
                    continue;
                double nextWeight = dist[state] + directWeights[inputArc];
                if (nextWeight < dist[next])
                    dist[next] = nextWeight;
            }
        }
    }

    private static int unsettledStateWithSmallestDistance(double[] dist, boolean[] settled) {
        int bestState = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int state = 0; state < dist.length; state++) {
            if (!settled[state] && dist[state] < bestDistance) {
                bestState = state;
                bestDistance = dist[state];
            }
        }
        return bestState;
    }

    private static final class TestWeighting implements Weighting {
        private final Map<Integer, EdgeValue> edgeValues = new HashMap<>();
        private final Map<TurnKey, TurnValue> turnValues = new HashMap<>();

        TestWeighting edge(int edge, boolean reverse, double weight, long millis) {
            edgeValues.put(GHUtility.createEdgeKey(edge, reverse), new EdgeValue(weight, millis));
            return this;
        }

        TestWeighting turn(int inEdge, int viaNode, int outEdge, double weight, long millis) {
            turnValues.put(new TurnKey(inEdge, viaNode, outEdge), new TurnValue(weight, millis));
            return this;
        }

        @Override
        public double calcMinWeightPerDistance() {
            return 0;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return edgeValue(edgeState, reverse).weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return edgeValue(edgeState, reverse).millis;
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return turnValues.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).weight;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return turnValues.getOrDefault(new TurnKey(inEdge, viaNode, outEdge), TurnValue.ZERO).millis;
        }

        @Override
        public boolean hasTurnCosts() {
            return true;
        }

        @Override
        public String getName() {
            return "edge_based_customizer_test";
        }

        private EdgeValue edgeValue(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = reverse ? edgeState.getReverseEdgeKey() : edgeState.getEdgeKey();
            return edgeValues.getOrDefault(edgeKey, EdgeValue.INACCESSIBLE);
        }
    }

    private static final class EdgeValue {
        private static final EdgeValue INACCESSIBLE = new EdgeValue(Double.POSITIVE_INFINITY, 0);

        private final double weight;
        private final long millis;

        private EdgeValue(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }
    }

    private static final class TurnValue {
        private static final TurnValue ZERO = new TurnValue(0, 0);

        private final double weight;
        private final long millis;

        private TurnValue(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }
    }

    private static final class TurnKey {
        private final int inEdge;
        private final int viaNode;
        private final int outEdge;

        private TurnKey(int inEdge, int viaNode, int outEdge) {
            this.inEdge = inEdge;
            this.viaNode = viaNode;
            this.outEdge = outEdge;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TurnKey))
                return false;
            TurnKey key = (TurnKey) o;
            return inEdge == key.inEdge && viaNode == key.viaNode && outEdge == key.outEdge;
        }

        @Override
        public int hashCode() {
            int result = inEdge;
            result = 31 * result + viaNode;
            result = 31 * result + outEdge;
            return result;
        }
    }
}
