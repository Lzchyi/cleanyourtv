package com.gathin.tvhome;

/** Row geometry for a fixed four-place dock followed by independently sized Home rows. */
final class HomeGeometry {
    static final int DOCK_CAPACITY = 4;
    private final int columns;
    private final boolean dock;

    HomeGeometry(int columns, boolean dock) {
        this.columns = Math.max(1, columns);
        this.dock = dock;
    }

    /**
     * Park the dock so exactly the requested rows follow it inside the viewport. Item height already
     * includes its label/padding and the dock decoration, which leave the final card breathing room.
     * Reserving more blank space here would expose the start of the next unrequested RecyclerView row.
     */
    static int dockTopPadding(int viewportHeight, int dockHeight, int regularHeight, int visibleRows, int minimumTop) {
        return Math.max(minimumTop, viewportHeight - dockHeight - Math.max(0, visibleRows) * regularHeight);
    }

    /** Keep a shortcut outside a partial dock, without inventing focusable application slots. */
    static int shortcutPadding(int appSlots, boolean dock) {
        return dock && appSlots > 0 && appSlots < DOCK_CAPACITY ? DOCK_CAPACITY - appSlots : 0;
    }
    static int shortcutPosition(int appSlots, boolean dock) { return dock && appSlots > 0 ? DOCK_CAPACITY : 0; }
    static int initialVisibleRows(int configured) { return Math.max(0, configured); }

    /** Only a small preview of the first ordinary row, never an extra fully visible row. */
    static int shortcutPeekOffset(int configuredRows, boolean shortcut, int cardTopInset, int previewHeight) {
        return configuredRows == 0 && shortcut ? cardTopInset + previewHeight : 0;
    }

    static int dockTopPadding(int viewportHeight, int dockHeight, int regularHeight,
                              int visibleRows, int minimumTop, int previewOffset) {
        return Math.max(minimumTop, dockTopPadding(viewportHeight, dockHeight, regularHeight,
                visibleRows, minimumTop) - Math.max(0, previewOffset));
    }

    int spanCount() { return dock ? DOCK_CAPACITY * columns : columns; }
    int spanSize(int position) { return dock ? (position < DOCK_CAPACITY ? columns : DOCK_CAPACITY) : 1; }
    boolean isDock(int position) { return dock && position >= 0 && position < DOCK_CAPACITY; }
    int rowStart(int position) {
        if (isDock(position)) return 0;
        int offset = dock ? DOCK_CAPACITY : 0;
        return offset + Math.max(0, position - offset) / columns * columns;
    }
    int rowCapacity(int position) { return isDock(position) ? DOCK_CAPACITY : columns; }
    int rowEnd(int position, int count) { return Math.min(count, rowStart(position) + rowCapacity(position)) - 1; }
    boolean isFirstRow(int position) { return rowStart(position) == 0; }
    boolean isLastRow(int position, int count) { return count > 0 && rowEnd(position, count) == count - 1; }

    /** Maps cell centres across the dock/grid boundary; -1 means no row exists in that direction. */
    int vertical(int position, int count, boolean down) {
        if (position < 0 || position >= count) return -1;
        int start = rowStart(position), capacity = rowCapacity(position);
        int nextStart;
        if (down) {
            nextStart = start + capacity;
            if (nextStart >= count) return -1;
        } else {
            if (start == 0) return -1;
            nextStart = dock && start == DOCK_CAPACITY ? 0 : Math.max(0, start - columns);
        }
        int nextCapacity = rowCapacity(nextStart);
        int column = (int) ((position - start + 0.5f) * nextCapacity / capacity);
        return Math.min(count - 1, nextStart + Math.min(nextCapacity - 1, column));
    }

    /** A visible shortcut is one Down away from any dock app; ordinary rows keep their usual mapping. */
    int down(int position, int count, int shortcut) {
        return isDock(position) && shortcut == DOCK_CAPACITY && shortcut < count ? shortcut : vertical(position, count, true);
    }

    boolean atHorizontalEdge(int position, int count, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        return towardStart ? position == rowStart(position) : position == rowEnd(position, count);
    }
}
