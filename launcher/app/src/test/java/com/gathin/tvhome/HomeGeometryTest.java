package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeGeometryTest {
    @Test public void fourPlaceDockAndRegularRowsFillTheSameWidth() {
        for (int columns = 4; columns <= 7; columns++) {
            HomeGeometry home = new HomeGeometry(columns, true);
            assertEquals(home.spanCount(), 4 * home.spanSize(0));
            assertEquals(home.spanCount(), columns * home.spanSize(4));
            assertTrue(home.isDock(3));
            assertFalse(home.isDock(4));
            assertEquals(4, home.rowStart(4 + columns - 1));
            assertEquals(4 + columns, home.rowStart(4 + columns));
        }
    }

    @Test public void downFromEveryTileOfFinalActualRowStopsAtTheBoundary() {
        for (int columns = 4; columns <= 7; columns++) {
            HomeGeometry home = new HomeGeometry(columns, true);
            for (int count = 1; count <= 35; count++) {
                int start = home.rowStart(count - 1);
                for (int position = start; position < count; position++) {
                    assertTrue(home.isLastRow(position, count));
                    assertEquals(-1, home.vertical(position, count, true));
                }
                if (start > 0) assertFalse(home.isLastRow(start - 1, count));
            }
        }
    }

    @Test public void verticalNavigationMapsCellCentresAtIndependentDockBoundary() {
        HomeGeometry home = new HomeGeometry(7, true);
        assertEquals(4, home.vertical(0, 20, true));
        assertEquals(6, home.vertical(1, 20, true));
        assertEquals(8, home.vertical(2, 20, true));
        assertEquals(10, home.vertical(3, 20, true));
        assertEquals(0, home.vertical(4, 20, false));
        assertEquals(3, home.vertical(10, 20, false));
        assertEquals(17, home.vertical(10, 20, true));
        assertEquals(10, home.vertical(17, 20, false));
    }

    @Test public void shortLastRowClampsToItsLastApp() {
        HomeGeometry home = new HomeGeometry(6, true);
        assertEquals(11, home.vertical(9, 12, true));
        assertEquals(11, home.vertical(8, 12, true));
        assertEquals(10, home.vertical(4, 12, true));
    }

    @Test public void sparseDockNeverWrapsHorizontallyOrFocusesEmptyPlace() {
        HomeGeometry home = new HomeGeometry(7, true);
        for (boolean rtl : new boolean[] {false, true}) {
            assertTrue(home.atHorizontalEdge(0, 2, rtl, !rtl));
            assertTrue(home.atHorizontalEdge(1, 2, rtl, rtl));
            assertFalse(home.atHorizontalEdge(0, 2, rtl, rtl));
            assertEquals(1, DockSlots.focusable(home.vertical(10, 18, false), 2, 2));
        }
    }

    @Test public void noDockUsesConfiguredColumnsWithoutAnImaginaryFourPlaceRow() {
        HomeGeometry home = new HomeGeometry(5, false);
        assertEquals(5, home.spanCount());
        assertEquals(1, home.spanSize(0));
        assertFalse(home.isDock(0));
        assertEquals(0, home.rowStart(4));
        assertEquals(5, home.rowStart(5));
        assertEquals(9, home.vertical(4, 11, true));
        assertEquals(4, home.vertical(9, 11, false));
    }

    @Test public void emptyHomeAndTopBoundaryHaveNoTileTarget() {
        HomeGeometry home = new HomeGeometry(5, true);
        assertEquals(-1, home.vertical(0, 0, true));
        assertFalse(home.isLastRow(0, 0));
        for (int position = 0; position < 4; position++) {
            assertTrue(home.isFirstRow(position));
            assertEquals(-1, home.vertical(position, 13, false));
        }
    }

    @Test public void everyVerticalTargetIsAnAdjacentRowAndWithinTheCatalog() {
        for (boolean dock : new boolean[] {false, true}) {
            for (int columns = 4; columns <= 7; columns++) {
                HomeGeometry home = new HomeGeometry(columns, dock);
                for (int count = 1; count <= 40; count++) {
                    for (int position = 0; position < count; position++) {
                        int down = home.vertical(position, count, true);
                        if (down >= 0) {
                            assertTrue(down < count);
                            assertEquals(home.rowStart(position) + home.rowCapacity(position), home.rowStart(down));
                        }
                        int up = home.vertical(position, count, false);
                        if (up >= 0) {
                            assertTrue(up < count);
                            assertEquals(home.rowStart(position), home.rowStart(up) + home.rowCapacity(up));
                        }
                    }
                }
            }
        }
    }

    @Test public void dockOnlyHomeDoesNotRevealTheNextRegularRow() {
        // The Sony viewport is about 900px tall; these rows include labels/padding and dock decoration.
        int padding = HomeGeometry.dockTopPadding(900, 328, 292, 0, 120);
        int dockCardTop = padding + 12 + 16;
        int dockBandBottom = dockCardTop + 202 + 28;
        int firstRegularCardTop = padding + 328 + 16;
        assertEquals(70, 900 - dockBandBottom);  // a balanced margin below the dock's rounded band
        assertTrue("regular row peeks below dock", firstRegularCardTop >= 900);
    }

    @Test public void requestedRegularRowsFitWithoutRevealingOneMore() {
        for (int rows = 0; rows <= 3; rows++) {
            int padding = HomeGeometry.dockTopPadding(1200, 300, 200, rows, 80);
            int nextRowTop = padding + 300 + rows * 200;
            assertTrue("unrequested row is visible", nextRowTop >= 1200);
            if (rows > 0) assertTrue("requested last row is missing", nextRowTop - 200 < 1200);
        }
    }

    @Test public void shortScreensKeepTheTitleClearAndAllowRowsToScroll() {
        assertEquals(150, HomeGeometry.dockTopPadding(450, 300, 200, 3, 150));
    }

    @Test public void shortcutIsOutsideTheDockEvenWhenOnlyOneAppRemains() {
        assertEquals(0, HomeGeometry.shortcutPadding(0, true));
        assertEquals(0, HomeGeometry.shortcutPosition(0, true));
        for (int apps = 1; apps <= 12; apps++) {
            int slots = apps + HomeGeometry.shortcutPadding(apps, true);
            assertTrue("shortcut would occupy a pinned app place", slots >= 4);
            assertEquals(4, HomeGeometry.shortcutPosition(slots, true));
        }
    }

    @Test public void shortcutDoesNotInventDockSlotsInPlainGridMode() {
        assertEquals(0, HomeGeometry.shortcutPadding(2, false));
        assertEquals(0, HomeGeometry.shortcutPosition(2, false));
    }

    @Test public void shortcutNeverChangesTheConfiguredFullRowCount() {
        assertEquals(0, HomeGeometry.initialVisibleRows(0));
        assertEquals(2, HomeGeometry.initialVisibleRows(2));
        assertEquals(0, HomeGeometry.initialVisibleRows(-1));
    }

    @Test public void oneDownFromEveryDockAppReachesTheShortcutAcrossGridWidths() {
        for (int columns = 4; columns <= 7; columns++) {
            HomeGeometry home = new HomeGeometry(columns, true);
            for (int dockPosition = 0; dockPosition < 4; dockPosition++)
                assertEquals(4, home.down(dockPosition, 20, 4));
            assertEquals(4 + columns, home.down(4, 20, 4));
            assertEquals(home.vertical(3, 20, true), home.down(3, 20, -1));
        }
    }

    @Test public void relocatedShortcutDoesNotPullDownPastAdjacentPinnedRows() {
        HomeGeometry home = new HomeGeometry(4, true);
        assertEquals(6, home.down(2, 20, 12));
        assertEquals(8, home.down(4, 20, 12));
        assertEquals(4, home.down(0, 20, 2));
    }

    @Test public void floatingHeaderPreservesInitialDockPositionWithoutAReservedViewportStrip() {
        int originalGridOrigin = 180;
        int originalPadding = HomeGeometry.dockTopPadding(900, 328, 292, 0, 120);
        int fullViewportPadding = HomeGeometry.dockTopPadding(1080, 328, 292, 0, 300);
        assertEquals(originalGridOrigin + originalPadding, fullViewportPadding);
    }

    @Test public void zeroRowsWithShortcutShowsOnlyTheRequestedPartialPeek() {
        int peek = HomeGeometry.shortcutPeekOffset(0, true, 16, 56);  // 28dp peek at Sony density2
        int padding = HomeGeometry.dockTopPadding(1080, 328, 292, 0, 300, peek);
        int nextCardTop = padding + 328 + 16;
        assertEquals(56, 1080 - nextCardTop);
        assertTrue("a full regular row was exposed", 1080 - nextCardTop < 202);
        assertEquals(0, HomeGeometry.shortcutPeekOffset(0, false, 16, 56));
        assertEquals(0, HomeGeometry.shortcutPeekOffset(1, true, 16, 56));
    }

    @Test public void heldDownAtTheLastRowCannotProduceAnotherNavigationTarget() {
        for (int columns = 4; columns <= 7; columns++) {
            HomeGeometry home = new HomeGeometry(columns, true);
            for (int count = 1; count <= 24; count++) {
                int lastRow = home.rowStart(count - 1);
                for (int position = lastRow; position < count; position++) {
                    for (int repeat = 0; repeat < 10; repeat++)
                        assertEquals(-1, home.down(position, count, count > 4 ? 4 : -1));
                }
            }
            assertEquals(-1, home.down(0, 0, -1));
        }
    }
}
