package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppsButtonTest {
    @Test public void observedSonyAppsScanMatches() {
        assertTrue(AppsButton.sonyAppsScan(580, 0x054c, 0x0c99));
    }

    @Test public void otherSonyButtonsPassThrough() {
        for (int scan : new int[]{28, 102, 114, 115, 116, 139, 158}) {
            assertFalse(AppsButton.sonyAppsScan(scan, 0x054c, 0x0c99));
        }
    }

    @Test public void sameScanOnOtherDevicesPassesThrough() {
        assertFalse(AppsButton.sonyAppsScan(580, 0, 0));
        assertFalse(AppsButton.sonyAppsScan(580, 0x054c, 0x0268));
        assertFalse(AppsButton.sonyAppsScan(580, 0x18d1, 0x0c99));
    }
}
