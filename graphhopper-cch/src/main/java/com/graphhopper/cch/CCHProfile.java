// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.Objects;

import static com.graphhopper.config.Profile.validateProfileName;

/**
 * Entry in {@code profiles_cch}: references a routing profile that should get an in-memory CCH overlay.
 */
public final class CCHProfile {
    private String profile = "";

    private CCHProfile() {
        // default constructor needed for Jackson
    }

    public CCHProfile(CCHProfile profile) {
        this.profile = profile.profile;
    }

    public CCHProfile(String profile) {
        setProfile(profile);
    }

    public String getProfile() {
        return profile;
    }

    void setProfile(String profile) {
        validateProfileName(profile);
        this.profile = profile;
    }

    @Override
    public String toString() {
        return profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CCHProfile))
            return false;
        CCHProfile that = (CCHProfile) o;
        return Objects.equals(profile, that.profile);
    }

    @Override
    public int hashCode() {
        return profile.hashCode();
    }
}
