// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.Path;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CCHPathUnpackerTest {
    @Test
    void sourceEqualsTargetUnpacksToZeroEdgePath() {
        Fixture fixture = fixture(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 4),
                        arc(1, 0, 1, true, 4)),
                CCHNodeOrder.identity(3),
                support(0, 1));

        CCHQueryResult result = fixture.query.calc(2, 2);
        CCHUnpackedPath unpackedPath = fixture.unpacker.unpack(result);
        Path path = fixture.unpacker.toPath(fixture.baseGraph, result);

        assertTrue(unpackedPath.isFound());
        assertEquals(0, unpackedPath.getEdgeCount());
        assertEquals(0, unpackedPath.getWeight(), 1.e-9);
        assertEquals(0, unpackedPath.getMillis());
        assertEquals(0, unpackedPath.getDistance(), 1.e-9);
        assertTrue(path.isFound());
        assertEquals(2, path.getFromNode());
        assertEquals(2, path.getEndNode());
        assertEquals(0, path.getEdgeCount());
    }

    @Test
    void noPathUnpacksToUnfoundEmptyPath() {
        Fixture fixture = fixture(4,
                Arrays.asList(
                        arc(0, 1, 1, false, 1),
                        arc(2, 3, 2, false, 1)),
                CCHNodeOrder.identity(4),
                support(0, 1), support(2, 3));

        CCHQueryResult result = fixture.query.calc(0, 3);
        CCHUnpackedPath unpackedPath = fixture.unpacker.unpack(result);
        Path path = fixture.unpacker.toPath(fixture.baseGraph, result);

        assertFalse(unpackedPath.isFound());
        assertEquals(0, unpackedPath.getEdgeCount());
        assertTrue(Double.isInfinite(unpackedPath.getWeight()));
        assertFalse(path.isFound());
        assertEquals(0, path.getEdgeCount());
    }

    @Test
    void directArcUnpacksToSingleBaseEdgeWithDirection() {
        Fixture fixture = fixture(2,
                Collections.singletonList(arc(0, 1, 5, false, 3)),
                CCHNodeOrder.identity(2),
                support(0, 1));

        CCHUnpackedPath path = fixture.unpacker.unpack(fixture.query.calc(0, 1));

        assertEquals(1, path.getEdgeCount());
        assertEdge(path.getEdges().get(0), 5, false, 0, 1, 3, 30, 300);
        assertEquals(3, path.getWeight(), 1.e-9);
        assertEquals(30, path.getMillis());
        assertEquals(300, path.getDistance(), 1.e-9);
    }

    @Test
    void forwardShortcutUnpacksToOriginalBaseEdges() {
        Fixture fixture = fixture(3,
                Arrays.asList(
                        arc(0, 1, 0, false, 1),
                        arc(1, 2, 1, false, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                support(0, 1), support(1, 2));

        CCHUnpackedPath path = fixture.unpacker.unpack(fixture.query.calc(0, 2));

        assertEquals(2, path.getEdgeCount());
        assertEdge(path.getEdges().get(0), 0, false, 0, 1, 1, 10, 100);
        assertEdge(path.getEdges().get(1), 1, false, 1, 2, 2, 20, 200);
        assertEquals(3, path.getWeight(), 1.e-9);
    }

    @Test
    void backwardShortcutUnpacksInTargetOrderDirection() {
        Fixture fixture = fixture(3,
                Arrays.asList(
                        arc(2, 1, 2, true, 2),
                        arc(1, 0, 1, true, 1)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                support(0, 1), support(1, 2));

        CCHUnpackedPath path = fixture.unpacker.unpack(fixture.query.calc(2, 0));

        assertEquals(2, path.getEdgeCount());
        assertEdge(path.getEdges().get(0), 2, true, 2, 1, 2, 20, 200);
        assertEdge(path.getEdges().get(1), 1, true, 1, 0, 1, 10, 100);
        assertEquals(3, path.getWeight(), 1.e-9);
    }

    @Test
    void buildsGraphHopperPathWithExpectedEdgesNodesAndTotals() {
        Fixture fixture = fixture(3,
                Arrays.asList(
                        arc(0, 1, 0, false, 1),
                        arc(1, 2, 1, false, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                support(0, 1), support(1, 2));

        Path path = fixture.unpacker.toPath(fixture.baseGraph, fixture.query.calc(0, 2));

        assertTrue(path.isFound());
        assertEquals(0, path.getFromNode());
        assertEquals(2, path.getEndNode());
        assertEquals(IntArrayList.from(0, 1), path.getEdges());
        assertEquals(IntArrayList.from(0, 1, 2), path.calcNodes());
        assertEquals(3, path.calcPoints().size());
        assertEquals(3, path.getWeight(), 1.e-9);
        assertEquals(30, path.getTime());
        assertEquals(300, path.getDistance(), 1.e-9);
    }

    @Test
    void reverseGraphHopperPathUsesSameEdgeIdsWithFromNodeDirection() {
        Fixture fixture = fixture(2,
                Collections.singletonList(arc(1, 0, 0, true, 4)),
                CCHNodeOrder.identity(2),
                support(0, 1));

        Path path = fixture.unpacker.toPath(fixture.baseGraph, fixture.query.calc(1, 0));

        assertTrue(path.isFound());
        assertEquals(1, path.getFromNode());
        assertEquals(0, path.getEndNode());
        assertEquals(IntArrayList.from(0), path.getEdges());
        assertEquals(IntArrayList.from(1, 0), path.calcNodes());
        assertEquals(4, path.getWeight(), 1.e-9);
    }

    @Test
    void equalAlternativesUseMetricProvenanceChoice() {
        Fixture fixture = fixture(3,
                Arrays.asList(
                        arc(0, 1, 1, false, 1),
                        arc(1, 2, 2, false, 1),
                        arc(0, 2, 3, false, 2)),
                CCHNodeOrder.fromOrder(new int[]{1, 0, 2}),
                support(0, 1), support(1, 2), support(0, 2));

        CCHUnpackedPath path = fixture.unpacker.unpack(fixture.query.calc(0, 2));

        assertEquals(1, path.getEdgeCount());
        assertEdge(path.getEdges().get(0), 3, false, 0, 2, 2, 20, 200);
    }

    @Test
    void edgeListIsDefensiveCopy() {
        Fixture fixture = fixture(2,
                Collections.singletonList(arc(0, 1, 1, false, 3)),
                CCHNodeOrder.identity(2),
                support(0, 1));
        CCHUnpackedPath path = fixture.unpacker.unpack(fixture.query.calc(0, 1));

        path.getEdges().clear();

        assertEquals(1, path.getEdgeCount());
        assertEquals(1, path.getEdges().size());
    }

    @Test
    void validatesInputs() {
        Fixture fixture = fixture(2,
                Collections.singletonList(arc(0, 1, 1, false, 3)),
                CCHNodeOrder.identity(2),
                support(0, 1));

        assertThrows(NullPointerException.class, () -> new CCHPathUnpacker(null, fixture.metric));
        assertThrows(NullPointerException.class, () -> new CCHPathUnpacker(fixture.topology, null));
        assertThrows(IllegalArgumentException.class, () -> new CCHPathUnpacker(fixture.topology, new CCHMetric(1)));
        assertThrows(NullPointerException.class, () -> fixture.unpacker.unpack(null));
        assertThrows(NullPointerException.class, () -> fixture.unpacker.toPath(null, fixture.query.calc(0, 1)));
    }

    @Test
    void randomizedFoundPathsAreContiguousAndPreserveQueryWeight() {
        for (int seed = 0; seed < 50; seed++) {
            Fixture fixture = randomFixture(seed);
            for (int source = 0; source < fixture.inputGraph.getNodes(); source++) {
                for (int target = 0; target < fixture.inputGraph.getNodes(); target++) {
                    CCHQueryResult result = fixture.query.calc(source, target);
                    CCHUnpackedPath path = fixture.unpacker.unpack(result);

                    assertEquals(result.isFound(), path.isFound(), "seed=" + seed + ", source=" + source + ", target=" + target);
                    if (!path.isFound())
                        continue;
                    assertEquals(result.getWeight(), path.getWeight(), 1.e-9, "seed=" + seed + ", source=" + source + ", target=" + target);
                    assertContiguous(source, target, path.getEdges());
                }
            }
        }
    }

    private static void assertContiguous(int source, int target, List<CCHUnpackedEdge> edges) {
        int node = source;
        for (CCHUnpackedEdge edge : edges) {
            assertEquals(node, edge.getFrom());
            node = edge.getTo();
        }
        assertEquals(target, node);
    }

    private static void assertEdge(CCHUnpackedEdge edge, int baseEdge, boolean reverse, int from, int to,
                                   double weight, long millis, double distance) {
        assertEquals(baseEdge, edge.getBaseEdge());
        assertEquals(reverse, edge.isReverse());
        assertEquals(from, edge.getFrom());
        assertEquals(to, edge.getTo());
        assertEquals(weight, edge.getWeight(), 1.e-9);
        assertEquals(millis, edge.getMillis());
        assertEquals(distance, edge.getDistance(), 1.e-9);
    }

    private static Fixture fixture(int nodes, List<CCHInputArc> arcs, CCHNodeOrder order, CCHInputEdge... supportEdges) {
        CCHInputGraph inputGraph = new CCHInputGraph(nodes, arcs, Arrays.asList(supportEdges));
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, order);
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
        BaseGraph baseGraph = baseGraph(nodes, arcs);
        return new Fixture(inputGraph, topology, metric, baseGraph);
    }

    private static Fixture randomFixture(int seed) {
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
                supportEdges.add(support(a, b));
                if (forward)
                    arcs.add(arc(a, b, baseEdge, false, 1 + random.nextInt(20)));
                if (backward)
                    arcs.add(arc(b, a, baseEdge, true, 1 + random.nextInt(20)));
                baseEdge++;
            }
        }
        return fixture(nodes, arcs, randomOrder(nodes, seed + 1000), supportEdges.toArray(new CCHInputEdge[0]));
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

    private static BaseGraph baseGraph(int nodes, List<CCHInputArc> arcs) {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        NodeAccess nodeAccess = graph.getNodeAccess();
        for (int node = 0; node < nodes; node++) {
            nodeAccess.setNode(node, node, node * 0.01);
        }
        Set<Integer> addedEdges = new HashSet<>();
        for (CCHInputArc arc : arcs) {
            if (addedEdges.add(arc.getBaseEdge())) {
                graph.edge(arc.getFrom(), arc.getTo()).setDistance(arc.getDistance());
            }
        }
        return graph;
    }

    private static CCHInputArc arc(int from, int to, int baseEdge, boolean reverse, double weight) {
        return new CCHInputArc(from, to, baseEdge, reverse, weight, Math.round(weight * 10), weight * 100);
    }

    private static CCHInputEdge support(int a, int b) {
        return new CCHInputEdge(a, b);
    }

    private static final class Fixture {
        private final CCHInputGraph inputGraph;
        private final CCHTopology topology;
        private final CCHMetric metric;
        private final BaseGraph baseGraph;
        private final NodeBasedCCHQuery query;
        private final CCHPathUnpacker unpacker;

        private Fixture(CCHInputGraph inputGraph, CCHTopology topology, CCHMetric metric, BaseGraph baseGraph) {
            this.inputGraph = inputGraph;
            this.topology = topology;
            this.metric = metric;
            this.baseGraph = baseGraph;
            query = new NodeBasedCCHQuery(topology, metric);
            unpacker = new CCHPathUnpacker(topology, metric);
        }
    }
}
