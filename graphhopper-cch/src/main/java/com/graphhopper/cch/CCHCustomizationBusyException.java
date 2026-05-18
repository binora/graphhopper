// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

/**
 * Raised when a CCH metric recustomization request arrives while another one is still running.
 */
public final class CCHCustomizationBusyException extends IllegalStateException {
    public CCHCustomizationBusyException(String message) {
        super(message);
    }
}
