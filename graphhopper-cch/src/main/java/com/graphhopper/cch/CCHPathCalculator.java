// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * GraphHopper routing adapter for CCH queries.
 */
public final class CCHPathCalculator implements PathCalculator {
    private final RoutingCCHGraph routingCCHGraph;
    private final QueryGraph queryGraph;
    private final NodeBasedCCHQuery query;
    private final CCHPathUnpacker pathUnpacker;
    private final String initDebug;
    private String debug = "";
    private int visitedNodes;

    public CCHPathCalculator(RoutingCCHGraph routingCCHGraph, QueryGraph queryGraph) {
        this.routingCCHGraph = Objects.requireNonNull(routingCCHGraph, "routingCCHGraph");
        this.queryGraph = Objects.requireNonNull(queryGraph, "queryGraph");
        StopWatch sw = new StopWatch().start();
        this.query = new NodeBasedCCHQuery(routingCCHGraph.getTopology(), routingCCHGraph.getMetric());
        this.pathUnpacker = new CCHPathUnpacker(routingCCHGraph.getTopology(), routingCCHGraph.getMetric());
        this.initDebug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " us";
        this.debug = initDebug;
    }

    public RoutingCCHGraph getRoutingCCHGraph() {
        return routingCCHGraph;
    }

    public QueryGraph getQueryGraph() {
        return queryGraph;
    }

    @Override
    public List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions) {
        Objects.requireNonNull(edgeRestrictions, "edgeRestrictions");
        checkQueryGraphEndpoint("from", from);
        checkQueryGraphEndpoint("to", to);
        checkRestrictions(edgeRestrictions);

        StopWatch sw = new StopWatch().start();
        debug = initDebug;
        visitedNodes = 0;
        Candidate best = calcBestCandidate(from, to);
        Path path = best == null ? new Path(queryGraph) : best.toPath(queryGraph, from, to);
        debug += ", cch-routing:" + sw.stop().getMillis() + " ms";
        return Collections.singletonList(path);
    }

    @Override
    public String getDebugString() {
        return debug;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private static void checkRestrictions(EdgeRestrictions edgeRestrictions) {
        if (!edgeRestrictions.getUnfavoredEdges().isEmpty())
            throw new IllegalArgumentException("Using unfavored edges is currently not supported for graphhopper-cch");
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE)
            throw new IllegalArgumentException("Source/target edge restrictions are currently not supported for graphhopper-cch");
    }

    private Candidate calcBestCandidate(int from, int to) {
        CCHBoundaryOverlay overlay = new CCHBoundaryOverlayBuilder()
                .build(from, to, queryGraph, routingCCHGraph.getTopology(), routingCCHGraph.getWeighting());
        Candidate best = from == to ? Candidate.empty() : null;
        for (CCHBoundaryArc directArc : overlay.getDirectSourceToTargetArcs()) {
            best = better(best, Candidate.fromSegments(Collections.singletonList(CCHPathSegment.fromBoundaryArc(directArc))));
        }

        for (Anchor source : sourceAnchors(overlay)) {
            for (Anchor target : targetAnchors(overlay)) {
                CCHQueryResult result = query.calc(source.coreNode, target.coreNode);
                visitedNodes += result.getVisitedNodes();
                if (!result.isFound())
                    continue;
                CCHUnpackedPath corePath = pathUnpacker.unpack(result);
                List<CCHPathSegment> coreSegments = coreSegments(corePath);
                Set<Integer> forbiddenBaseEdges = new HashSet<>(source.forbiddenBaseEdges);
                forbiddenBaseEdges.addAll(target.forbiddenBaseEdges);
                if (usesForbiddenBaseEdge(coreSegments, forbiddenBaseEdges))
                    continue;
                List<CCHPathSegment> segments = new ArrayList<>(source.segments.size() + coreSegments.size() + target.segments.size());
                segments.addAll(source.segments);
                segments.addAll(coreSegments);
                segments.addAll(target.segments);
                best = better(best, Candidate.fromSegments(segments));
            }
        }
        return best;
    }

    private List<Anchor> sourceAnchors(CCHBoundaryOverlay overlay) {
        if (overlay.isSourceCoreNode())
            return Collections.singletonList(Anchor.core(overlay.getSourceNode()));
        List<Anchor> anchors = new ArrayList<>();
        for (CCHBoundaryArc arc : overlay.getSourceOutgoingArcs()) {
            if (isCoreNode(arc.getHeadNode()))
                anchors.add(Anchor.boundary(arc.getHeadNode(), CCHPathSegment.fromBoundaryArc(arc)));
        }
        return anchors;
    }

    private List<Anchor> targetAnchors(CCHBoundaryOverlay overlay) {
        if (overlay.isTargetCoreNode())
            return Collections.singletonList(Anchor.core(overlay.getTargetNode()));
        List<Anchor> anchors = new ArrayList<>();
        for (CCHBoundaryArc arc : overlay.getTargetIncomingArcs()) {
            if (isCoreNode(arc.getTailNode()))
                anchors.add(Anchor.boundary(arc.getTailNode(), CCHPathSegment.fromBoundaryArc(arc)));
        }
        return anchors;
    }

    private boolean isCoreNode(int node) {
        return node >= 0 && node < routingCCHGraph.getNodes() && !queryGraph.isVirtualNode(node);
    }

    private static List<CCHPathSegment> coreSegments(CCHUnpackedPath corePath) {
        List<CCHPathSegment> segments = new ArrayList<>(corePath.getEdges().size());
        for (CCHUnpackedEdge edge : corePath.getEdges()) {
            segments.add(CCHPathSegment.fromUnpackedEdge(edge));
        }
        return segments;
    }

    private static boolean usesForbiddenBaseEdge(List<CCHPathSegment> segments, Set<Integer> forbiddenBaseEdges) {
        if (forbiddenBaseEdges.isEmpty())
            return false;
        for (CCHPathSegment segment : segments) {
            if (forbiddenBaseEdges.contains(segment.edgeId))
                return true;
        }
        return false;
    }

    private static Candidate better(Candidate current, Candidate candidate) {
        if (candidate == null)
            return current;
        if (current == null || candidate.compareTo(current) < 0)
            return candidate;
        return current;
    }

    private void checkQueryGraphEndpoint(String name, int node) {
        if (node < 0 || node >= queryGraph.getNodes())
            throw new IllegalArgumentException("graphhopper-cch endpoint " + name
                    + "=" + node + " is outside query graph nodes [0," + queryGraph.getNodes() + ")");
    }

    private static final class Anchor {
        private final int coreNode;
        private final List<CCHPathSegment> segments;
        private final Set<Integer> forbiddenBaseEdges;

        private Anchor(int coreNode, List<CCHPathSegment> segments, Set<Integer> forbiddenBaseEdges) {
            this.coreNode = coreNode;
            this.segments = segments;
            this.forbiddenBaseEdges = forbiddenBaseEdges;
        }

        private static Anchor core(int coreNode) {
            return new Anchor(coreNode, Collections.emptyList(), Collections.emptySet());
        }

        private static Anchor boundary(int coreNode, CCHPathSegment segment) {
            Set<Integer> forbiddenBaseEdges = new HashSet<>();
            if (segment.splitBaseEdge != NO_EDGE)
                forbiddenBaseEdges.add(segment.splitBaseEdge);
            return new Anchor(coreNode, Collections.singletonList(segment), forbiddenBaseEdges);
        }
    }

    private static final class Candidate implements Comparable<Candidate> {
        private final List<CCHPathSegment> segments;
        private final double weight;
        private final long millis;
        private final double distance;

        private Candidate(List<CCHPathSegment> segments, double weight, long millis, double distance) {
            this.segments = segments;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
        }

        private static Candidate empty() {
            return new Candidate(Collections.emptyList(), 0, 0, 0);
        }

        private static Candidate fromSegments(List<CCHPathSegment> segments) {
            double weight = 0;
            long millis = 0;
            double distance = 0;
            for (CCHPathSegment segment : segments) {
                weight += segment.weight;
                millis = saturatedAdd(millis, segment.millis);
                distance += segment.distance;
            }
            return new Candidate(Collections.unmodifiableList(new ArrayList<>(segments)), weight, millis, distance);
        }

        private Path toPath(Graph graph, int source, int target) {
            Path path = new Path(graph);
            path.setFound(true)
                    .setFromNode(source)
                    .setEndNode(target)
                    .setWeight(weight)
                    .setTime(millis)
                    .setDistance(distance);
            int node = source;
            for (CCHPathSegment segment : segments) {
                if (segment.from != node)
                    throw new IllegalStateException("path segment is not contiguous: expected from " + node + ", got " + segment.from);
                EdgeIteratorState edge = graph.getEdgeIteratorState(segment.edgeId, segment.from);
                if (edge == null)
                    throw new IllegalStateException("edge " + segment.edgeId + " is not adjacent to " + segment.from);
                if (edge.getBaseNode() != segment.to)
                    throw new IllegalStateException("edge " + segment.edgeId + " from " + segment.from
                            + " leads to " + edge.getBaseNode() + ", expected " + segment.to);
                path.addEdge(segment.edgeId);
                node = segment.to;
            }
            if (node != target)
                throw new IllegalStateException("path ended at " + node + ", expected " + target);
            return path;
        }

        @Override
        public int compareTo(Candidate other) {
            int byWeight = Double.compare(weight, other.weight);
            if (byWeight != 0)
                return byWeight;
            int byMillis = Long.compare(millis, other.millis);
            if (byMillis != 0)
                return byMillis;
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0)
                return byDistance;
            int bySegments = Integer.compare(segments.size(), other.segments.size());
            if (bySegments != 0)
                return bySegments;
            for (int i = 0; i < segments.size(); i++) {
                int bySegment = segments.get(i).compareTo(other.segments.get(i));
                if (bySegment != 0)
                    return bySegment;
            }
            return 0;
        }
    }

    private static final class CCHPathSegment implements Comparable<CCHPathSegment> {
        private final int edgeId;
        private final int from;
        private final int to;
        private final double weight;
        private final long millis;
        private final double distance;
        private final int splitBaseEdge;

        private CCHPathSegment(int edgeId, int from, int to, double weight, long millis, double distance, int splitBaseEdge) {
            this.edgeId = edgeId;
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.millis = millis;
            this.distance = distance;
            this.splitBaseEdge = splitBaseEdge;
        }

        private static CCHPathSegment fromBoundaryArc(CCHBoundaryArc arc) {
            return new CCHPathSegment(
                    arc.getEdgeId(),
                    arc.getTailNode(),
                    arc.getHeadNode(),
                    arc.getWeight(),
                    arc.getMillis(),
                    arc.getDistance(),
                    arc.isVirtualEdge() ? GHUtility.getEdgeFromEdgeKey(arc.getOriginalEdgeKey()) : NO_EDGE);
        }

        private static CCHPathSegment fromUnpackedEdge(CCHUnpackedEdge edge) {
            return new CCHPathSegment(
                    edge.getBaseEdge(),
                    edge.getFrom(),
                    edge.getTo(),
                    edge.getWeight(),
                    edge.getMillis(),
                    edge.getDistance(),
                    NO_EDGE);
        }

        @Override
        public int compareTo(CCHPathSegment other) {
            int byEdge = Integer.compare(edgeId, other.edgeId);
            if (byEdge != 0)
                return byEdge;
            int byFrom = Integer.compare(from, other.from);
            if (byFrom != 0)
                return byFrom;
            return Integer.compare(to, other.to);
        }
    }

    private static long saturatedAdd(long first, long second) {
        if (first == Long.MAX_VALUE || second == Long.MAX_VALUE)
            return Long.MAX_VALUE;
        if (Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }
}
