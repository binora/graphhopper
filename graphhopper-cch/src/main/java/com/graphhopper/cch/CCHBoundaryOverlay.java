// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CCHBoundaryOverlay {
    private final int sourceNode;
    private final int targetNode;
    private final boolean sourceCoreNode;
    private final boolean targetCoreNode;
    private final List<CCHBoundaryArc> sourceOutgoingArcs;
    private final List<CCHBoundaryArc> sourceIncomingArcs;
    private final List<CCHBoundaryArc> targetOutgoingArcs;
    private final List<CCHBoundaryArc> targetIncomingArcs;
    private final List<CCHBoundaryArc> directSourceToTargetArcs;
    private final List<CCHBoundaryArc> directTargetToSourceArcs;
    private final List<CCHBoundaryArc> allBoundaryArcs;

    CCHBoundaryOverlay(int sourceNode, int targetNode, boolean sourceCoreNode, boolean targetCoreNode,
                       List<CCHBoundaryArc> sourceOutgoingArcs, List<CCHBoundaryArc> sourceIncomingArcs,
                       List<CCHBoundaryArc> targetOutgoingArcs, List<CCHBoundaryArc> targetIncomingArcs,
                       List<CCHBoundaryArc> directSourceToTargetArcs, List<CCHBoundaryArc> directTargetToSourceArcs,
                       List<CCHBoundaryArc> allBoundaryArcs) {
        if (sourceNode < 0)
            throw new IllegalArgumentException("sourceNode must be >= 0");
        if (targetNode < 0)
            throw new IllegalArgumentException("targetNode must be >= 0");
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.sourceCoreNode = sourceCoreNode;
        this.targetCoreNode = targetCoreNode;
        this.sourceOutgoingArcs = immutableCopy(sourceOutgoingArcs);
        this.sourceIncomingArcs = immutableCopy(sourceIncomingArcs);
        this.targetOutgoingArcs = immutableCopy(targetOutgoingArcs);
        this.targetIncomingArcs = immutableCopy(targetIncomingArcs);
        this.directSourceToTargetArcs = immutableCopy(directSourceToTargetArcs);
        this.directTargetToSourceArcs = immutableCopy(directTargetToSourceArcs);
        this.allBoundaryArcs = immutableCopy(allBoundaryArcs);
    }

    public int getSourceNode() {
        return sourceNode;
    }

    public int getTargetNode() {
        return targetNode;
    }

    public boolean isSourceCoreNode() {
        return sourceCoreNode;
    }

    public boolean isTargetCoreNode() {
        return targetCoreNode;
    }

    public List<CCHBoundaryArc> getSourceOutgoingArcs() {
        return sourceOutgoingArcs;
    }

    public List<CCHBoundaryArc> getSourceIncomingArcs() {
        return sourceIncomingArcs;
    }

    public List<CCHBoundaryArc> getTargetOutgoingArcs() {
        return targetOutgoingArcs;
    }

    public List<CCHBoundaryArc> getTargetIncomingArcs() {
        return targetIncomingArcs;
    }

    public List<CCHBoundaryArc> getDirectSourceToTargetArcs() {
        return directSourceToTargetArcs;
    }

    public List<CCHBoundaryArc> getDirectTargetToSourceArcs() {
        return directTargetToSourceArcs;
    }

    public List<CCHBoundaryArc> getDirectArcs() {
        ArrayList<CCHBoundaryArc> direct = new ArrayList<>(directSourceToTargetArcs.size() + directTargetToSourceArcs.size());
        direct.addAll(directSourceToTargetArcs);
        direct.addAll(directTargetToSourceArcs);
        return Collections.unmodifiableList(direct);
    }

    public List<CCHBoundaryArc> getAllBoundaryArcs() {
        return allBoundaryArcs;
    }

    private static List<CCHBoundaryArc> immutableCopy(List<CCHBoundaryArc> arcs) {
        Objects.requireNonNull(arcs, "arcs");
        return Collections.unmodifiableList(new ArrayList<>(arcs));
    }
}
