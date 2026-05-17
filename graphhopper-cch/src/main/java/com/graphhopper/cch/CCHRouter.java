// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHRequest;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.Router;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;

import java.util.Map;

import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP;

public class CCHRouter extends Router {
    public static final String CUSTOMIZABLE_CH_DISABLE = "customizable_ch.disable";

    private final Map<String, RoutingCCHGraph> cchGraphs;

    public CCHRouter(BaseGraph graph, EncodingManager encodingManager, LocationIndex locationIndex,
                     Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathDetailsBuilderFactory,
                     TranslationMap translationMap, RouterConfig routerConfig, WeightingFactory weightingFactory,
                     Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks,
                     Map<String, RoutingCCHGraph> cchGraphs) {
        super(graph, encodingManager, locationIndex, profilesByName, pathDetailsBuilderFactory, translationMap,
                routerConfig, weightingFactory, chGraphs, landmarks);
        this.cchGraphs = cchGraphs;
    }

    @Override
    protected Solver createSolver(GHRequest request) {
        if (!request.getHints().getBool(CUSTOMIZABLE_CH_DISABLE, false) && cchGraphs.containsKey(request.getProfile()))
            return new CCHSolver(request, profilesByName, routerConfig, encodingManager, cchGraphs);
        return super.createSolver(request);
    }

    private static final class CCHSolver extends Solver {
        private final Map<String, RoutingCCHGraph> cchGraphs;

        private CCHSolver(GHRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig,
                          EncodingManager lookup, Map<String, RoutingCCHGraph> cchGraphs) {
            super(request, profilesByName, routerConfig, lookup);
            this.cchGraphs = cchGraphs;
        }

        @Override
        protected void checkRequest() {
            super.checkRequest();
            if (!request.getHeadings().isEmpty())
                throw unsupported("heading", "customizable_ch.disable=true");
            if (request.getHints().getBool(Parameters.Routing.PASS_THROUGH, false))
                throw unsupported(Parameters.Routing.PASS_THROUGH, "customizable_ch.disable=true");
            if (request.getCustomModel() != null)
                throw unsupported("custom_model", "customizable_ch.disable=true");
            if (!request.getCurbsides().isEmpty())
                throw unsupported(Parameters.Routing.CURBSIDE, "customizable_ch.disable=true");
            if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm()))
                throw new IllegalArgumentException("algorithm=round_trip cannot be used with graphhopper-cch; use customizable_ch.disable=true");
            if (ALT_ROUTE.equalsIgnoreCase(request.getAlgorithm()))
                throw new IllegalArgumentException("algorithm=alternative_route cannot be used with graphhopper-cch; use customizable_ch.disable=true");
        }

        @Override
        protected Weighting createWeighting() {
            return getRoutingCCHGraph(profile.getName()).getWeighting();
        }

        @Override
        protected PathCalculator createPathCalculator(QueryGraph queryGraph) {
            return new CCHPathCalculator(getRoutingCCHGraph(profile.getName()), queryGraph);
        }

        private RoutingCCHGraph getRoutingCCHGraph(String profileName) {
            RoutingCCHGraph routingCCHGraph = cchGraphs.get(profileName);
            if (routingCCHGraph == null)
                throw new IllegalArgumentException("Cannot find CCH preparation for the requested profile: '" + profileName + "'" +
                        "\nYou can try disabling CCH using " + CUSTOMIZABLE_CH_DISABLE + "=true" +
                        "\navailable CCH profiles: " + cchGraphs.keySet());
            return routingCCHGraph;
        }

        private static IllegalArgumentException unsupported(String parameter, String fallback) {
            return new IllegalArgumentException("The '" + parameter + "' parameter is currently not supported for graphhopper-cch; use " + fallback);
        }
    }
}
