// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBasedCCHMetricSourceTest {
    @Test
    void buildsDeterministicEdgeStatesAndTurnTransitions() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();

        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);

        assertEquals(3, edgeGraph.getBaseNodes());
        assertEquals(2, edgeGraph.getBaseEdges());
        assertEquals(4, edgeGraph.getStates());
        assertState(edgeGraph, 0, GHUtility.createEdgeKey(first, false), 0, 1);
        assertState(edgeGraph, 1, GHUtility.createEdgeKey(first, true), 1, 0);
        assertState(edgeGraph, 2, GHUtility.createEdgeKey(second, false), 1, 2);
        assertState(edgeGraph, 3, GHUtility.createEdgeKey(second, true), 2, 1);

        CCHInputGraph inputGraph = edgeGraph.getInputGraph();
        assertEquals(6, inputGraph.getArcs());
        assertTransition(edgeGraph, inputGraph, 0, 1, 0, 0, 0);
        assertTransition(edgeGraph, inputGraph, 1, 0, 1, 1, 0);
        assertTransition(edgeGraph, inputGraph, 2, 0, 2, 1, 1);
        assertTransition(edgeGraph, inputGraph, 3, 3, 1, 1, 0);
        assertTransition(edgeGraph, inputGraph, 4, 3, 2, 1, 1);
        assertTransition(edgeGraph, inputGraph, 5, 2, 3, 2, 1);
        assertEquals(4, inputGraph.getSupportEdges());
    }

    @Test
    void metricSourceEmitsTurnAndOutgoingEdgeCostWithProvenance() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHTopology topology = topology(edgeGraph);

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(first, true, 11, 110)
                .edge(second, false, 20, 200)
                .edge(second, true, Double.POSITIVE_INFINITY, 0)
                .turn(first, 1, second, 5, 50);

        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, topology);

        assertEquals(TraversalMode.EDGE_BASED, source.getTraversalMode());
        assertTrue(source.hasTurnCosts());
        CCHMetricCandidate candidate = candidateFor(source, edgeGraph, topology, state(edgeGraph, first, false), state(edgeGraph, second, false));
        assertEquals(25, candidate.getWeight(), 1.e-9);
        assertEquals(250, candidate.getMillis());
        assertEquals(20, candidate.getDistance(), 1.e-9);
        assertEquals(topology.findArc(state(edgeGraph, first, false), state(edgeGraph, second, false)), candidate.getCCHArc());

        CCHMetricProvenance provenance = candidate.getProvenance();
        assertTrue(provenance.isEdgeTransition());
        assertEquals(GHUtility.createEdgeKey(first, false), provenance.getIncomingEdgeKey());
        assertEquals(1, provenance.getViaNode());
        assertEquals(GHUtility.createEdgeKey(second, false), provenance.getOutgoingEdgeKey());
        assertEquals(second, provenance.getOutgoingBaseEdge());
        assertFalse(provenance.isOutgoingReverse());
        assertEquals(5, provenance.getTurnWeight(), 1.e-9);
        assertEquals(50, provenance.getTurnMillis());
        assertEquals(20, provenance.getEdgeWeight(), 1.e-9);
        assertEquals(200, provenance.getEdgeMillis());
        assertEquals(20, provenance.getEdgeDistance(), 1.e-9);
    }

    @Test
    void inaccessibleOutgoingDirectionsKeepTopologyButEmitNoCandidate() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int edge = graph.edge(0, 1).setDistance(10).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHTopology topology = topology(edgeGraph);

        TestWeighting weighting = new TestWeighting()
                .edge(edge, false, 10, 100)
                .edge(edge, true, Double.POSITIVE_INFINITY, 0);

        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, topology);

        assertEquals(2, edgeGraph.getStates());
        assertEquals(2, edgeGraph.getInputGraph().getArcs());
        assertEquals(1, source.getCandidates());
        CCHMetricCandidate candidate = source.getCandidate(0);
        assertEquals(GHUtility.createEdgeKey(edge, false), candidate.getProvenance().getOutgoingEdgeKey());
        assertEquals(10, candidate.getWeight(), 1.e-9);
    }

    @Test
    void restrictedTurnsAreSkippedButFiniteUTurnsRemain() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHTopology topology = topology(edgeGraph);

        TestWeighting weighting = new TestWeighting()
                .edge(first, false, 10, 100)
                .edge(first, true, 11, 110)
                .edge(second, false, 20, 200)
                .edge(second, true, 21, 210)
                .turn(first, 1, second, Double.POSITIVE_INFINITY, 0)
                .turn(first, 1, first, 7, 70);

        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph, weighting, edgeGraph, topology);

        assertNull(optionalCandidateFor(source, edgeGraph, topology, state(edgeGraph, first, false), state(edgeGraph, second, false)));
        CCHMetricCandidate uTurn = candidateFor(source, edgeGraph, topology, state(edgeGraph, first, false), state(edgeGraph, first, true));
        assertEquals(18, uTurn.getWeight(), 1.e-9);
        assertEquals(180, uTurn.getMillis());
        assertEquals(7, uTurn.getProvenance().getTurnWeight(), 1.e-9);
        assertEquals(11, uTurn.getProvenance().getEdgeWeight(), 1.e-9);
    }

    @Test
    void v1CustomizerStillRejectsEdgeBasedSources() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int edge = graph.edge(0, 1).setDistance(10).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHTopology topology = topology(edgeGraph);
        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph,
                new TestWeighting().edge(edge, false, 10, 100).edge(edge, true, 10, 100),
                edgeGraph, topology);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CCHMetricCustomizer().customize(topology, source));

        assertTrue(error.getMessage().contains("node-based"));
    }

    private static CCHTopology topology(EdgeStateCCHInputGraph edgeGraph) {
        return new CCHTopologyBuilder().build(edgeGraph.getInputGraph(), CCHNodeOrder.identity(edgeGraph.getStates()));
    }

    private static int state(EdgeStateCCHInputGraph edgeGraph, int edge, boolean reverse) {
        return edgeGraph.getStateForEdgeKey(GHUtility.createEdgeKey(edge, reverse));
    }

    private static void assertState(EdgeStateCCHInputGraph edgeGraph, int state, int edgeKey, int tail, int head) {
        assertEquals(edgeKey, edgeGraph.getStateEdgeKey(state));
        assertEquals(state, edgeGraph.getStateForEdgeKey(edgeKey));
        assertEquals(tail, edgeGraph.getStateTailNode(state));
        assertEquals(head, edgeGraph.getStateHeadNode(state));
    }

    private static void assertTransition(EdgeStateCCHInputGraph edgeGraph, CCHInputGraph inputGraph, int inputArc,
                                         int fromState, int toState, int viaNode, int outgoingBaseEdge) {
        CCHInputArc arc = inputGraph.getArc(inputArc);
        assertEquals(fromState, arc.getFrom());
        assertEquals(toState, arc.getTo());
        assertEquals(outgoingBaseEdge, arc.getBaseEdge());
        assertEquals(edgeGraph.getStateEdgeKey(fromState), edgeGraph.getTransitionInEdgeKey(inputArc));
        assertEquals(viaNode, edgeGraph.getTransitionViaNode(inputArc));
        assertEquals(edgeGraph.getStateEdgeKey(toState), edgeGraph.getTransitionOutEdgeKey(inputArc));
    }

    private static CCHMetricCandidate candidateFor(EdgeBasedCCHMetricSource source, EdgeStateCCHInputGraph edgeGraph,
                                                   CCHTopology topology, int fromState, int toState) {
        CCHMetricCandidate candidate = optionalCandidateFor(source, edgeGraph, topology, fromState, toState);
        if (candidate == null)
            fail("missing candidate " + fromState + "->" + toState);
        return candidate;
    }

    private static CCHMetricCandidate optionalCandidateFor(EdgeBasedCCHMetricSource source, EdgeStateCCHInputGraph edgeGraph,
                                                           CCHTopology topology, int fromState, int toState) {
        int cchArc = topology.findArc(fromState, toState);
        assertNotEquals(CCHStorage.NO_ARC, cchArc);
        for (int i = 0; i < source.getCandidates(); i++) {
            CCHMetricCandidate candidate = source.getCandidate(i);
            if (candidate.getCCHArc() == cchArc
                    && edgeGraph.getTransitionInEdgeKey(inputArc(edgeGraph, fromState, toState)) == candidate.getProvenance().getIncomingEdgeKey()
                    && edgeGraph.getTransitionOutEdgeKey(inputArc(edgeGraph, fromState, toState)) == candidate.getProvenance().getOutgoingEdgeKey()) {
                return candidate;
            }
        }
        return null;
    }

    private static int inputArc(EdgeStateCCHInputGraph edgeGraph, int fromState, int toState) {
        CCHInputGraph inputGraph = edgeGraph.getInputGraph();
        for (int inputArc = 0; inputArc < inputGraph.getArcs(); inputArc++) {
            CCHInputArc arc = inputGraph.getArc(inputArc);
            if (arc.getFrom() == fromState && arc.getTo() == toState)
                return inputArc;
        }
        throw new AssertionError("missing input arc " + fromState + "->" + toState);
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
            return "edge_state_test";
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
