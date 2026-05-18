// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

public final class FileCCHNodeOrderProvider implements CCHNodeOrderProvider {
    public enum Numbering {
        ZERO_BASED,
        ONE_BASED
    }

    private final Path orderFile;
    private final Numbering numbering;

    public FileCCHNodeOrderProvider(Path orderFile) {
        this(orderFile, Numbering.ZERO_BASED);
    }

    public FileCCHNodeOrderProvider(Path orderFile, Numbering numbering) {
        this.orderFile = Objects.requireNonNull(orderFile, "orderFile");
        this.numbering = Objects.requireNonNull(numbering, "numbering");
    }

    public Path getOrderFile() {
        return orderFile;
    }

    public Numbering getNumbering() {
        return numbering;
    }

    @Override
    public CCHNodeOrder build(CCHInputGraph inputGraph) {
        Objects.requireNonNull(inputGraph, "inputGraph");
        try {
            switch (numbering) {
                case ZERO_BASED:
                    return CCHOrderIO.readZeroBasedOrder(orderFile, inputGraph.getNodes());
                case ONE_BASED:
                    return CCHOrderIO.readOneBasedOrder(orderFile, inputGraph.getNodes());
                default:
                    throw new IllegalArgumentException("Unsupported CCH order numbering: " + numbering);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read CCH order file '" + orderFile + "'", e);
        }
    }
}
