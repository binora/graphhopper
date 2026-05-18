// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHBoundaryOverlayTest {
    @Test
    void towerEndpointsProduceOnlyCoreMarkers() {
        Fixture fixture = fixture();
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, Collections.emptyList());

        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder()
                .build(0, 1, queryGraph, fixture.topology, fixture.weighting);

        assertEquals(0, overlay.getSourceNode());
        assertEquals(1, overlay.getTargetNode());
        assertTrue(overlay.isSourceCoreNode());
        assertTrue(overlay.isTargetCoreNode());
        assertTrue(overlay.getSourceOutgoingArcs().isEmpty());
        assertTrue(overlay.getSourceIncomingArcs().isEmpty());
        assertTrue(overlay.getTargetOutgoingArcs().isEmpty());
        assertTrue(overlay.getTargetIncomingArcs().isEmpty());
        assertTrue(overlay.getDirectArcs().isEmpty());
        assertTrue(overlay.getAllBoundaryArcs().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> overlay.getAllBoundaryArcs().add(null));
    }

    @Test
    void virtualSourceEmitsDirectedVirtualBoundaryArcs() {
        Fixture fixture = fixture();
        Snap sourceSnap = snap(0, 0.25, fixture.baseEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, sourceSnap);
        int source = sourceSnap.getClosestNode();

        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder()
                .build(source, 1, queryGraph, fixture.topology, fixture.weighting);

        assertFalse(overlay.isSourceCoreNode());
        assertTrue(overlay.isTargetCoreNode());
        assertEquals(2, overlay.getSourceOutgoingArcs().size());
        assertEquals(2, overlay.getSourceIncomingArcs().size());
        assertEquals(4, overlay.getAllBoundaryArcs().size());

        CCHBoundaryArc sourceToBase = findArc(overlay.getSourceOutgoingArcs(), source, 0);
        CCHBoundaryArc sourceToAdj = findArc(overlay.getSourceOutgoingArcs(), source, 1);
        CCHBoundaryArc baseToSource = findArc(overlay.getSourceIncomingArcs(), 0, source);
        CCHBoundaryArc adjToSource = findArc(overlay.getSourceIncomingArcs(), 1, source);
        assertBoundaryArcMatches(queryGraph, sourceToBase, fixture.weighting);
        assertBoundaryArcMatches(queryGraph, sourceToAdj, fixture.weighting);
        assertBoundaryArcMatches(queryGraph, baseToSource, fixture.weighting);
        assertBoundaryArcMatches(queryGraph, adjToSource, fixture.weighting);
        assertTrue(sourceToBase.isVirtualEdge());
        assertTrue(queryGraph.getEdgeIteratorStateForKey(sourceToBase.getEdgeKey()) instanceof VirtualEdgeIteratorState);
        assertEquals(((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorStateForKey(sourceToBase.getEdgeKey())).getOriginalEdgeKey(),
                sourceToBase.getOriginalEdgeKey());
        assertEquals(Collections.singletonList(sourceToAdj), overlay.getDirectSourceToTargetArcs());
        assertEquals(Collections.singletonList(adjToSource), overlay.getDirectTargetToSourceArcs());
    }

    @Test
    void virtualTargetEmitsDirectedVirtualBoundaryArcs() {
        Fixture fixture = fixture();
        Snap targetSnap = snap(0, 0.75, fixture.baseEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, targetSnap);
        int target = targetSnap.getClosestNode();

        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder()
                .build(0, target, queryGraph, fixture.topology, fixture.weighting);

        assertTrue(overlay.isSourceCoreNode());
        assertFalse(overlay.isTargetCoreNode());
        assertEquals(2, overlay.getTargetOutgoingArcs().size());
        assertEquals(2, overlay.getTargetIncomingArcs().size());
        assertEquals(4, overlay.getAllBoundaryArcs().size());

        CCHBoundaryArc sourceToTarget = findArc(overlay.getTargetIncomingArcs(), 0, target);
        CCHBoundaryArc targetToSource = findArc(overlay.getTargetOutgoingArcs(), target, 0);
        assertBoundaryArcMatches(queryGraph, sourceToTarget, fixture.weighting);
        assertBoundaryArcMatches(queryGraph, targetToSource, fixture.weighting);
        assertEquals(Collections.singletonList(sourceToTarget), overlay.getDirectSourceToTargetArcs());
        assertEquals(Collections.singletonList(targetToSource), overlay.getDirectTargetToSourceArcs());
    }

    @Test
    void bothVirtualEndpointsOnSameEdgeExposeDirectArcsBothWays() {
        Fixture fixture = fixture();
        Snap sourceSnap = snap(0, 0.25, fixture.baseEdge);
        Snap targetSnap = snap(0, 0.75, fixture.baseEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, Arrays.asList(sourceSnap, targetSnap));
        int source = sourceSnap.getClosestNode();
        int target = targetSnap.getClosestNode();

        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder()
                .build(source, target, queryGraph, fixture.topology, fixture.weighting);

        assertFalse(overlay.isSourceCoreNode());
        assertFalse(overlay.isTargetCoreNode());
        assertFalse(overlay.getDirectSourceToTargetArcs().isEmpty());
        assertFalse(overlay.getDirectTargetToSourceArcs().isEmpty());
        for (CCHBoundaryArc sourceToTarget : overlay.getDirectSourceToTargetArcs()) {
            assertEquals(source, sourceToTarget.getTailNode());
            assertEquals(target, sourceToTarget.getHeadNode());
            assertBoundaryArcMatches(queryGraph, sourceToTarget, fixture.weighting);
            assertTrue(sourceToTarget.isVirtualEdge());
        }
        for (CCHBoundaryArc targetToSource : overlay.getDirectTargetToSourceArcs()) {
            assertEquals(target, targetToSource.getTailNode());
            assertEquals(source, targetToSource.getHeadNode());
            assertBoundaryArcMatches(queryGraph, targetToSource, fixture.weighting);
            assertTrue(targetToSource.isVirtualEdge());
        }
    }

    @Test
    void directedAccessIsRepresentedExplicitly() {
        Fixture fixture = fixture();
        Snap sourceSnap = snap(0, 0.25, fixture.baseEdge);
        Snap targetSnap = snap(0, 0.75, fixture.baseEdge);
        QueryGraph forwardQueryGraph = QueryGraph.create(fixture.graph, Arrays.asList(sourceSnap, targetSnap));
        int source = sourceSnap.getClosestNode();
        int target = targetSnap.getClosestNode();

        CCHBoundaryOverlay forwardOnly = new CCHBoundaryOverlayBuilder()
                .build(source, target, forwardQueryGraph, fixture.topology, new OriginalDirectionWeighting(Access.FORWARD_ONLY));
        assertFalse(forwardOnly.getDirectSourceToTargetArcs().isEmpty());
        assertTrue(forwardOnly.getDirectTargetToSourceArcs().isEmpty());
        assertOnlyOriginalDirection(forwardOnly.getAllBoundaryArcs(), false);

        sourceSnap = snap(0, 0.25, fixture.baseEdge);
        targetSnap = snap(0, 0.75, fixture.baseEdge);
        QueryGraph reverseQueryGraph = QueryGraph.create(fixture.graph, Arrays.asList(sourceSnap, targetSnap));
        CCHBoundaryOverlay reverseOnly = new CCHBoundaryOverlayBuilder()
                .build(sourceSnap.getClosestNode(), targetSnap.getClosestNode(), reverseQueryGraph,
                        fixture.topology, new OriginalDirectionWeighting(Access.REVERSE_ONLY));
        assertTrue(reverseOnly.getDirectSourceToTargetArcs().isEmpty());
        assertFalse(reverseOnly.getDirectTargetToSourceArcs().isEmpty());
        assertOnlyOriginalDirection(reverseOnly.getAllBoundaryArcs(), true);

        sourceSnap = snap(0, 0.25, fixture.baseEdge);
        targetSnap = snap(0, 0.75, fixture.baseEdge);
        QueryGraph inaccessibleQueryGraph = QueryGraph.create(fixture.graph, Arrays.asList(sourceSnap, targetSnap));
        CCHBoundaryOverlay noAccess = new CCHBoundaryOverlayBuilder()
                .build(sourceSnap.getClosestNode(), targetSnap.getClosestNode(), inaccessibleQueryGraph,
                        fixture.topology, new OriginalDirectionWeighting(Access.NONE));
        assertTrue(noAccess.getAllBoundaryArcs().isEmpty());
    }

    @Test
    void rejectsUnsupportedTurnCostsAndInvalidMetrics() {
        Fixture fixture = fixture();
        Snap sourceSnap = snap(0, 0.25, fixture.baseEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, sourceSnap);

        IllegalArgumentException turnCosts = assertThrows(IllegalArgumentException.class,
                () -> new CCHBoundaryOverlayBuilder()
                        .build(sourceSnap.getClosestNode(), 1, queryGraph, fixture.topology, new TurnCostWeighting()));
        assertTrue(turnCosts.getMessage().contains("without turn costs"));

        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class,
                () -> new CCHBoundaryOverlayBuilder()
                        .build(sourceSnap.getClosestNode(), 1, queryGraph, fixture.topology, new InvalidWeighting(Double.NaN, 0)));
        assertTrue(nan.getMessage().contains("NaN"));

        IllegalArgumentException negativeMillis = assertThrows(IllegalArgumentException.class,
                () -> new CCHBoundaryOverlayBuilder()
                        .build(sourceSnap.getClosestNode(), 1, queryGraph, fixture.topology, new InvalidWeighting(1, -1)));
        assertTrue(negativeMillis.getMessage().contains("Negative millis"));
    }

    @Test
    void overlayConstructionDoesNotMutatePreparedTopologyOrMetric() {
        Fixture fixture = fixture();
        CCHMetric metric = new CCHMetricCustomizer().customize(fixture.topology,
                new BaseGraphCCHMetricSource(fixture.graph, fixture.weighting, fixture.topology));
        TopologySnapshot topologyBefore = TopologySnapshot.copyOf(fixture.topology);
        MetricSnapshot metricBefore = MetricSnapshot.copyOf(metric);
        Snap sourceSnap = snap(0, 0.25, fixture.baseEdge);
        QueryGraph queryGraph = QueryGraph.create(fixture.graph, sourceSnap);

        new CCHBoundaryOverlayBuilder().build(sourceSnap.getClosestNode(), 1, queryGraph, fixture.topology, fixture.weighting);

        topologyBefore.assertMatches(fixture.topology);
        metricBefore.assertMatches(metric);
    }

    private static Fixture fixture() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.getNodeAccess().setNode(0, 0, 0);
        graph.getNodeAccess().setNode(1, 0, 1);
        EdgeIteratorState baseEdge = graph.edge(0, 1).setDistance(100);
        CCHInputGraph supportGraph = BaseGraphCCHSupportBuilder.fromGraph(graph);
        CCHTopology topology = new CCHTopologyBuilder().build(supportGraph, CCHNodeOrder.identity(graph.getNodes()));
        return new Fixture(graph, baseEdge, topology, new DistanceWeighting());
    }

    private static Snap snap(double lat, double lon, EdgeIteratorState edge) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(new DistanceCalcEarth());
        return snap;
    }

    private static CCHBoundaryArc findArc(List<CCHBoundaryArc> arcs, int tail, int head) {
        for (CCHBoundaryArc arc : arcs) {
            if (arc.getTailNode() == tail && arc.getHeadNode() == head)
                return arc;
        }
        fail("missing boundary arc " + tail + "->" + head + " in " + arcs);
        throw new AssertionError();
    }

    private static void assertBoundaryArcMatches(QueryGraph queryGraph, CCHBoundaryArc arc, Weighting weighting) {
        EdgeIteratorState traversalEdge = queryGraph.getEdgeIteratorState(arc.getEdgeId(), arc.getHeadNode());
        assertEquals(arc.getTailNode(), traversalEdge.getBaseNode());
        assertEquals(arc.getHeadNode(), traversalEdge.getAdjNode());
        assertEquals(traversalEdge.getEdge(), arc.getEdgeId());
        assertEquals(traversalEdge.getEdgeKey(), arc.getEdgeKey());
        int originalEdgeKey = traversalEdge instanceof VirtualEdgeIteratorState
                ? ((VirtualEdgeIteratorState) traversalEdge).getOriginalEdgeKey()
                : traversalEdge.getEdgeKey();
        assertEquals(originalEdgeKey, arc.getOriginalEdgeKey());
        assertEquals(queryGraph.isVirtualEdge(traversalEdge.getEdge()), arc.isVirtualEdge());
        assertEquals(traversalEdge.getDistance(), arc.getDistance(), 1.e-9);
        assertEquals(weighting.calcEdgeWeight(traversalEdge, false), arc.getWeight(), 1.e-9);
        assertEquals(weighting.calcEdgeMillis(traversalEdge, false), arc.getMillis());
        assertTrue(traversalEdge.fetchWayGeometry(FetchMode.ALL).size() >= 2);
    }

    private static void assertOnlyOriginalDirection(List<CCHBoundaryArc> arcs, boolean reverse) {
        assertFalse(arcs.isEmpty());
        for (CCHBoundaryArc arc : arcs) {
            assertEquals(reverse, (arc.getOriginalEdgeKey() & 1) == 1, arc.toString());
        }
    }

    private static final class Fixture {
        private final BaseGraph graph;
        private final EdgeIteratorState baseEdge;
        private final CCHTopology topology;
        private final DistanceWeighting weighting;

        private Fixture(BaseGraph graph, EdgeIteratorState baseEdge, CCHTopology topology, DistanceWeighting weighting) {
            this.graph = graph;
            this.baseEdge = baseEdge;
            this.topology = topology;
            this.weighting = weighting;
        }
    }

    private enum Access {
        FORWARD_ONLY,
        REVERSE_ONLY,
        NONE
    }

    private static class DistanceWeighting implements Weighting {
        @Override
        public double calcMinWeightPerDistance() {
            return 1;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return edgeState.getDistance();
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return Math.round(edgeState.getDistance() * 10);
        }

        @Override
        public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
            return 0;
        }

        @Override
        public boolean hasTurnCosts() {
            return false;
        }

        @Override
        public String getName() {
            return "distance";
        }
    }

    private static final class OriginalDirectionWeighting extends DistanceWeighting {
        private final Access access;

        private OriginalDirectionWeighting(Access access) {
            this.access = access;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            int originalEdgeKey = reverse ? edgeState.getReverseEdgeKey() : edgeState.getEdgeKey();
            boolean reverseOriginalDirection = (originalEdgeKey & 1) == 1;
            if (access == Access.NONE)
                return Double.POSITIVE_INFINITY;
            if (access == Access.FORWARD_ONLY && reverseOriginalDirection)
                return Double.POSITIVE_INFINITY;
            if (access == Access.REVERSE_ONLY && !reverseOriginalDirection)
                return Double.POSITIVE_INFINITY;
            return edgeState.getDistance();
        }
    }

    private static final class TurnCostWeighting extends DistanceWeighting {
        @Override
        public boolean hasTurnCosts() {
            return true;
        }
    }

    private static final class InvalidWeighting extends DistanceWeighting {
        private final double weight;
        private final long millis;

        private InvalidWeighting(double weight, long millis) {
            this.weight = weight;
            this.millis = millis;
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
            return weight;
        }

        @Override
        public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
            return millis;
        }
    }

    private static int originalEdgeKey(EdgeIteratorState edgeState) {
        return edgeState instanceof VirtualEdgeIteratorState
                ? ((VirtualEdgeIteratorState) edgeState).getOriginalEdgeKey()
                : edgeState.getEdgeKey();
    }

    private static final class TopologySnapshot {
        private final int[] upFirstOut;
        private final int[] upTail;
        private final int[] upHead;
        private final int[] downFirstOut;
        private final int[] downTail;
        private final int[] downHead;
        private final int[] inputArcToCCHArc;
        private final boolean[] fillArc;
        private final int[] skippedArc1;
        private final int[] skippedArc2;

        private TopologySnapshot(CCHTopology topology) {
            upFirstOut = topology.getUpFirstOutArray();
            upTail = topology.getUpTailArray();
            upHead = topology.getUpHeadArray();
            downFirstOut = topology.getDownFirstOutArray();
            downTail = topology.getDownTailArray();
            downHead = topology.getDownHeadArray();
            inputArcToCCHArc = topology.getInputArcCCHArcArray();
            fillArc = topology.getFillArcArray();
            skippedArc1 = topology.getSkippedArc1Array();
            skippedArc2 = topology.getSkippedArc2Array();
        }

        private static TopologySnapshot copyOf(CCHTopology topology) {
            return new TopologySnapshot(topology);
        }

        private void assertMatches(CCHTopology topology) {
            assertArrayEquals(upFirstOut, topology.getUpFirstOutArray());
            assertArrayEquals(upTail, topology.getUpTailArray());
            assertArrayEquals(upHead, topology.getUpHeadArray());
            assertArrayEquals(downFirstOut, topology.getDownFirstOutArray());
            assertArrayEquals(downTail, topology.getDownTailArray());
            assertArrayEquals(downHead, topology.getDownHeadArray());
            assertArrayEquals(inputArcToCCHArc, topology.getInputArcCCHArcArray());
            assertArrayEquals(fillArc, topology.getFillArcArray());
            assertArrayEquals(skippedArc1, topology.getSkippedArc1Array());
            assertArrayEquals(skippedArc2, topology.getSkippedArc2Array());
        }
    }

    private static final class MetricSnapshot {
        private final double[] weights;
        private final long[] millis;
        private final double[] distances;

        private MetricSnapshot(CCHMetric metric) {
            weights = metric.getWeightArray();
            millis = metric.getMillisArray();
            distances = metric.getDistanceArray();
        }

        private static MetricSnapshot copyOf(CCHMetric metric) {
            return new MetricSnapshot(metric);
        }

        private void assertMatches(CCHMetric metric) {
            assertArrayEquals(weights, metric.getWeightArray());
            assertArrayEquals(millis, metric.getMillisArray());
            assertArrayEquals(distances, metric.getDistanceArray());
        }
    }
}
