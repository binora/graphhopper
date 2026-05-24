// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

public final class CCHTrafficCustomizationResult {
    private final CCHTrafficSnapshotInfo activeSnapshot;
    private final CCHCustomizationResult customization;

    CCHTrafficCustomizationResult(CCHTrafficSnapshot activeSnapshot, CCHCustomizationResult customization) {
        this.activeSnapshot = new CCHTrafficSnapshotInfo(activeSnapshot, true);
        this.customization = customization;
    }

    public CCHTrafficSnapshotInfo getActiveSnapshot() {
        return activeSnapshot;
    }

    public CCHCustomizationResult getCustomization() {
        return customization;
    }
}
