// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.Router;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CCHGraphHopper extends GraphHopper {
    private final List<CCHProfile> cchProfiles = new ArrayList<>();
    private CCHNodeOrderProvider cchNodeOrderProvider = new DeterministicCCHNodeOrderBuilder();
    private volatile Map<String, RoutingCCHGraph> cchGraphs = Collections.emptyMap();
    private volatile Map<String, CCHTrafficSnapshot> cchTrafficSnapshots = Collections.emptyMap();
    private volatile CCHTrafficSnapshot activeTrafficSnapshot = CCHTrafficSnapshot.empty();
    private CCHDataAccessStore cchStore;
    private final AtomicBoolean cchCustomizationInProgress = new AtomicBoolean();

    @Override
    public CCHGraphHopper init(GraphHopperConfig ghConfig) {
        super.init(ghConfig);
        if (ghConfig instanceof CCHGraphHopperConfig)
            replaceCCHProfiles(((CCHGraphHopperConfig) ghConfig).getCCHProfiles());
        else
            replaceCCHProfiles(Collections.emptyList());
        return this;
    }

    public CCHGraphHopper setCCHProfiles(CCHProfile... cchProfiles) {
        return setCCHProfiles(Arrays.asList(cchProfiles));
    }

    public CCHGraphHopper setCCHProfiles(Collection<CCHProfile> cchProfiles) {
        if (!this.cchProfiles.isEmpty())
            throw new IllegalArgumentException("Cannot initialize CCH profiles multiple times");
        replaceCCHProfiles(cchProfiles);
        return this;
    }

    private void replaceCCHProfiles(Collection<CCHProfile> cchProfiles) {
        if (!cchGraphs.isEmpty())
            throw new IllegalArgumentException("Cannot set CCH profiles after CCH was prepared");
        LinkedHashSet<String> profileNames = new LinkedHashSet<>();
        List<CCHProfile> copies = new ArrayList<>();
        for (CCHProfile cchProfile : Objects.requireNonNull(cchProfiles, "cchProfiles")) {
            CCHProfile copy = new CCHProfile(cchProfile);
            if (!profileNames.add(copy.getProfile()))
                throw new IllegalArgumentException("Duplicate CCH reference to profile '" + copy.getProfile() + "'");
            copies.add(copy);
        }
        this.cchProfiles.clear();
        this.cchProfiles.addAll(copies);
    }

    public List<CCHProfile> getCCHProfiles() {
        List<CCHProfile> result = new ArrayList<>(cchProfiles.size());
        cchProfiles.forEach(p -> result.add(new CCHProfile(p)));
        return result;
    }

    public Map<String, RoutingCCHGraph> getCCHGraphs() {
        return cchGraphs;
    }

    public List<CCHCustomizationStatus> getCCHCustomizationStatus() {
        List<CCHCustomizationStatus> statuses = new ArrayList<>();
        boolean busy = cchCustomizationInProgress.get();
        for (CCHProfile cchProfile : cchProfiles) {
            String profileName = cchProfile.getProfile();
            Profile profile = getProfile(profileName);
            RoutingCCHGraph routingCCHGraph = cchGraphs.get(profileName);
            CCHTopology topology = routingCCHGraph == null ? null : routingCCHGraph.getTopology();
            int profileHash = profile == null ? 0 : getProfileHash(profile);
            int generation = getCCHMetricGeneration(profileName);
            statuses.add(new CCHCustomizationStatus(profileName, routingCCHGraph != null, busy,
                    getProperties() != null && generation > 0, profileHash, generation,
                    topology == null ? 0 : CCHDataAccessStore.topologyFingerprint(topology),
                    topology == null ? 0 : topology.getNodes(),
                    topology == null ? 0 : topology.getArcs()));
        }
        return statuses;
    }

    public CCHCustomizationResult recustomizeCCHProfile(String profileName) {
        Objects.requireNonNull(profileName, "profile");
        if (!cchCustomizationInProgress.compareAndSet(false, true))
            throw new CCHCustomizationBusyException("CCH metric recustomization is already running");
        long startNanos = System.nanoTime();
        try {
            return recustomizeCCHProfileInternal(profileName, startNanos);
        } finally {
            cchCustomizationInProgress.set(false);
        }
    }

    public CCHTrafficSnapshot getActiveCCHTrafficSnapshot() {
        return activeTrafficSnapshot;
    }

    public CCHTrafficStatus getCCHTrafficStatus() {
        return new CCHTrafficStatus(activeTrafficSnapshot, new ArrayList<>(cchTrafficSnapshots.values()));
    }

    public CCHTrafficSnapshotInfo putCCHTrafficSnapshot(CCHTrafficSnapshot snapshot) {
        validateTrafficSnapshot(snapshot);
        if (CCHTrafficSnapshot.EMPTY_ID.equals(snapshot.getId()))
            throw new IllegalArgumentException("'" + CCHTrafficSnapshot.EMPTY_ID + "' is reserved for the empty CCH traffic snapshot");
        Map<String, CCHTrafficSnapshot> updated = new LinkedHashMap<>(cchTrafficSnapshots);
        if (updated.containsKey(snapshot.getId()))
            throw new IllegalArgumentException("CCH traffic snapshot already exists: '" + snapshot.getId() + "'");
        updated.put(snapshot.getId(), snapshot);
        cchTrafficSnapshots = Collections.unmodifiableMap(updated);
        return new CCHTrafficSnapshotInfo(snapshot, snapshot.getId().equals(activeTrafficSnapshot.getId()));
    }

    public CCHTrafficStatus activateCCHTrafficSnapshot(String snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (cchCustomizationInProgress.get())
            throw new CCHCustomizationBusyException("CCH metric recustomization is already running");
        if (CCHTrafficSnapshot.EMPTY_ID.equals(snapshotId)) {
            activeTrafficSnapshot = CCHTrafficSnapshot.empty();
            return getCCHTrafficStatus();
        }
        CCHTrafficSnapshot snapshot = cchTrafficSnapshots.get(snapshotId);
        if (snapshot == null)
            throw new IllegalArgumentException("Unknown CCH traffic snapshot: '" + snapshotId + "'");
        activeTrafficSnapshot = snapshot;
        return getCCHTrafficStatus();
    }

    public CCHTrafficCustomizationResult activateCCHTrafficSnapshotAndRecustomize(String profileName, String snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (!cchCustomizationInProgress.compareAndSet(false, true))
            throw new CCHCustomizationBusyException("CCH metric recustomization is already running");
        CCHTrafficSnapshot snapshot = CCHTrafficSnapshot.EMPTY_ID.equals(snapshotId)
                ? CCHTrafficSnapshot.empty()
                : cchTrafficSnapshots.get(snapshotId);
        long startNanos = System.nanoTime();
        CCHTrafficSnapshot previous = activeTrafficSnapshot;
        boolean success = false;
        try {
            if (snapshot == null)
                throw new IllegalArgumentException("Unknown CCH traffic snapshot: '" + snapshotId + "'");
            activeTrafficSnapshot = snapshot;
            CCHCustomizationResult customization = recustomizeCCHProfileInternal(profileName, startNanos);
            success = true;
            return new CCHTrafficCustomizationResult(snapshot, customization);
        } finally {
            if (!success)
                activeTrafficSnapshot = previous;
            cchCustomizationInProgress.set(false);
        }
    }

    public CCHGraphHopper setCCHNodeOrderProvider(CCHNodeOrderProvider cchNodeOrderProvider) {
        if (!cchGraphs.isEmpty())
            throw new IllegalArgumentException("Cannot set CCH node order provider after CCH was prepared");
        this.cchNodeOrderProvider = Objects.requireNonNull(cchNodeOrderProvider, "cchNodeOrderProvider");
        return this;
    }

    public CCHNodeOrderProvider getCCHNodeOrderProvider() {
        return cchNodeOrderProvider;
    }

    @Override
    public void checkProfilesConsistency() {
        super.checkProfilesConsistency();
        checkCCHProfiles();
    }

    @Override
    protected void postProcessing(boolean closeEarly) {
        super.postProcessing(closeEarly);
        loadOrPrepareCCH();
    }

    protected void loadOrPrepareCCH() {
        checkCCHProfiles();
        if (cchProfiles.isEmpty()) {
            cchGraphs = Collections.emptyMap();
            return;
        }
        if (!getBaseGraph().isFrozen())
            getBaseGraph().freeze();

        if (getProperties() != null) {
            loadOrPreparePersistedCCH();
            return;
        }

        CCHTopology topology = prepareTopology();
        Map<String, RoutingCCHGraph> prepared = new LinkedHashMap<>();
        for (CCHProfile cchProfile : cchProfiles) {
            Profile profile = getProfile(cchProfile.getProfile());
            Weighting weighting = createWeighting(profile, new PMap());
            CCHMetric metric = new CCHMetricCustomizer().customize(topology,
                    new BaseGraphCCHMetricSource(getBaseGraph(), weighting, topology));
            prepared.put(cchProfile.getProfile(), new DefaultRoutingCCHGraph(getBaseGraph(), topology, metric, weighting));
        }
        cchGraphs = Collections.unmodifiableMap(prepared);
    }

    private void loadOrPreparePersistedCCH() {
        cchStore = new CCHDataAccessStore(getBaseGraph().getDirectory(), getBaseGraph().getSegmentSize());
        loadPersistedTrafficSnapshot();
        for (CCHProfile cchProfile : cchProfiles) {
            String profileName = cchProfile.getProfile();
            int profileHash = getProfileHash(getProfile(profileName));
            String storedVersion = getCCHProfileVersion(profileName);
            if (!storedVersion.isEmpty() && !storedVersion.equals(String.valueOf(profileHash)))
                throw new IllegalArgumentException("CCH preparation of " + profileName + " already exists in storage and doesn't match configuration");
        }

        CCHTopology topology = cchStore.loadTopology(getBaseGraph().getNodes());
        if (topology == null) {
            ensureWriteAccessForMissingCCH("topology");
            topology = prepareTopology();
            cchStore.saveTopology(topology);
        }

        Map<String, RoutingCCHGraph> prepared = new LinkedHashMap<>();
        for (CCHProfile cchProfile : cchProfiles) {
            String profileName = cchProfile.getProfile();
            Profile profile = getProfile(profileName);
            int profileHash = getProfileHash(profile);
            Weighting weighting = createWeighting(profile, new PMap());
            CCHTrafficSnapshot metricTrafficSnapshot = trafficSnapshotFrom(weighting);
            int metricGeneration = getCCHMetricGeneration(profileName);
            CCHMetric metric = metricGeneration > 0
                    ? cchStore.loadMetric(profileName, profileHash, topology, metricGeneration)
                    : cchStore.loadMetric(profileName, profileHash, topology);
            if (metric == null) {
                ensureWriteAccessForMissingCCH("metric for profile '" + profileName + "'");
                metric = new CCHMetricCustomizer().customize(topology,
                        new BaseGraphCCHMetricSource(getBaseGraph(), weighting, topology));
                int newGeneration = metricGeneration > 0 ? metricGeneration + 1 : 1;
                cchStore.saveMetric(profileName, profileHash, topology, metric, newGeneration);
                savePersistedTrafficSnapshot(metricTrafficSnapshot);
                setCCHProfileVersion(profileName, profileHash);
                setCCHMetricGeneration(profileName, newGeneration);
            }
            prepared.put(profileName, new DefaultRoutingCCHGraph(getBaseGraph(), topology, metric, weighting));
        }
        cchGraphs = Collections.unmodifiableMap(prepared);
    }

    private CCHTopology prepareTopology() {
        CCHInputGraph supportGraph = BaseGraphCCHSupportBuilder.fromGraph(getBaseGraph());
        CCHNodeOrder order = cchNodeOrderProvider.build(supportGraph);
        if (order == null)
            throw new IllegalArgumentException("CCH node order provider returned null");
        if (order.getNodes() != supportGraph.getNodes())
            throw new IllegalArgumentException("CCH node order provider returned " + order.getNodes()
                    + " nodes for support graph with " + supportGraph.getNodes() + " nodes");
        return new CCHTopologyBuilder().build(supportGraph, order);
    }

    private void ensureWriteAccessForMissingCCH(String missingPart) {
        if (!isAllowWrites())
            throw new IllegalStateException("CCH " + missingPart + " is missing and writes are not allowed");
        ensureWriteAccess();
    }

    private String getCCHProfileVersion(String profile) {
        StorableProperties properties = getProperties();
        return properties == null ? "" : properties.get("graph.profiles.cch." + profile + ".version");
    }

    private void setCCHProfileVersion(String profile, int version) {
        StorableProperties properties = getProperties();
        if (properties != null)
            properties.put("graph.profiles.cch." + profile + ".version", version);
    }

    private int getCCHMetricGeneration(String profile) {
        StorableProperties properties = getProperties();
        if (properties == null)
            return 0;
        String generation = properties.get("graph.profiles.cch." + profile + ".metric_generation");
        return generation.isEmpty() ? 0 : Integer.parseInt(generation);
    }

    private void setCCHMetricGeneration(String profile, int generation) {
        StorableProperties properties = getProperties();
        if (properties != null)
            properties.put("graph.profiles.cch." + profile + ".metric_generation", generation);
    }

    private CCHCustomizationResult recustomizeCCHProfileInternal(String profileName, long startNanos) {
        if (getBaseGraph().isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");
        if (!isAllowWrites())
            throw new IllegalStateException("CCH metric recustomization requires write access");
        if (!hasCCHProfile(profileName)) {
            if (getProfile(profileName) != null)
                throw new IllegalArgumentException("profile '" + profileName + "' is not configured in profiles_cch");
            throw new IllegalArgumentException("CCH profile references unknown profile '" + profileName + "'");
        }

        RoutingCCHGraph current = cchGraphs.get(profileName);
        if (current == null)
            throw new IllegalStateException("CCH profile '" + profileName + "' is not prepared");
        CCHTopology topology = current.getTopology();
        if (topology == null)
            throw new IllegalStateException("CCH topology is missing for profile '" + profileName + "'");

        Profile profile = getProfile(profileName);
        if (profile == null)
            throw new IllegalArgumentException("CCH profile references unknown profile '" + profileName + "'");
        if (profile.hasTurnCosts())
            throw new IllegalArgumentException("graphhopper-cch v1 only supports node-based profiles without turn costs: '" + profileName + "'");
        int profileHash = getProfileHash(profile);
        Weighting weighting = createWeighting(profile, new PMap());
        CCHTrafficSnapshot metricTrafficSnapshot = trafficSnapshotFrom(weighting);
        CCHMetric metric = new CCHMetricCustomizer().customize(topology,
                new BaseGraphCCHMetricSource(getBaseGraph(), weighting, topology));
        RoutingCCHGraph updated = new DefaultRoutingCCHGraph(getBaseGraph(), current.getCCHStorage(), topology, metric, weighting);

        boolean persisted = false;
        int generation = getCCHMetricGeneration(profileName);
        if (getProperties() != null) {
            if (cchStore == null)
                cchStore = new CCHDataAccessStore(getBaseGraph().getDirectory(), getBaseGraph().getSegmentSize());
            int newGeneration = generation + 1;
            if (newGeneration <= 0)
                throw new IllegalStateException("CCH metric generation overflow for profile '" + profileName + "'");
            cchStore.saveMetric(profileName, profileHash, topology, metric, newGeneration);
            savePersistedTrafficSnapshot(metricTrafficSnapshot);
            setCCHProfileVersion(profileName, profileHash);
            setCCHMetricGeneration(profileName, newGeneration);
            getProperties().flush();
            generation = newGeneration;
            persisted = true;
        }

        Map<String, RoutingCCHGraph> swapped = new LinkedHashMap<>(cchGraphs);
        swapped.put(profileName, updated);
        cchGraphs = Collections.unmodifiableMap(swapped);

        long elapsedMillis = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        return new CCHCustomizationResult(profileName, persisted, profileHash, generation,
                CCHDataAccessStore.topologyFingerprint(topology), topology.getNodes(), topology.getArcs(), elapsedMillis);
    }

    private boolean hasCCHProfile(String profileName) {
        for (CCHProfile cchProfile : cchProfiles) {
            if (cchProfile.getProfile().equals(profileName))
                return true;
        }
        return false;
    }

    @Override
    protected WeightingFactory createWeightingFactory() {
        WeightingFactory delegate = super.createWeightingFactory();
        return (profile, requestHints, disableTurnCosts) ->
                applyCCHTraffic(delegate.createWeighting(profile, requestHints, disableTurnCosts));
    }

    protected final Weighting applyCCHTraffic(Weighting weighting) {
        return applyCCHTraffic(weighting, activeTrafficSnapshot);
    }

    protected final Weighting applyCCHTraffic(Weighting weighting, CCHTrafficSnapshot snapshot) {
        Objects.requireNonNull(weighting, "weighting");
        CCHTrafficSnapshot effectiveSnapshot = snapshot == null ? CCHTrafficSnapshot.empty() : snapshot;
        return effectiveSnapshot.isEmpty() ? weighting : new CCHTrafficWeighting(weighting, effectiveSnapshot);
    }

    private static CCHTrafficSnapshot trafficSnapshotFrom(Weighting weighting) {
        if (weighting instanceof CCHTrafficWeighting)
            return ((CCHTrafficWeighting) weighting).getSnapshot();
        return CCHTrafficSnapshot.empty();
    }

    private void validateTrafficSnapshot(CCHTrafficSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (getBaseGraph() == null || getBaseGraph().isClosed())
            return;
        int edges = getBaseGraph().getEdges();
        for (CCHTrafficOverride override : snapshot.getEntries()) {
            if (override.getEdge() >= edges)
                throw new IllegalArgumentException("CCH traffic override references edge " + override.getEdge()
                        + ", but the base graph has only " + edges + " edges");
        }
    }

    private void loadPersistedTrafficSnapshot() {
        StorableProperties properties = getProperties();
        if (properties == null)
            return;
        String id = properties.get("graph.cch.traffic.active.id");
        if (id.isEmpty()) {
            activeTrafficSnapshot = CCHTrafficSnapshot.empty();
            return;
        }
        long createdMillis = Long.parseLong(properties.get("graph.cch.traffic.active.created_millis"));
        CCHTrafficSnapshot.Builder builder = CCHTrafficSnapshot.builder(id).setCreatedMillis(createdMillis);
        String entries = properties.get("graph.cch.traffic.active.entries");
        if (!entries.isEmpty()) {
            for (String entry : entries.split(";")) {
                if (entry.isEmpty())
                    continue;
                String[] parts = entry.split(",", -1);
                if (parts.length != 5)
                    throw new IllegalArgumentException("Invalid persisted CCH traffic entry: " + entry);
                int edge = Integer.parseInt(parts[0]);
                boolean reverse = Boolean.parseBoolean(parts[1]);
                Double speedKmh = parts[2].isEmpty() ? null : Double.parseDouble(parts[2]);
                long delayMillis = Long.parseLong(parts[3]);
                boolean blocked = Boolean.parseBoolean(parts[4]);
                builder.override(edge, reverse, speedKmh, delayMillis, blocked);
            }
        }
        CCHTrafficSnapshot snapshot = builder.build();
        validateTrafficSnapshot(snapshot);
        Map<String, CCHTrafficSnapshot> restored = new LinkedHashMap<>(cchTrafficSnapshots);
        restored.put(snapshot.getId(), snapshot);
        cchTrafficSnapshots = Collections.unmodifiableMap(restored);
        activeTrafficSnapshot = snapshot;
    }

    private void savePersistedTrafficSnapshot(CCHTrafficSnapshot snapshot) {
        StorableProperties properties = getProperties();
        if (properties == null)
            return;
        if (snapshot == null || snapshot.isEmpty()) {
            properties.remove("graph.cch.traffic.active.id");
            properties.remove("graph.cch.traffic.active.created_millis");
            properties.remove("graph.cch.traffic.active.entries");
            return;
        }
        StringBuilder entries = new StringBuilder();
        for (CCHTrafficOverride override : snapshot.getEntries()) {
            if (entries.length() > 0)
                entries.append(';');
            entries.append(override.getEdge()).append(',')
                    .append(override.isReverse()).append(',')
                    .append(override.getSpeedKmh() == null ? "" : override.getSpeedKmh()).append(',')
                    .append(override.getDelayMillis()).append(',')
                    .append(override.isBlocked());
        }
        properties.put("graph.cch.traffic.active.id", snapshot.getId());
        properties.put("graph.cch.traffic.active.created_millis", snapshot.getCreatedMillis());
        properties.put("graph.cch.traffic.active.entries", entries.toString());
    }

    @Override
    protected Router doCreateRouter(BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex,
                                    Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathBuilderFactory,
                                    TranslationMap trMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                                    Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        return new CCHRouter(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks, cchGraphs);
    }

    @Override
    public void close() {
        super.close();
        if (cchStore != null)
            cchStore.close();
    }

    private void checkCCHProfiles() {
        Set<String> seen = new LinkedHashSet<>();
        for (CCHProfile cchProfile : cchProfiles) {
            String profileName = cchProfile.getProfile();
            if (!seen.add(profileName))
                throw new IllegalArgumentException("Duplicate CCH reference to profile '" + profileName + "'");
            Profile profile = getProfile(profileName);
            if (profile == null)
                throw new IllegalArgumentException("CCH profile references unknown profile '" + profileName + "'");
            if (profile.hasTurnCosts())
                throw new IllegalArgumentException("graphhopper-cch v1 only supports node-based profiles without turn costs: '" + profileName + "'");
        }
    }
}
