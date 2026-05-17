// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCHDataAccessStoreTest {
    private static final String PROFILE = "profile";
    private static final int PROFILE_HASH = 12345;

    @TempDir
    Path tempDir;

    @Test
    void roundTripsTopologyAndMetric() {
        Fixture fixture = fixture();
        CCHDataAccessStore writer = store("round-trip");
        writer.saveTopology(fixture.topology);
        writer.saveMetric(PROFILE, PROFILE_HASH, fixture.topology, fixture.metric);
        writer.close();

        CCHDataAccessStore reader = store("round-trip");
        CCHTopology loadedTopology = reader.loadTopology(fixture.topology.getNodes());
        CCHMetric loadedMetric = reader.loadMetric(PROFILE, PROFILE_HASH, loadedTopology);

        assertTopologyEquals(fixture.topology, loadedTopology);
        assertMetricEquals(fixture.metric, loadedMetric);
        reader.close();
    }

    @Test
    void missingTopologyAndMetricReturnNull() {
        Fixture fixture = fixture();
        CCHDataAccessStore store = store("missing");

        assertNull(store.loadTopology(fixture.topology.getNodes()));
        assertNull(store.loadMetric(PROFILE, PROFILE_HASH, fixture.topology));
        store.close();
    }

    @Test
    void rejectsBadTopologyMagicAndVersion() {
        assertTopologyHeaderRejected("bad-magic", 1, 1, "invalid magic");
        assertTopologyHeaderRejected("bad-version", 0x43434854, 99, "unsupported version");
    }

    @Test
    void rejectsWrongTopologyNodeCount() {
        Fixture fixture = fixture();
        CCHDataAccessStore writer = store("wrong-nodes");
        writer.saveTopology(fixture.topology);
        writer.close();

        CCHDataAccessStore reader = store("wrong-nodes");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reader.loadTopology(fixture.topology.getNodes() + 1));
        assertTrue(error.getMessage().contains("node count"), error.getMessage());
        reader.close();
    }

    @Test
    void rejectsWrongMetricProfileHashAndTopologyFingerprint() {
        Fixture fixture = fixture();
        CCHDataAccessStore writer = store("metric-mismatch");
        writer.saveTopology(fixture.topology);
        writer.saveMetric(PROFILE, PROFILE_HASH, fixture.topology, fixture.metric);
        writer.close();

        CCHDataAccessStore wrongProfileReader = store("metric-mismatch");
        CCHTopology loadedTopology = wrongProfileReader.loadTopology(fixture.topology.getNodes());
        IllegalStateException profileError = assertThrows(IllegalStateException.class,
                () -> wrongProfileReader.loadMetric(PROFILE, PROFILE_HASH + 1, loadedTopology));
        assertTrue(profileError.getMessage().contains("profile hash"), profileError.getMessage());
        wrongProfileReader.close();

        CCHDataAccessStore wrongTopologyReader = store("metric-mismatch");
        CCHTopology sameShapeDifferentTopology = withToggledFillArc(fixture.topology);
        IllegalStateException topologyError = assertThrows(IllegalStateException.class,
                () -> wrongTopologyReader.loadMetric(PROFILE, PROFILE_HASH, sameShapeDifferentTopology));
        assertTrue(topologyError.getMessage().contains("topology fingerprint"), topologyError.getMessage());
        wrongTopologyReader.close();
    }

    private void assertTopologyHeaderRejected(String directory, int magic, int version, String messagePart) {
        RAMDirectory dir = new RAMDirectory(tempDir.resolve(directory).toString(), true);
        dir.create();
        DataAccess access = dir.create("cch_topology");
        access.create(4);
        access.setHeader(0, magic);
        access.setHeader(4, version);
        access.flush();
        access.close();
        dir.close();

        CCHDataAccessStore reader = store(directory);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> reader.loadTopology(0));
        assertTrue(error.getMessage().contains(messagePart), error.getMessage());
        reader.close();
    }

    private CCHDataAccessStore store(String directory) {
        return new CCHDataAccessStore(new RAMDirectory(tempDir.resolve(directory).toString(), true).create(), -1);
    }

    private static Fixture fixture() {
        CCHInputGraph inputGraph = inputGraph();
        CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, CCHNodeOrder.fromOrder(new int[]{1, 0, 2}));
        CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
        return new Fixture(topology, metric);
    }

    private static CCHInputGraph inputGraph() {
        return new CCHInputGraph(3,
                Arrays.asList(
                        new CCHInputArc(0, 1, 0, false, 1, 10, 1),
                        new CCHInputArc(1, 0, 0, true, 1, 10, 1),
                        new CCHInputArc(1, 2, 1, false, 2, 20, 2)),
                List.of(new CCHInputEdge(0, 1), new CCHInputEdge(1, 2)));
    }

    private static CCHTopology withToggledFillArc(CCHTopology topology) {
        boolean[] fillArc = topology.getFillArcArray();
        fillArc[0] = !fillArc[0];
        return new CCHTopology(topology.getNodeOrder(),
                topology.getUpFirstOutArray(), topology.getUpTailArray(), topology.getUpHeadArray(),
                topology.getDownFirstOutArray(), topology.getDownTailArray(), topology.getDownHeadArray(),
                topology.getInputArcCCHArcArray(), fillArc, topology.getSkippedArc1Array(), topology.getSkippedArc2Array());
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

    private static void assertMetricEquals(CCHMetric expected, CCHMetric actual) {
        assertArrayEquals(expected.getWeightArray(), actual.getWeightArray());
        assertArrayEquals(expected.getMillisArray(), actual.getMillisArray());
        assertArrayEquals(expected.getDistanceArray(), actual.getDistanceArray());
        assertArrayEquals(expected.getProvenanceTypeArray(), actual.getProvenanceTypeArray());
        assertArrayEquals(expected.getBaseEdgeArray(), actual.getBaseEdgeArray());
        assertArrayEquals(expected.getReverseArray(), actual.getReverseArray());
        assertArrayEquals(expected.getSkippedArc1Array(), actual.getSkippedArc1Array());
        assertArrayEquals(expected.getSkippedArc2Array(), actual.getSkippedArc2Array());
        assertArrayEquals(expected.getTieBreakKeyArray(), actual.getTieBreakKeyArray());
    }

    private static final class Fixture {
        private final CCHTopology topology;
        private final CCHMetric metric;

        private Fixture(CCHTopology topology, CCHMetric metric) {
            this.topology = topology;
            this.metric = metric;
        }
    }
}
