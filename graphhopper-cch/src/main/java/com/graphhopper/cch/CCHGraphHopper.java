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
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;

import java.util.*;

public class CCHGraphHopper extends GraphHopper {
    private final List<CCHProfile> cchProfiles = new ArrayList<>();
    private Map<String, RoutingCCHGraph> cchGraphs = Collections.emptyMap();

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

        Map<String, RoutingCCHGraph> prepared = new LinkedHashMap<>();
        for (CCHProfile cchProfile : cchProfiles) {
            Profile profile = getProfile(cchProfile.getProfile());
            Weighting weighting = createWeighting(profile, new PMap());
            CCHInputGraph inputGraph = BaseGraphCCHInputBuilder.fromGraph(getBaseGraph(), weighting);
            CCHNodeOrder order = new DeterministicCCHNodeOrderBuilder().build(inputGraph);
            CCHTopology topology = new CCHTopologyBuilder().build(inputGraph, order);
            CCHMetric metric = new CCHMetricCustomizer().customize(topology, inputGraph);
            prepared.put(cchProfile.getProfile(), new DefaultRoutingCCHGraph(getBaseGraph(), topology, metric, weighting));
        }
        cchGraphs = Collections.unmodifiableMap(prepared);
    }

    @Override
    protected Router doCreateRouter(BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex,
                                    Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathBuilderFactory,
                                    TranslationMap trMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                                    Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        return new CCHRouter(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks, cchGraphs);
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
