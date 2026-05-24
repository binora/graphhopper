// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class CCHTrafficSnapshotInfo {
    private final String id;
    private final long createdMillis;
    private final int overrides;
    private final boolean active;

    CCHTrafficSnapshotInfo(CCHTrafficSnapshot snapshot, boolean active) {
        this.id = snapshot.getId();
        this.createdMillis = snapshot.getCreatedMillis();
        this.overrides = snapshot.size();
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public long getCreatedMillis() {
        return createdMillis;
    }

    public int getOverrides() {
        return overrides;
    }

    public boolean isActive() {
        return active;
    }
}
