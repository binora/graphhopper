// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CCHUnpackedPath {
    private final boolean found;
    private final int source;
    private final int target;
    private final List<CCHUnpackedEdge> edges;
    private final double weight;
    private final long millis;
    private final double distance;

    public CCHUnpackedPath(boolean found, int source, int target, List<CCHUnpackedEdge> edges,
                           double weight, long millis, double distance) {
        Objects.requireNonNull(edges, "edges");
        if (source < 0)
            throw new IllegalArgumentException("source must be >= 0");
        if (target < 0)
            throw new IllegalArgumentException("target must be >= 0");
        if (!found && !edges.isEmpty())
            throw new IllegalArgumentException("unfound paths cannot contain edges");
        if (!Double.isFinite(weight) && found || Double.isFinite(weight) && weight < 0)
            throw new IllegalArgumentException("weight must be finite and >= 0 for found paths");
        if (millis < 0)
            throw new IllegalArgumentException("millis must be >= 0");
        if (!Double.isFinite(distance) && found || Double.isFinite(distance) && distance < 0)
            throw new IllegalArgumentException("distance must be finite and >= 0 for found paths");
        this.found = found;
        this.source = source;
        this.target = target;
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        this.weight = weight;
        this.millis = millis;
        this.distance = distance;
    }

    public boolean isFound() {
        return found;
    }

    public int getSource() {
        return source;
    }

    public int getTarget() {
        return target;
    }

    public List<CCHUnpackedEdge> getEdges() {
        return new ArrayList<>(edges);
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public double getWeight() {
        return weight;
    }

    public long getMillis() {
        return millis;
    }

    public double getDistance() {
        return distance;
    }
}
