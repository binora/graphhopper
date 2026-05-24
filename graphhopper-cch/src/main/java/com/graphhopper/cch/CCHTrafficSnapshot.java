// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CCHTrafficSnapshot {
    public static final String EMPTY_ID = "empty";
    private static final CCHTrafficSnapshot EMPTY = new CCHTrafficSnapshot(EMPTY_ID, 0, Collections.emptyMap());

    private final String id;
    private final long createdMillis;
    private final Map<Long, CCHTrafficOverride> overrides;
    private final List<CCHTrafficOverride> entries;

    private CCHTrafficSnapshot(String id, long createdMillis, Map<Long, CCHTrafficOverride> overrides) {
        this.id = requireId(id);
        if (createdMillis < 0)
            throw new IllegalArgumentException("created_millis must be >= 0");
        this.createdMillis = createdMillis;
        LinkedHashMap<Long, CCHTrafficOverride> sorted = new LinkedHashMap<>();
        overrides.values().stream()
                .sorted(Comparator.comparingInt(CCHTrafficOverride::getEdge)
                        .thenComparing(CCHTrafficOverride::isReverse))
                .forEach(o -> sorted.put(o.key(), o));
        this.overrides = Collections.unmodifiableMap(sorted);
        this.entries = Collections.unmodifiableList(new ArrayList<>(sorted.values()));
    }

    public static CCHTrafficSnapshot empty() {
        return EMPTY;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public long getCreatedMillis() {
        return createdMillis;
    }

    public int size() {
        return overrides.size();
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public List<CCHTrafficOverride> getEntries() {
        return entries;
    }

    public CCHTrafficOverride getOverride(int edge, boolean reverse) {
        return overrides.get(CCHTrafficOverride.key(edge, reverse));
    }

    public boolean hasOverride(int edge, boolean reverse) {
        return getOverride(edge, reverse) != null;
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        String trimmed = id.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("traffic snapshot id must not be empty");
        if (!trimmed.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("traffic snapshot id may only contain letters, digits, '.', '_' and '-'");
        return trimmed;
    }

    public static final class Builder {
        private final String id;
        private long createdMillis = System.currentTimeMillis();
        private final Map<Long, CCHTrafficOverride> overrides = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = requireId(id);
        }

        public Builder setCreatedMillis(long createdMillis) {
            if (createdMillis < 0)
                throw new IllegalArgumentException("created_millis must be >= 0");
            this.createdMillis = createdMillis;
            return this;
        }

        public Builder override(int edge, boolean reverse, Double speedKmh, long delayMillis, boolean blocked) {
            CCHTrafficOverride override = new CCHTrafficOverride(edge, reverse, speedKmh, delayMillis, blocked);
            overrides.put(override.key(), override);
            return this;
        }

        public Builder speed(int edge, boolean reverse, double speedKmh) {
            return override(edge, reverse, speedKmh, 0, false);
        }

        public Builder delay(int edge, boolean reverse, long delayMillis) {
            return override(edge, reverse, null, delayMillis, false);
        }

        public Builder block(int edge, boolean reverse) {
            return override(edge, reverse, null, 0, true);
        }

        public CCHTrafficSnapshot build() {
            if (overrides.isEmpty())
                throw new IllegalArgumentException("traffic snapshot must contain at least one override");
            return new CCHTrafficSnapshot(id, createdMillis, overrides);
        }
    }
}
