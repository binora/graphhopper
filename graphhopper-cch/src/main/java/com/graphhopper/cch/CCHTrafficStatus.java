// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CCHTrafficStatus {
    private final CCHTrafficSnapshotInfo activeSnapshot;
    private final List<CCHTrafficSnapshotInfo> snapshots;

    CCHTrafficStatus(CCHTrafficSnapshot activeSnapshot, List<CCHTrafficSnapshot> snapshots) {
        this.activeSnapshot = new CCHTrafficSnapshotInfo(activeSnapshot, true);
        List<CCHTrafficSnapshotInfo> infos = new ArrayList<>(snapshots.size());
        for (CCHTrafficSnapshot snapshot : snapshots) {
            infos.add(new CCHTrafficSnapshotInfo(snapshot, snapshot.getId().equals(activeSnapshot.getId())));
        }
        this.snapshots = Collections.unmodifiableList(infos);
    }

    public CCHTrafficSnapshotInfo getActiveSnapshot() {
        return activeSnapshot;
    }

    public List<CCHTrafficSnapshotInfo> getSnapshots() {
        return snapshots;
    }
}
