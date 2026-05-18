// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EdgeStateCCHTopologyTest {
    private static final int H_EDGE_INPUT_ARCS = 28;
    private static final int H_EDGE_FINGERPRINT_LOW = 32;

    @TempDir
    Path tempDir;

    @Test
    void buildsDeterministicEdgeStateTopologyAndKeepsRestrictedTurnSupport() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(1, 2).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        CCHNodeOrder order = CCHNodeOrder.identity(edgeGraph.getStates());

        EdgeStateCCHTopology firstTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, order);
        EdgeStateCCHTopology secondTopology = new EdgeStateCCHTopologyBuilder().build(edgeGraph, order);

        assertEdgeTopologyEquals(firstTopology, secondTopology);
        int restrictedTail = state(edgeGraph, first, false);
        int restrictedHead = state(edgeGraph, second, false);
        int restrictedCCHArc = firstTopology.getTopology().findArc(restrictedTail, restrictedHead);
        assertNotEquals(CCHStorage.NO_ARC, restrictedCCHArc);

        EdgeBasedCCHMetricSource source = new EdgeBasedCCHMetricSource(graph,
                new TestWeighting()
                        .edge(first, false, 10, 100)
                        .edge(second, false, 20, 200)
                        .turn(first, 1, second, Double.POSITIVE_INFINITY, 0),
                edgeGraph, firstTopology.getTopology());

        for (int i = 0; i < source.getCandidates(); i++) {
            assertNotEquals(restrictedCCHArc, source.getCandidate(i).getCCHArc());
        }
    }

    @Test
    void disconnectedComponentsDoNotGainCrossComponentTopologyArcs() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        int first = graph.edge(0, 1).setDistance(10).getEdge();
        int second = graph.edge(2, 3).setDistance(20).getEdge();
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopologyBuilder()
                .build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));

        int firstComponent = state(edgeGraph, first, false);
        int secondComponent = state(edgeGraph, second, false);
        assertEquals(CCHStorage.NO_ARC, edgeTopology.getTopology().findArc(firstComponent, secondComponent));
        assertEquals(CCHStorage.NO_ARC, edgeTopology.getTopology().findArc(secondComponent, firstComponent));

        int roots = 0;
        for (int parent : new CCHEliminationTree(edgeTopology.getTopology()).getParentArray()) {
            if (parent == CCHStorage.NO_ARC)
                roots++;
        }
        assertEquals(2, roots);
    }

    @Test
    void roundTripsEdgeTopologySeparatelyFromNodeTopology() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);
        graph.edge(1, 2).setDistance(20);
        EdgeStateCCHTopology edgeTopology = edgeTopology(graph);
        CCHTopology nodeTopology = new CCHTopologyBuilder().build(
                BaseGraphCCHSupportBuilder.fromGraph(graph), CCHNodeOrder.identity(graph.getNodes()));

        CCHDataAccessStore writer = store("round-trip");
        writer.saveTopology(nodeTopology);
        writer.saveEdgeTopology(edgeTopology);
        writer.close();

        CCHDataAccessStore reader = store("round-trip");
        CCHTopology loadedNodeTopology = reader.loadTopology(graph.getNodes());
        EdgeStateCCHTopology loadedEdgeTopology = reader.loadEdgeTopology(graph.getNodes(), graph.getEdges());

        assertTopologyEquals(nodeTopology, loadedNodeTopology);
        assertEdgeTopologyEquals(edgeTopology, loadedEdgeTopology);
        reader.close();
    }

    @Test
    void rejectsWrongEdgeTopologyCountsAndFingerprint() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);
        EdgeStateCCHTopology edgeTopology = edgeTopology(graph);
        CCHDataAccessStore writer = store("mismatch");
        writer.saveEdgeTopology(edgeTopology);
        writer.close();

        CCHDataAccessStore wrongNodesReader = store("mismatch");
        IllegalStateException nodeError = assertThrows(IllegalStateException.class,
                () -> wrongNodesReader.loadEdgeTopology(graph.getNodes() + 1, graph.getEdges()));
        assertTrue(nodeError.getMessage().contains("base node count"), nodeError.getMessage());
        wrongNodesReader.close();

        CCHDataAccessStore wrongEdgesReader = store("mismatch");
        IllegalStateException edgeError = assertThrows(IllegalStateException.class,
                () -> wrongEdgesReader.loadEdgeTopology(graph.getNodes(), graph.getEdges() + 1));
        assertTrue(edgeError.getMessage().contains("base edge count"), edgeError.getMessage());
        wrongEdgesReader.close();

        mutateHeader("mismatch", H_EDGE_FINGERPRINT_LOW, value -> value + 1);
        CCHDataAccessStore fingerprintReader = store("mismatch");
        IllegalStateException fingerprintError = assertThrows(IllegalStateException.class,
                () -> fingerprintReader.loadEdgeTopology(graph.getNodes(), graph.getEdges()));
        assertTrue(fingerprintError.getMessage().contains("fingerprint mismatch"), fingerprintError.getMessage());
        fingerprintReader.close();
    }

    @Test
    void rejectsWrongPersistedEdgeInputArcCount() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setDistance(10);
        EdgeStateCCHTopology edgeTopology = edgeTopology(graph);
        CCHDataAccessStore writer = store("wrong-arcs");
        writer.saveEdgeTopology(edgeTopology);
        writer.close();

        mutateHeader("wrong-arcs", H_EDGE_INPUT_ARCS, value -> value + 1);
        CCHDataAccessStore reader = store("wrong-arcs");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reader.loadEdgeTopology(graph.getNodes(), graph.getEdges()));
        assertTrue(error.getMessage().contains("edge-state CCH topology")
                || error.getMessage().contains("persisted"), error.getMessage());
        reader.close();
    }

    private CCHDataAccessStore store(String directory) {
        return new CCHDataAccessStore(new RAMDirectory(tempDir.resolve(directory).toString(), true).create(), -1);
    }

    private void mutateHeader(String directory, int offset, IntMutation mutation) {
        RAMDirectory dir = new RAMDirectory(tempDir.resolve(directory).toString(), true);
        dir.create();
        DataAccess access = dir.create("cch_edge_topology");
        assertTrue(access.loadExisting());
        access.setHeader(offset, mutation.apply(access.getHeader(offset)));
        access.flush();
        access.close();
        dir.close();
    }

    private static EdgeStateCCHTopology edgeTopology(BaseGraph graph) {
        EdgeStateCCHInputGraph edgeGraph = EdgeStateCCHInputBuilder.fromGraph(graph);
        return new EdgeStateCCHTopologyBuilder().build(edgeGraph, CCHNodeOrder.identity(edgeGraph.getStates()));
    }

    private static int state(EdgeStateCCHInputGraph edgeGraph, int edge, boolean reverse) {
        return edgeGraph.getStateForEdgeKey(GHUtility.createEdgeKey(edge, reverse));
    }

    private static void assertEdgeTopologyEquals(EdgeStateCCHTopology expected, EdgeStateCCHTopology actual) {
        assertEquals(expected.getBaseNodes(), actual.getBaseNodes());
        assertEquals(expected.getBaseEdges(), actual.getBaseEdges());
        assertEquals(expected.getStates(), actual.getStates());
        assertArrayEquals(expected.getEdgeStateInputGraph().getStateEdgeKeyArray(), actual.getEdgeStateInputGraph().getStateEdgeKeyArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getStateTailNodeArray(), actual.getEdgeStateInputGraph().getStateTailNodeArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getStateHeadNodeArray(), actual.getEdgeStateInputGraph().getStateHeadNodeArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getEdgeKeyToStateArray(), actual.getEdgeStateInputGraph().getEdgeKeyToStateArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getTransitionInEdgeKeyArray(), actual.getEdgeStateInputGraph().getTransitionInEdgeKeyArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getTransitionViaNodeArray(), actual.getEdgeStateInputGraph().getTransitionViaNodeArray());
        assertArrayEquals(expected.getEdgeStateInputGraph().getTransitionOutEdgeKeyArray(), actual.getEdgeStateInputGraph().getTransitionOutEdgeKeyArray());
        assertTopologyEquals(expected.getTopology(), actual.getTopology());
    }

    private static void assertTopologyEquals(CCHTopology expected, CCHTopology actual) {
        assertArrayEquals(expected.getNodeOrder().getOrderArray(), actual.getNodeOrder().getOrderArray());
        assertArrayEquals(expected.getNodeOrder().getRankArray(), actual.getNodeOrder().getRankArray());
        assertArrayEquals(expected.getUpFirstOutArray(), actual.getUpFirstOutArray());
        assertArrayEquals(expected.getUpTailArray(), actual.getUpTailArray());
        assertArrayEquals(expected.getUpHeadArray(), actual.getUpHeadArray());
        assertArrayEquals(expected.getDownFirstOutArray(), actual.getDownFirstOutArray());
        assertArrayEquals(expected.getDownTailArray(), actual.getDownTailArray());
        assertArrayEquals(expected.getDownHeadArray(), actual.getDownHeadArray());
        assertArrayEquals(expected.getInputArcCCHArcArray(), actual.getInputArcCCHArcArray());
        assertArrayEquals(expected.getFillArcArray(), actual.getFillArcArray());
        assertArrayEquals(expected.getSkippedArc1Array(), actual.getSkippedArc1Array());
        assertArrayEquals(expected.getSkippedArc2Array(), actual.getSkippedArc2Array());
        assertArrayEquals(new CCHEliminationTree(expected).getParentArray(), new CCHEliminationTree(actual).getParentArray());
    }

    private interface IntMutation {
        int apply(int value);
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
            int edgeKey = reverse ? edgeState.getReverseEdgeKey() : edgeState.getEdgeKey();
            return edgeValues.getOrDefault(edgeKey, EdgeValue.INACCESSIBLE).weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            int edgeKey = reverse ? edgeState.getReverseEdgeKey() : edgeState.getEdgeKey();
            return edgeValues.getOrDefault(edgeKey, EdgeValue.INACCESSIBLE).millis;
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
            return "edge_state_topology_test";
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
