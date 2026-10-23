package com.gathin.tvhome;

import java.text.Collator;

/** Deterministic catalogue ordering and physical D-pad boundaries, independent of Android views. */
final class LibraryNavigation {
    private LibraryNavigation() { }

    static int columns(int availableWidthDp) {
        return Math.max(2, Math.min(6, availableWidthDp / 130));
    }

    static boolean landscapeBanner(int width, int height) {
        return width <= 0 || height <= 0 || width >= height * 1.4f;
    }

    static int compare(Collator collator, String labelA, String packageA, String labelB, String packageB) {
        int label = collator.compare(labelA, labelB);
        return label != 0 ? label : packageA.compareTo(packageB);
    }

    /** The last row may be incomplete; Down lands on its nearest valid column. */
    static int verticalTarget(int position, int count, int columns, boolean down) {
        if (count <= 0 || columns <= 0) return -1;
        position = Math.max(0, Math.min(position, count - 1));
        if (!down) return position < columns ? -1 : position - columns;
        int lastRow = ((count - 1) / columns) * columns;
        return position >= lastRow ? position : Math.min(position + columns, count - 1);
    }

    static boolean horizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        if (count <= 0 || columns <= 0) return true;
        boolean towardStart = left != rtl;
        return towardStart ? position % columns == 0 : position % columns == columns - 1 || position == count - 1;
    }

    /** Unlike String.hashCode, common package-name collisions do not share RecyclerView item IDs. */
    static long stableId(String pkg) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < pkg.length(); i++) {
            hash ^= pkg.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
