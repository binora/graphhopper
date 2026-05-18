// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Persistence adapter for prepared CCH topology and metrics.
 * <p>
 * v1 stores one graph-level node-based topology and one metric per CCH profile. Future edge-based storage must use
 * separate names, headers, and fingerprints rather than changing the meaning of existing node-based data files.
 */
public final class CCHDataAccessStore {
    private static final String TOPOLOGY_NAME = "cch_topology";
    private static final String EDGE_TOPOLOGY_NAME = "cch_edge_topology";
    private static final String METRIC_PREFIX = "cch_metric_";
    private static final int TOPOLOGY_MAGIC = 0x43434854;
    private static final int EDGE_TOPOLOGY_MAGIC = 0x43434554;
    private static final int METRIC_MAGIC = 0x4343484d;
    private static final int VERSION = 1;

    private static final int H_MAGIC = 0;
    private static final int H_VERSION = 4;
    private static final int H_NODES = 8;
    private static final int H_UP_ARCS = 12;
    private static final int H_DOWN_ARCS = 16;
    private static final int H_INPUT_ARCS = 20;
    private static final int H_FINGERPRINT_LOW = 24;
    private static final int H_FINGERPRINT_HIGH = 28;
    private static final int H_DATA_BYTES_LOW = 32;
    private static final int H_DATA_BYTES_HIGH = 36;

    private static final int H_METRIC_ARCS = 8;
    private static final int H_METRIC_FINGERPRINT_LOW = 12;
    private static final int H_METRIC_FINGERPRINT_HIGH = 16;
    private static final int H_METRIC_PROFILE_HASH = 20;
    private static final int H_METRIC_DATA_BYTES_LOW = 24;
    private static final int H_METRIC_DATA_BYTES_HIGH = 28;

    private static final int H_EDGE_BASE_NODES = 8;
    private static final int H_EDGE_BASE_EDGES = 12;
    private static final int H_EDGE_STATES = 16;
    private static final int H_EDGE_UP_ARCS = 20;
    private static final int H_EDGE_DOWN_ARCS = 24;
    private static final int H_EDGE_INPUT_ARCS = 28;
    private static final int H_EDGE_FINGERPRINT_LOW = 32;
    private static final int H_EDGE_FINGERPRINT_HIGH = 36;
    private static final int H_EDGE_DATA_BYTES_LOW = 40;
    private static final int H_EDGE_DATA_BYTES_HIGH = 44;

    private final Directory directory;
    private final int segmentSize;
    private final Map<String, DataAccess> accesses = new HashMap<>();
    private final Map<String, Boolean> loaded = new HashMap<>();

    public CCHDataAccessStore(Directory directory, int segmentSize) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.segmentSize = segmentSize;
    }

    public CCHTopology loadTopology(int expectedNodes) {
        DataAccess access = access(TOPOLOGY_NAME);
        if (!loadExisting(TOPOLOGY_NAME, access))
            return null;
        checkHeader(access, TOPOLOGY_MAGIC, "CCH topology");
        int nodes = access.getHeader(H_NODES);
        if (nodes != expectedNodes)
            throw new IllegalStateException("CCH topology node count does not match graph: " + nodes + " != " + expectedNodes);
        int upArcs = access.getHeader(H_UP_ARCS);
        int downArcs = access.getHeader(H_DOWN_ARCS);
        int inputArcs = access.getHeader(H_INPUT_ARCS);
        long expectedFingerprint = readHeaderLong(access, H_FINGERPRINT_LOW, H_FINGERPRINT_HIGH);
        long dataBytes = readHeaderLong(access, H_DATA_BYTES_LOW, H_DATA_BYTES_HIGH);

        Cursor cursor = new Cursor();
        int[] order = readIntArray(access, cursor, nodes);
        int[] rank = readIntArray(access, cursor, nodes);
        int[] upFirstOut = readIntArray(access, cursor, nodes + 1);
        int[] upTail = readIntArray(access, cursor, upArcs);
        int[] upHead = readIntArray(access, cursor, upArcs);
        int[] downFirstOut = readIntArray(access, cursor, nodes + 1);
        int[] downTail = readIntArray(access, cursor, downArcs);
        int[] downHead = readIntArray(access, cursor, downArcs);
        int[] inputArcToCCHArc = readIntArray(access, cursor, inputArcs);
        boolean[] fillArc = readBooleanArray(access, cursor, upArcs + downArcs);
        int[] skippedArc1 = readIntArray(access, cursor, upArcs + downArcs);
        int[] skippedArc2 = readIntArray(access, cursor, upArcs + downArcs);
        int[] eliminationTreeParent = readIntArray(access, cursor, nodes);
        checkBodyLength("CCH topology", cursor.position, dataBytes);

        CCHTopology topology = new CCHTopology(CCHNodeOrder.fromOrderAndRank(order, rank),
                upFirstOut, upTail, upHead, downFirstOut, downTail, downHead,
                inputArcToCCHArc, fillArc, skippedArc1, skippedArc2);
        long actualFingerprint = topologyFingerprint(topology);
        if (actualFingerprint != expectedFingerprint)
            throw new IllegalStateException("CCH topology fingerprint mismatch: " + actualFingerprint + " != " + expectedFingerprint);
        int[] actualParents = new CCHEliminationTree(topology).getParentArray();
        if (!Arrays.equals(eliminationTreeParent, actualParents))
            throw new IllegalStateException("CCH topology elimination tree metadata does not match topology");
        return topology;
    }

    public void saveTopology(CCHTopology topology) {
        Objects.requireNonNull(topology, "topology");
        DataAccess access = access(TOPOLOGY_NAME);
        checkNotLoaded(TOPOLOGY_NAME);
        int nodes = topology.getNodes();
        int upArcs = topology.getUpArcs();
        int downArcs = topology.getDownArcs();
        int arcs = topology.getArcs();
        long dataBytes = intBytes(nodes) * 2
                + intBytes(nodes + 1) * 2
                + intBytes(upArcs) * 2
                + intBytes(downArcs) * 2
                + intBytes(topology.getInputArcCCHArcArray().length)
                + intBytes(arcs)
                + intBytes(arcs) * 2
                + intBytes(nodes);
        access.create(dataBytes);

        Cursor cursor = new Cursor();
        writeIntArray(access, cursor, topology.getNodeOrder().getOrderArray());
        writeIntArray(access, cursor, topology.getNodeOrder().getRankArray());
        writeIntArray(access, cursor, topology.getUpFirstOutArray());
        writeIntArray(access, cursor, topology.getUpTailArray());
        writeIntArray(access, cursor, topology.getUpHeadArray());
        writeIntArray(access, cursor, topology.getDownFirstOutArray());
        writeIntArray(access, cursor, topology.getDownTailArray());
        writeIntArray(access, cursor, topology.getDownHeadArray());
        writeIntArray(access, cursor, topology.getInputArcCCHArcArray());
        writeBooleanArray(access, cursor, topology.getFillArcArray());
        writeIntArray(access, cursor, topology.getSkippedArc1Array());
        writeIntArray(access, cursor, topology.getSkippedArc2Array());
        writeIntArray(access, cursor, new CCHEliminationTree(topology).getParentArray());
        checkBodyLength("CCH topology", cursor.position, dataBytes);

        access.setHeader(H_MAGIC, TOPOLOGY_MAGIC);
        access.setHeader(H_VERSION, VERSION);
        access.setHeader(H_NODES, nodes);
        access.setHeader(H_UP_ARCS, upArcs);
        access.setHeader(H_DOWN_ARCS, downArcs);
        access.setHeader(H_INPUT_ARCS, topology.getInputArcCCHArcArray().length);
        writeHeaderLong(access, H_FINGERPRINT_LOW, H_FINGERPRINT_HIGH, topologyFingerprint(topology));
        writeHeaderLong(access, H_DATA_BYTES_LOW, H_DATA_BYTES_HIGH, dataBytes);
        access.flush();
        loaded.put(TOPOLOGY_NAME, true);
    }

    public EdgeStateCCHTopology loadEdgeTopology(int expectedBaseNodes, int expectedBaseEdges) {
        DataAccess access = access(EDGE_TOPOLOGY_NAME);
        if (!loadExisting(EDGE_TOPOLOGY_NAME, access))
            return null;
        checkHeader(access, EDGE_TOPOLOGY_MAGIC, "edge-state CCH topology");
        int baseNodes = access.getHeader(H_EDGE_BASE_NODES);
        if (baseNodes != expectedBaseNodes)
            throw new IllegalStateException("edge-state CCH topology base node count does not match graph: " + baseNodes + " != " + expectedBaseNodes);
        int baseEdges = access.getHeader(H_EDGE_BASE_EDGES);
        if (baseEdges != expectedBaseEdges)
            throw new IllegalStateException("edge-state CCH topology base edge count does not match graph: " + baseEdges + " != " + expectedBaseEdges);
        int states = access.getHeader(H_EDGE_STATES);
        int upArcs = access.getHeader(H_EDGE_UP_ARCS);
        int downArcs = access.getHeader(H_EDGE_DOWN_ARCS);
        int inputArcs = access.getHeader(H_EDGE_INPUT_ARCS);
        long expectedFingerprint = readHeaderLong(access, H_EDGE_FINGERPRINT_LOW, H_EDGE_FINGERPRINT_HIGH);
        long dataBytes = readHeaderLong(access, H_EDGE_DATA_BYTES_LOW, H_EDGE_DATA_BYTES_HIGH);

        Cursor cursor = new Cursor();
        int[] stateEdgeKey = readIntArray(access, cursor, states);
        int[] stateTailNode = readIntArray(access, cursor, states);
        int[] stateHeadNode = readIntArray(access, cursor, states);
        int[] edgeKeyToState = readIntArray(access, cursor, baseEdges * 2);
        int[] transitionInEdgeKey = readIntArray(access, cursor, inputArcs);
        int[] transitionViaNode = readIntArray(access, cursor, inputArcs);
        int[] transitionOutEdgeKey = readIntArray(access, cursor, inputArcs);
        int[] order = readIntArray(access, cursor, states);
        int[] rank = readIntArray(access, cursor, states);
        int[] upFirstOut = readIntArray(access, cursor, states + 1);
        int[] upTail = readIntArray(access, cursor, upArcs);
        int[] upHead = readIntArray(access, cursor, upArcs);
        int[] downFirstOut = readIntArray(access, cursor, states + 1);
        int[] downTail = readIntArray(access, cursor, downArcs);
        int[] downHead = readIntArray(access, cursor, downArcs);
        int[] inputArcToCCHArc = readIntArray(access, cursor, inputArcs);
        boolean[] fillArc = readBooleanArray(access, cursor, upArcs + downArcs);
        int[] skippedArc1 = readIntArray(access, cursor, upArcs + downArcs);
        int[] skippedArc2 = readIntArray(access, cursor, upArcs + downArcs);
        int[] eliminationTreeParent = readIntArray(access, cursor, states);
        checkBodyLength("edge-state CCH topology", cursor.position, dataBytes);

        EdgeStateCCHInputGraph edgeStateInputGraph = new EdgeStateCCHInputGraph(baseNodes, baseEdges,
                edgeInputGraphFromTransitions(states, edgeKeyToState, transitionInEdgeKey, transitionOutEdgeKey),
                stateEdgeKey, stateTailNode, stateHeadNode, edgeKeyToState,
                transitionInEdgeKey, transitionViaNode, transitionOutEdgeKey);
        CCHTopology topology = new CCHTopology(CCHNodeOrder.fromOrderAndRank(order, rank),
                upFirstOut, upTail, upHead, downFirstOut, downTail, downHead,
                inputArcToCCHArc, fillArc, skippedArc1, skippedArc2);
        EdgeStateCCHTopology edgeTopology = new EdgeStateCCHTopology(edgeStateInputGraph, topology);
        long actualFingerprint = edgeTopologyFingerprint(edgeTopology);
        if (actualFingerprint != expectedFingerprint)
            throw new IllegalStateException("edge-state CCH topology fingerprint mismatch: " + actualFingerprint + " != " + expectedFingerprint);
        int[] actualParents = new CCHEliminationTree(topology).getParentArray();
        if (!Arrays.equals(eliminationTreeParent, actualParents))
            throw new IllegalStateException("edge-state CCH topology elimination tree metadata does not match topology");
        return edgeTopology;
    }

    public void saveEdgeTopology(EdgeStateCCHTopology edgeTopology) {
        Objects.requireNonNull(edgeTopology, "edgeTopology");
        DataAccess access = access(EDGE_TOPOLOGY_NAME);
        checkNotLoaded(EDGE_TOPOLOGY_NAME);
        EdgeStateCCHInputGraph edgeStateInputGraph = edgeTopology.getEdgeStateInputGraph();
        CCHTopology topology = edgeTopology.getTopology();
        int states = edgeStateInputGraph.getStates();
        int upArcs = topology.getUpArcs();
        int downArcs = topology.getDownArcs();
        int arcs = topology.getArcs();
        int inputArcs = edgeStateInputGraph.getInputGraph().getArcs();
        long dataBytes = intBytes(states) * 3
                + intBytes(edgeTopology.getBaseEdges() * 2)
                + intBytes(inputArcs) * 3
                + intBytes(states) * 2
                + intBytes(states + 1) * 2
                + intBytes(upArcs) * 2
                + intBytes(downArcs) * 2
                + intBytes(inputArcs)
                + intBytes(arcs)
                + intBytes(arcs) * 2
                + intBytes(states);
        access.create(dataBytes);

        Cursor cursor = new Cursor();
        writeIntArray(access, cursor, edgeStateInputGraph.getStateEdgeKeyArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getStateTailNodeArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getStateHeadNodeArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getEdgeKeyToStateArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getTransitionInEdgeKeyArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getTransitionViaNodeArray());
        writeIntArray(access, cursor, edgeStateInputGraph.getTransitionOutEdgeKeyArray());
        writeIntArray(access, cursor, topology.getNodeOrder().getOrderArray());
        writeIntArray(access, cursor, topology.getNodeOrder().getRankArray());
        writeIntArray(access, cursor, topology.getUpFirstOutArray());
        writeIntArray(access, cursor, topology.getUpTailArray());
        writeIntArray(access, cursor, topology.getUpHeadArray());
        writeIntArray(access, cursor, topology.getDownFirstOutArray());
        writeIntArray(access, cursor, topology.getDownTailArray());
        writeIntArray(access, cursor, topology.getDownHeadArray());
        writeIntArray(access, cursor, topology.getInputArcCCHArcArray());
        writeBooleanArray(access, cursor, topology.getFillArcArray());
        writeIntArray(access, cursor, topology.getSkippedArc1Array());
        writeIntArray(access, cursor, topology.getSkippedArc2Array());
        writeIntArray(access, cursor, new CCHEliminationTree(topology).getParentArray());
        checkBodyLength("edge-state CCH topology", cursor.position, dataBytes);

        access.setHeader(H_MAGIC, EDGE_TOPOLOGY_MAGIC);
        access.setHeader(H_VERSION, VERSION);
        access.setHeader(H_EDGE_BASE_NODES, edgeTopology.getBaseNodes());
        access.setHeader(H_EDGE_BASE_EDGES, edgeTopology.getBaseEdges());
        access.setHeader(H_EDGE_STATES, states);
        access.setHeader(H_EDGE_UP_ARCS, upArcs);
        access.setHeader(H_EDGE_DOWN_ARCS, downArcs);
        access.setHeader(H_EDGE_INPUT_ARCS, inputArcs);
        writeHeaderLong(access, H_EDGE_FINGERPRINT_LOW, H_EDGE_FINGERPRINT_HIGH, edgeTopologyFingerprint(edgeTopology));
        writeHeaderLong(access, H_EDGE_DATA_BYTES_LOW, H_EDGE_DATA_BYTES_HIGH, dataBytes);
        access.flush();
        loaded.put(EDGE_TOPOLOGY_NAME, true);
    }

    public CCHMetric loadMetric(String profile, int expectedProfileHash, CCHTopology topology) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(topology, "topology");
        return loadMetric(profile, expectedProfileHash, topology, metricName(profile));
    }

    public CCHMetric loadMetric(String profile, int expectedProfileHash, CCHTopology topology, int generation) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(topology, "topology");
        if (generation <= 0)
            throw new IllegalArgumentException("CCH metric generation must be positive: " + generation);
        return loadMetric(profile, expectedProfileHash, topology, metricName(profile, generation));
    }

    private CCHMetric loadMetric(String profile, int expectedProfileHash, CCHTopology topology, String name) {
        DataAccess access = access(name);
        if (!loadExisting(name, access))
            return null;
        checkHeader(access, METRIC_MAGIC, "CCH metric '" + profile + "'");
        int arcs = access.getHeader(H_METRIC_ARCS);
        if (arcs != topology.getArcs())
            throw new IllegalStateException("CCH metric '" + profile + "' arc count does not match topology: " + arcs + " != " + topology.getArcs());
        int profileHash = access.getHeader(H_METRIC_PROFILE_HASH);
        if (profileHash != expectedProfileHash)
            throw new IllegalStateException("CCH metric '" + profile + "' profile hash does not match configuration: " + profileHash + " != " + expectedProfileHash);
        long expectedFingerprint = readHeaderLong(access, H_METRIC_FINGERPRINT_LOW, H_METRIC_FINGERPRINT_HIGH);
        long actualFingerprint = topologyFingerprint(topology);
        if (actualFingerprint != expectedFingerprint)
            throw new IllegalStateException("CCH metric '" + profile + "' topology fingerprint mismatch: " + expectedFingerprint + " != " + actualFingerprint);
        long dataBytes = readHeaderLong(access, H_METRIC_DATA_BYTES_LOW, H_METRIC_DATA_BYTES_HIGH);

        Cursor cursor = new Cursor();
        double[] weight = readDoubleArray(access, cursor, arcs);
        long[] millis = readLongArray(access, cursor, arcs);
        double[] distance = readDoubleArray(access, cursor, arcs);
        byte[] provenanceType = readByteArray(access, cursor, arcs);
        int[] baseEdge = readIntArray(access, cursor, arcs);
        boolean[] reverse = readBooleanArray(access, cursor, arcs);
        int[] skippedArc1 = readIntArray(access, cursor, arcs);
        int[] skippedArc2 = readIntArray(access, cursor, arcs);
        long[] tieBreakKey = readLongArray(access, cursor, arcs);
        checkBodyLength("CCH metric '" + profile + "'", cursor.position, dataBytes);
        return CCHMetric.fromRaw(weight, millis, distance, provenanceType, baseEdge, reverse, skippedArc1, skippedArc2, tieBreakKey);
    }

    public void saveMetric(String profile, int profileHash, CCHTopology topology, CCHMetric metric) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(metric, "metric");
        saveMetric(profile, profileHash, topology, metric, metricName(profile));
    }

    public void saveMetric(String profile, int profileHash, CCHTopology topology, CCHMetric metric, int generation) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(metric, "metric");
        if (generation <= 0)
            throw new IllegalArgumentException("CCH metric generation must be positive: " + generation);
        saveMetric(profile, profileHash, topology, metric, metricName(profile, generation));
    }

    private void saveMetric(String profile, int profileHash, CCHTopology topology, CCHMetric metric, String name) {
        if (metric.getArcs() != topology.getArcs())
            throw new IllegalArgumentException("metric and topology arc counts differ: " + metric.getArcs() + " != " + topology.getArcs());
        directory.create();
        DataAccess access = access(name);
        checkNotLoaded(name);
        int arcs = metric.getArcs();
        long dataBytes = longBytes(arcs)
                + longBytes(arcs)
                + longBytes(arcs)
                + intBytes(arcs)
                + intBytes(arcs)
                + intBytes(arcs)
                + intBytes(arcs)
                + intBytes(arcs)
                + longBytes(arcs);
        access.create(dataBytes);

        Cursor cursor = new Cursor();
        writeDoubleArray(access, cursor, metric.getWeightArray());
        writeLongArray(access, cursor, metric.getMillisArray());
        writeDoubleArray(access, cursor, metric.getDistanceArray());
        writeByteArray(access, cursor, metric.getProvenanceTypeArray());
        writeIntArray(access, cursor, metric.getBaseEdgeArray());
        writeBooleanArray(access, cursor, metric.getReverseArray());
        writeIntArray(access, cursor, metric.getSkippedArc1Array());
        writeIntArray(access, cursor, metric.getSkippedArc2Array());
        writeLongArray(access, cursor, metric.getTieBreakKeyArray());
        checkBodyLength("CCH metric '" + profile + "'", cursor.position, dataBytes);

        access.setHeader(H_MAGIC, METRIC_MAGIC);
        access.setHeader(H_VERSION, VERSION);
        access.setHeader(H_METRIC_ARCS, arcs);
        writeHeaderLong(access, H_METRIC_FINGERPRINT_LOW, H_METRIC_FINGERPRINT_HIGH, topologyFingerprint(topology));
        access.setHeader(H_METRIC_PROFILE_HASH, profileHash);
        writeHeaderLong(access, H_METRIC_DATA_BYTES_LOW, H_METRIC_DATA_BYTES_HIGH, dataBytes);
        access.flush();
        loaded.put(name, true);
    }

    public void close() {
        for (DataAccess access : accesses.values()) {
            if (!access.isClosed())
                access.close();
        }
    }

    static long topologyFingerprint(CCHTopology topology) {
        Fingerprint fingerprint = new Fingerprint();
        fingerprint.add(topology.getNodes());
        fingerprint.add(topology.getUpArcs());
        fingerprint.add(topology.getDownArcs());
        fingerprint.add(topology.getNodeOrder().getOrderArray());
        fingerprint.add(topology.getNodeOrder().getRankArray());
        fingerprint.add(topology.getUpFirstOutArray());
        fingerprint.add(topology.getUpTailArray());
        fingerprint.add(topology.getUpHeadArray());
        fingerprint.add(topology.getDownFirstOutArray());
        fingerprint.add(topology.getDownTailArray());
        fingerprint.add(topology.getDownHeadArray());
        fingerprint.add(topology.getInputArcCCHArcArray());
        fingerprint.add(topology.getFillArcArray());
        fingerprint.add(topology.getSkippedArc1Array());
        fingerprint.add(topology.getSkippedArc2Array());
        return fingerprint.value();
    }

    static long edgeTopologyFingerprint(EdgeStateCCHTopology edgeTopology) {
        EdgeStateCCHInputGraph edgeStateInputGraph = edgeTopology.getEdgeStateInputGraph();
        Fingerprint fingerprint = new Fingerprint();
        fingerprint.add(EDGE_TOPOLOGY_MAGIC);
        fingerprint.add(edgeTopology.getBaseNodes());
        fingerprint.add(edgeTopology.getBaseEdges());
        fingerprint.add(edgeTopology.getStates());
        fingerprint.add(edgeStateInputGraph.getStateEdgeKeyArray());
        fingerprint.add(edgeStateInputGraph.getStateTailNodeArray());
        fingerprint.add(edgeStateInputGraph.getStateHeadNodeArray());
        fingerprint.add(edgeStateInputGraph.getEdgeKeyToStateArray());
        fingerprint.add(edgeStateInputGraph.getTransitionInEdgeKeyArray());
        fingerprint.add(edgeStateInputGraph.getTransitionViaNodeArray());
        fingerprint.add(edgeStateInputGraph.getTransitionOutEdgeKeyArray());
        long topologyFingerprint = topologyFingerprint(edgeTopology.getTopology());
        fingerprint.add((int) topologyFingerprint);
        fingerprint.add((int) (topologyFingerprint >>> 32));
        return fingerprint.value();
    }

    private DataAccess access(String name) {
        return accesses.computeIfAbsent(name, n -> {
            DataAccess existing = directory.getDAs().get(n);
            return existing != null ? existing : directory.create(n, segmentSize);
        });
    }

    private boolean loadExisting(String name, DataAccess access) {
        Boolean present = loaded.get(name);
        if (present == null) {
            present = access.loadExisting();
            loaded.put(name, present);
        }
        return present;
    }

    private void checkNotLoaded(String name) {
        if (Boolean.TRUE.equals(loaded.get(name)))
            throw new IllegalStateException(name + " already exists");
    }

    private static String metricName(String profile) {
        return METRIC_PREFIX + profile;
    }

    private static String metricName(String profile, int generation) {
        return METRIC_PREFIX + profile + "_" + generation;
    }

    private static CCHInputGraph edgeInputGraphFromTransitions(int states, int[] edgeKeyToState,
                                                               int[] transitionInEdgeKey,
                                                               int[] transitionOutEdgeKey) {
        List<CCHInputArc> arcs = new ArrayList<>(transitionInEdgeKey.length);
        TreeSet<CCHInputEdge> supportEdges = new TreeSet<>();
        for (int inputArc = 0; inputArc < transitionInEdgeKey.length; inputArc++) {
            int from = stateForEdgeKey(edgeKeyToState, transitionInEdgeKey[inputArc]);
            int to = stateForEdgeKey(edgeKeyToState, transitionOutEdgeKey[inputArc]);
            int outEdgeKey = transitionOutEdgeKey[inputArc];
            CCHInputArc arc = new CCHInputArc(from, to, GHUtility.getEdgeFromEdgeKey(outEdgeKey),
                    (outEdgeKey & 1) == 1, 0, 0, 0);
            arcs.add(arc);
            supportEdges.add(new CCHInputEdge(from, to));
        }
        return new CCHInputGraph(states, arcs, new ArrayList<>(supportEdges));
    }

    private static int stateForEdgeKey(int[] edgeKeyToState, int edgeKey) {
        if (edgeKey < 0 || edgeKey >= edgeKeyToState.length)
            throw new IllegalStateException("persisted edge key outside state mapping range: " + edgeKey);
        int state = edgeKeyToState[edgeKey];
        if (state == EdgeStateCCHInputGraph.NO_STATE)
            throw new IllegalStateException("persisted edge key does not map to an edge state: " + edgeKey);
        return state;
    }

    private static void checkHeader(DataAccess access, int expectedMagic, String description) {
        int magic = access.getHeader(H_MAGIC);
        if (magic != expectedMagic)
            throw new IllegalStateException(description + " has invalid magic: " + magic);
        int version = access.getHeader(H_VERSION);
        if (version != VERSION)
            throw new IllegalStateException(description + " has unsupported version: " + version + " != " + VERSION);
    }

    private static void checkBodyLength(String description, long actual, long expected) {
        if (actual != expected)
            throw new IllegalStateException(description + " encoded byte length mismatch: " + actual + " != " + expected);
    }

    private static void writeIntArray(DataAccess access, Cursor cursor, int[] values) {
        for (int value : values) {
            access.setInt(cursor.position, value);
            cursor.position += 4;
        }
    }

    private static int[] readIntArray(DataAccess access, Cursor cursor, int length) {
        int[] values = new int[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = access.getInt(cursor.position);
            cursor.position += 4;
        }
        return values;
    }

    private static void writeBooleanArray(DataAccess access, Cursor cursor, boolean[] values) {
        for (boolean value : values) {
            access.setInt(cursor.position, value ? 1 : 0);
            cursor.position += 4;
        }
    }

    private static boolean[] readBooleanArray(DataAccess access, Cursor cursor, int length) {
        boolean[] values = new boolean[length];
        for (int i = 0; i < values.length; i++) {
            int value = access.getInt(cursor.position);
            if (value != 0 && value != 1)
                throw new IllegalStateException("invalid persisted boolean value: " + value);
            values[i] = value == 1;
            cursor.position += 4;
        }
        return values;
    }

    private static void writeByteArray(DataAccess access, Cursor cursor, byte[] values) {
        for (byte value : values) {
            access.setInt(cursor.position, value);
            cursor.position += 4;
        }
    }

    private static byte[] readByteArray(DataAccess access, Cursor cursor, int length) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte) access.getInt(cursor.position);
            cursor.position += 4;
        }
        return values;
    }

    private static void writeDoubleArray(DataAccess access, Cursor cursor, double[] values) {
        for (double value : values) {
            writeLong(access, cursor, Double.doubleToLongBits(value));
        }
    }

    private static double[] readDoubleArray(DataAccess access, Cursor cursor, int length) {
        double[] values = new double[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Double.longBitsToDouble(readLong(access, cursor));
        }
        return values;
    }

    private static void writeLongArray(DataAccess access, Cursor cursor, long[] values) {
        for (long value : values) {
            writeLong(access, cursor, value);
        }
    }

    private static long[] readLongArray(DataAccess access, Cursor cursor, int length) {
        long[] values = new long[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = readLong(access, cursor);
        }
        return values;
    }

    private static void writeLong(DataAccess access, Cursor cursor, long value) {
        access.setInt(cursor.position, (int) value);
        access.setInt(cursor.position + 4, (int) (value >>> 32));
        cursor.position += 8;
    }

    private static long readLong(DataAccess access, Cursor cursor) {
        long low = access.getInt(cursor.position) & 0xffffffffL;
        long high = access.getInt(cursor.position + 4);
        cursor.position += 8;
        return (high << 32) | low;
    }

    private static void writeHeaderLong(DataAccess access, int lowHeader, int highHeader, long value) {
        access.setHeader(lowHeader, (int) value);
        access.setHeader(highHeader, (int) (value >>> 32));
    }

    private static long readHeaderLong(DataAccess access, int lowHeader, int highHeader) {
        long low = access.getHeader(lowHeader) & 0xffffffffL;
        long high = access.getHeader(highHeader);
        return (high << 32) | low;
    }

    private static long intBytes(int values) {
        return 4L * values;
    }

    private static long longBytes(int values) {
        return 8L * values;
    }

    private static final class Cursor {
        private long position;
    }

    private static final class Fingerprint {
        private static final long OFFSET = 0xcbf29ce484222325L;
        private static final long PRIME = 0x100000001b3L;
        private long value = OFFSET;

        private void add(int value) {
            this.value ^= value & 0xff;
            this.value *= PRIME;
            this.value ^= (value >>> 8) & 0xff;
            this.value *= PRIME;
            this.value ^= (value >>> 16) & 0xff;
            this.value *= PRIME;
            this.value ^= (value >>> 24) & 0xff;
            this.value *= PRIME;
        }

        private void add(int[] values) {
            add(values.length);
            for (int value : values) {
                add(value);
            }
        }

        private void add(boolean[] values) {
            add(values.length);
            for (boolean value : values) {
                add(value ? 1 : 0);
            }
        }

        private long value() {
            return value;
        }
    }
}
