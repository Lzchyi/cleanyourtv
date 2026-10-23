package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class LibraryAppsTest {
    @Test public void themeAndTvDefaultsStayInPlaceUntilTheOwnerChoosesAnApp() {
        assertTrue(LibraryApps.visible(true, false, false));
        assertFalse(LibraryApps.visible(true, true, false));
        assertFalse(LibraryApps.visible(false, false, false));
        assertTrue(LibraryApps.visible(false, false, true));
        assertTrue(LibraryApps.visible(true, true, true));
    }

    @Test public void undoingAnAdditionRestoresDefaultVisibilityAndKeepsOtherChoices() {
        Set<String> original = new HashSet<>();
        original.add("com.example.phone");
        original.add("com.example.hidden");
        Set<String> next = LibraryApps.change(original, "com.example.phone", false);
        assertFalse(next.contains("com.example.phone"));
        assertTrue(next.contains("com.example.hidden"));
        assertTrue(original.contains("com.example.phone"));
        assertFalse(LibraryApps.visible(false, false, next.contains("com.example.phone")));
    }

    @Test public void removingAnyHomeAppOverridesThemeDefaultsAndPreviousAdditions() {
        assertFalse(LibraryApps.visible(true, false, false, true));
        assertFalse(LibraryApps.visible(true, false, true, true));
        assertFalse(LibraryApps.visible(false, false, true, true));
        assertTrue(LibraryApps.visible(false, false, true, false));
        assertTrue(LibraryApps.visible(true, false, false, false));
    }

    @Test public void userHidingOverridesExplicitHomeAdditionsAndThemeDefaults() {
        assertFalse(LibraryApps.visible(false, false, true, false, true));
        assertFalse(LibraryApps.visible(true, true, true, false, true));
        assertFalse(LibraryApps.visible(true, false, false, false, true));
    }

    @Test public void unhidingRestoresExistingHomeIntentWithoutReaddingRemovedApps() {
        // The hidden flag changes independently of the saved addition/removal choices.
        assertTrue(LibraryApps.visible(false, false, true, false, false));
        assertTrue(LibraryApps.visible(true, true, true, false, false));
        assertTrue(LibraryApps.visible(true, false, false, false, false));
        assertFalse(LibraryApps.visible(true, false, true, true, false));
        assertFalse(LibraryApps.visible(false, false, false, false, false));
    }

    @Test public void choicesRoundTripInStableOrderIncludingAnEmptyList() {
        Set<String> apps = new HashSet<>();
        apps.add("org.z.example"); apps.add("com.a.example");
        assertEquals("com.a.example\norg.z.example\n", LibraryApps.encode(apps));
        assertEquals(apps, LibraryApps.decode(LibraryApps.encode(apps)));
        assertEquals(Collections.emptySet(), LibraryApps.decode(""));
    }

    @Test public void duplicateAndMalformedRecordsAreRejected() {
        for (String raw : new String[]{"com.example\ncom.example\n", "com..example\n",
                "com.example\n\n", "../file\n", "9bad.app\n", "com.example\ninvalid/name\n"}) {
            try { LibraryApps.decode(raw); fail(raw); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void aFullListAllowsRemovalAndAnExistingChoiceButNotAnotherApp() {
        Set<String> apps = new HashSet<>();
        for (int i = 0; i < LibraryApps.MAX_ADDED; i++) apps.add("com.example.app" + i);
        assertEquals(apps, LibraryApps.change(apps, "com.example.app0", true));
        assertEquals(LibraryApps.MAX_ADDED - 1, LibraryApps.change(apps, "com.example.app0", false).size());
        try { LibraryApps.change(apps, "com.example.extra", true); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(LibraryApps.MAX_ADDED, apps.size());
    }
}
