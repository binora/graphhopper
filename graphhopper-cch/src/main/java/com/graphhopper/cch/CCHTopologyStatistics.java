// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

public final class CCHTopologyStatistics {
    private final int nodes;
    private final int inputArcs;
    private final int supportEdges;
    private final int upArcs;
    private final int downArcs;
    private final int fillArcs;
    private final long lowerTriangles;
    private final long intermediateTriangles;
    private final long upperTriangles;
    private final int eliminationTreeRoots;
    private final int maxEliminationTreeDepth;
    private final long estimatedTopologyBytes;

    CCHTopologyStatistics(int nodes, int inputArcs, int supportEdges, int upArcs, int downArcs, int fillArcs,
                          long lowerTriangles, long intermediateTriangles, long upperTriangles,
                          int eliminationTreeRoots, int maxEliminationTreeDepth, long estimatedTopologyBytes) {
        this.nodes = nodes;
        this.inputArcs = inputArcs;
        this.supportEdges = supportEdges;
        this.upArcs = upArcs;
        this.downArcs = downArcs;
        this.fillArcs = fillArcs;
        this.lowerTriangles = lowerTriangles;
        this.intermediateTriangles = intermediateTriangles;
        this.upperTriangles = upperTriangles;
        this.eliminationTreeRoots = eliminationTreeRoots;
        this.maxEliminationTreeDepth = maxEliminationTreeDepth;
        this.estimatedTopologyBytes = estimatedTopologyBytes;
    }

    public int getNodes() {
        return nodes;
    }

    public int getInputArcs() {
        return inputArcs;
    }

    public int getSupportEdges() {
        return supportEdges;
    }

    public int getUpArcs() {
        return upArcs;
    }

    public int getDownArcs() {
        return downArcs;
    }

    public int getArcs() {
        return upArcs + downArcs;
    }

    public int getDirectArcs() {
        return getArcs() - fillArcs;
    }

    public int getFillArcs() {
        return fillArcs;
    }

    public long getLowerTriangles() {
        return lowerTriangles;
    }

    public long getIntermediateTriangles() {
        return intermediateTriangles;
    }

    public long getUpperTriangles() {
        return upperTriangles;
    }

    public long getTriangles() {
        return lowerTriangles + intermediateTriangles + upperTriangles;
    }

    public int getEliminationTreeRoots() {
        return eliminationTreeRoots;
    }

    public int getMaxEliminationTreeDepth() {
        return maxEliminationTreeDepth;
    }

    public long getEstimatedTopologyBytes() {
        return estimatedTopologyBytes;
    }

    @Override
    public String toString() {
        return "CCHTopologyStatistics{" +
                "nodes=" + nodes +
                ", inputArcs=" + inputArcs +
                ", supportEdges=" + supportEdges +
                ", arcs=" + getArcs() +
                ", fillArcs=" + fillArcs +
                ", lowerTriangles=" + lowerTriangles +
                ", intermediateTriangles=" + intermediateTriangles +
                ", upperTriangles=" + upperTriangles +
                ", eliminationTreeRoots=" + eliminationTreeRoots +
                ", maxEliminationTreeDepth=" + maxEliminationTreeDepth +
                ", estimatedTopologyBytes=" + estimatedTopologyBytes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHTopologyStatistics))
            return false;
        CCHTopologyStatistics that = (CCHTopologyStatistics) o;
        return nodes == that.nodes && inputArcs == that.inputArcs && supportEdges == that.supportEdges
                && upArcs == that.upArcs && downArcs == that.downArcs && fillArcs == that.fillArcs
                && lowerTriangles == that.lowerTriangles && intermediateTriangles == that.intermediateTriangles
                && upperTriangles == that.upperTriangles && eliminationTreeRoots == that.eliminationTreeRoots
                && maxEliminationTreeDepth == that.maxEliminationTreeDepth
                && estimatedTopologyBytes == that.estimatedTopologyBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, inputArcs, supportEdges, upArcs, downArcs, fillArcs, lowerTriangles,
                intermediateTriangles, upperTriangles, eliminationTreeRoots, maxEliminationTreeDepth,
                estimatedTopologyBytes);
    }
}
