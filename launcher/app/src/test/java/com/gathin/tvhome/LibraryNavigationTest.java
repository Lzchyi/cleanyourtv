package com.gathin.tvhome;

import org.junit.Test;

import java.text.Collator;
import java.util.Locale;

import static org.junit.Assert.*;

public class LibraryNavigationTest {
    @Test public void landscapeColumnsFitSmallScreensAndUseSixOnA1080pTv() {
        assertEquals(2, LibraryNavigation.columns(200));
        assertEquals(3, LibraryNavigation.columns(420));
        assertEquals(5, LibraryNavigation.columns(720));
        assertEquals(6, LibraryNavigation.columns(840));
        assertEquals(6, LibraryNavigation.columns(1900));
    }

    @Test public void squareAppArtworkFallsBackToTheOriginalIcon() {
        assertTrue(LibraryNavigation.landscapeBanner(320, 180));
        assertFalse(LibraryNavigation.landscapeBanner(256, 256));
        assertFalse(LibraryNavigation.landscapeBanner(180, 320));
        assertTrue(LibraryNavigation.landscapeBanner(-1, -1));
    }

    @Test public void incompleteLastRowRetainsNearestValidColumnAndStopsAtBottom() {
        assertEquals(9, LibraryNavigation.verticalTarget(4, 12, 5, true));
        assertEquals(11, LibraryNavigation.verticalTarget(9, 12, 5, true));
        assertEquals(11, LibraryNavigation.verticalTarget(11, 12, 5, true));
        assertEquals(6, LibraryNavigation.verticalTarget(11, 12, 5, false));
        assertEquals(-1, LibraryNavigation.verticalTarget(4, 12, 5, false));
        assertEquals(-1, LibraryNavigation.verticalTarget(0, 0, 5, true));
    }

    @Test public void horizontalBoundariesDoNotWrapAndRespectReadingDirection() {
        assertTrue(LibraryNavigation.horizontalEdge(5, 12, 5, false, true));
        assertTrue(LibraryNavigation.horizontalEdge(9, 12, 5, false, false));
        assertTrue(LibraryNavigation.horizontalEdge(11, 12, 5, false, false));
        assertFalse(LibraryNavigation.horizontalEdge(6, 12, 5, false, true));
        assertTrue(LibraryNavigation.horizontalEdge(5, 12, 5, true, false));
        assertTrue(LibraryNavigation.horizontalEdge(11, 12, 5, true, true));
    }

    @Test public void labelsUseConfiguredLocaleAndPackagesBreakEquivalentLabelTies() {
        Collator swedish = Collator.getInstance(Locale.forLanguageTag("sv-SE"));
        assertTrue(LibraryNavigation.compare(swedish, "Zebra", "z.app", "Åland", "a.app") < 0);
        Collator english = Collator.getInstance(Locale.US);
        english.setStrength(Collator.PRIMARY);
        assertTrue(LibraryNavigation.compare(english, "Alpha", "z.app", "Bravo", "a.app") < 0);
        assertTrue(LibraryNavigation.compare(english, "Alpha", "a.app", "ALPHA", "z.app") < 0);
        assertEquals(0, LibraryNavigation.compare(english, "Alpha", "a.app", "ALPHA", "a.app"));
    }

    @Test public void appIdsStayStableWhenCatalogPositionsChange() {
        assertEquals(LibraryNavigation.stableId("com.example.app"), LibraryNavigation.stableId("com.example.app"));
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertNotEquals(LibraryNavigation.stableId("Aa"), LibraryNavigation.stableId("BB"));
    }
}
