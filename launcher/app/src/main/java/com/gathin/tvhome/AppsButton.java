package com.gathin.tvhome;

/** The observed Apps scan of a Sony VRC 001 remote, not a general recents-key remapping. */
final class AppsButton {
    private AppsButton() { }

    static boolean sonyAppsScan(int scanCode, int vendorId, int productId) {
        return scanCode == 580 && vendorId == 0x054c && productId == 0x0c99;
    }
}
