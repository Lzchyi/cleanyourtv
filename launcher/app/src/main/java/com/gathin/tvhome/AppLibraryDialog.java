package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/** On-demand vertical app catalogue. Metadata and icons load off the UI thread; no backdrop capture. */
final class AppLibraryDialog {
    /**
     * The catalogue, the labels and the icons load here, off the UI thread: each label and icon opens the app's
     * resources, and on a TCL G09 that froze Home for 60-140 ms (2 to 4 janky frames) every time the library opened.
     */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    /** Labels kept between openings (LOADER thread only), until the app is updated. */
    private static final Map<String, Known> KNOWN = new HashMap<>();

    private static final class Known {
        final long updated;
        final String locale;
        final String label;

        Known(long updated, String locale, String label) {
            this.updated = updated;
            this.locale = locale;
            this.label = label;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private static final class Artwork {
        final Drawable drawable;
        final boolean banner;

        Artwork(Drawable drawable, boolean banner) {
            this.drawable = drawable;
            this.banner = banner;
        }
    }

    private static final int ARTWORK_CACHE_LIMIT = 24;
    /** Bound retained artwork while scrolling large catalogues; attached views keep their own references. */
    private final Map<String, Artwork> artwork = new LinkedHashMap<>(ARTWORK_CACHE_LIMIT, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Artwork> eldest) {
            return size() > ARTWORK_CACHE_LIMIT;
        }
    };
    private final Set<String> artworkLoading = new HashSet<>();
    private final Runnable dismissed;
    private boolean closed;
    /** Only the latest load fills the catalogue. */
    private int loads;
    private final Activity activity;
    private final Predicate<String> onHome;
    private final Runnable changed;
    private final LinearLayout panel;
    private final boolean reducedMotion;
    private final boolean animations;
    private final boolean rtl;
    final PopupWindow popup;
    private final float density;
    private List<ResolveInfo> allApps = new ArrayList<>();
    private List<ResolveInfo> apps = new ArrayList<>();
    private Set<String> hiddenApps = new HashSet<>();
    private boolean showingHidden;
    private TextView filter;
    private String selectedPackage;
    private int selectedPosition;
    private final Map<String, String> labels = new HashMap<>();
    private PopupWindow actionPopup;
    private RecyclerView catalog;
    private final int catalogWidth;
    private final int catalogHeight;
    private final int columns;
    private final int bannerHeight;

    private void stopCatalog() {
        if (catalog != null) catalog.stopScroll();
        catalog = null;
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1), 0x28FFFFFF);
        return bg;
    }

    private int motionDuration() {
        return animations && !reducedMotion && android.animation.ValueAnimator.areAnimatorsEnabled()
                ? activity.getResources().getInteger(R.integer.premium_motion_ms) : 0;
    }

    AppLibraryDialog(Activity activity, boolean animations, boolean reducedMotion, Predicate<String> onHome, Runnable changed, Runnable dismissed) {
        this.activity = activity;
        this.animations = animations;
        this.rtl = activity.getResources().getBoolean(R.bool.rtl_app_grid);
        this.reducedMotion = reducedMotion;
        this.onHome = onHome;
        this.changed = changed;
        this.dismissed = dismissed;
        density = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(56), dp(28), dp(56), dp(16));
        panel.setClipChildren(false);
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x25101826, 0x5510151F}));
        View decor = activity.getWindow().getDecorView();
        catalogWidth = decor.getWidth() > 0 ? decor.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        catalogHeight = decor.getHeight() > 0 ? decor.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
        int innerWidth = catalogWidth - dp(120);
        columns = LibraryNavigation.columns(Math.round(innerWidth / density));
        bannerHeight = Math.max(dp(40), Math.round((innerWidth / columns - dp(16)) * 9f / 16));
        popup = new PopupWindow(panel, catalogWidth, catalogHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setOnDismissListener(() -> {
            closed = true;
            dismissActions();
            stopCatalog();
            artwork.clear();
            dismissed.run();
        });
    }

    void close() {
        dismissActions();
        if (popup.isShowing()) {
            popup.dismiss();
        } else if (!closed) {
            closed = true;   // still loading: it never shows
            dismissed.run();
        }
    }

    void show() {
        load();
    }

    private void reveal() {
        popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
        View container = panel.getRootView();
        if (container.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.23f;
            activity.getWindowManager().updateViewLayout(container, params);
        }
        int duration = motionDuration();
        panel.setAlpha(duration > 0 ? 0f : 1f);
        if (duration > 0) panel.animate().alpha(1f).setDuration(duration).start();
    }

    /** Called by the activity's existing package receiver; an uninstall cannot leave a stale action. */
    /** Packages with a leanback entry, from the last refresh. */
    private final java.util.Set<String> tvPackages = new java.util.HashSet<>();

    void refresh() {
        load();
    }

    /** Reads the catalogue on LOADER, then fills it on the UI thread. */
    private void load() {
        dismissActions();
        int load = ++loads;
        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        String locale = activity.getResources().getConfiguration().getLocales().toLanguageTags();
        LOADER.execute(() -> {
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            Set<String> tv = new HashSet<>();
            // Prefer the TV entry when an app declares both categories.
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category)) tv.add(pkg);
                    if (!pkg.equals(self) && !unique.containsKey(pkg)) unique.put(pkg, info);
                }
            }
            Map<String, String> names = new HashMap<>();
            for (Map.Entry<String, ResolveInfo> entry : unique.entrySet()) {
                String pkg = entry.getKey();
                long updated = 0;
                try {
                    PackageInfo info = pm.getPackageInfo(pkg, 0);
                    updated = info.lastUpdateTime;
                } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { }
                Known known = KNOWN.get(pkg);
                if (known == null || known.updated != updated || !known.locale.equals(locale)) {
                    known = new Known(updated, locale, loadLabel(pm, entry.getValue()));
                    KNOWN.put(pkg, known);
                }
                names.put(pkg, known.label);
            }
            KNOWN.keySet().retainAll(unique.keySet());
            Set<String> hidden = LibraryApps.loadHidden(activity);
            main.post(() -> {
                if (closed || load != loads) return;
                tvPackages.clear();
                tvPackages.addAll(tv);
                hiddenApps = new HashSet<>(hidden);
                labels.clear();
                labels.putAll(names);
                fill(new ArrayList<>(unique.values()));
                // A package broadcast during the first load must not cancel the pending reveal.
                if (!popup.isShowing()) reveal();
            });
        });
    }

    private void fill(List<ResolveInfo> catalogue) {
        allApps = catalogue;
        Collator collator = Collator.getInstance(activity.getResources().getConfiguration().getLocales().get(0));
        allApps.sort((a, b) -> {
            String pa = a.activityInfo.packageName, pb = b.activityInfo.packageName;
            return LibraryNavigation.compare(collator, label(a), pa, label(b), pb);
        });
        showCatalog();
    }

    private String label(ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        String cached = labels.get(pkg);
        if (cached != null) return cached;
        String value = loadLabel(activity.getPackageManager(), app);
        labels.put(pkg, value);
        return value;
    }

    private static String loadLabel(PackageManager pm, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        try { return Cards.cleanLabel(String.valueOf(app.loadLabel(pm)), pkg); }
        catch (RuntimeException unavailable) { return pkg; }
    }

    /** Uses the app's own TV banner, with its original icon when no landscape banner is available. */
    private void bindArtwork(ImageView image, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        image.setTag(pkg);
        Artwork cached = artwork.get(pkg);
        showArtwork(image, cached);
        if (cached != null || !artworkLoading.add(pkg)) return;
        PackageManager pm = activity.getPackageManager();
        LOADER.execute(() -> {
            Drawable loaded = null;
            boolean banner = false;
            try {
                loaded = app.activityInfo.loadBanner(pm);
                banner = loaded != null && LibraryNavigation.landscapeBanner(
                        loaded.getIntrinsicWidth(), loaded.getIntrinsicHeight());
                if (!banner) loaded = app.loadIcon(pm);
            } catch (RuntimeException | OutOfMemoryError unavailable) {
                try { loaded = app.loadIcon(pm); }
                catch (RuntimeException | OutOfMemoryError missing) { loaded = null; }
                banner = false;
            }
            Drawable result = loaded;
            boolean isBanner = banner;
            main.post(() -> {
                artworkLoading.remove(pkg);
                if (closed) return;
                Artwork shown = new Artwork(result != null ? result : activity.getDrawable(R.drawable.ic_apps), isBanner);
                artwork.put(pkg, shown);
                // A catalogue refresh can replace a bound view while its artwork is loading.
                if (pkg.equals(image.getTag())) showArtwork(image, shown);
                if (catalog != null) {
                    for (int i = 0; i < catalog.getChildCount(); i++) {
                        ImageView bound = catalog.getChildAt(i).findViewById(R.id.icon);
                        if (bound != null && pkg.equals(bound.getTag())) showArtwork(bound, shown);
                    }
                }
            });
        });
    }

    private void showArtwork(ImageView image, Artwork art) {
        boolean banner = art != null && art.banner;
        image.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int padding = banner ? 0 : dp(12);
        image.setPadding(padding, padding, padding, padding);
        image.setImageDrawable(art == null ? null : art.drawable);
    }

    private void heading(String text) {
        TextView title = text(text, 18, 0xFFFFFFFF);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(12), dp(10), dp(12), dp(8));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showCatalog() {
        stopCatalog();
        panel.removeAllViews();
        apps = new ArrayList<>();
        int hiddenCount = 0;
        for (ResolveInfo app : allApps) {
            boolean hidden = hiddenApps.contains(app.activityInfo.packageName);
            if (hidden) hiddenCount++;
            if (hidden == showingHidden) apps.add(app);
        }
        // Unhiding the final app returns to the catalogue rather than leaving an empty focus trap.
        if (showingHidden && hiddenCount == 0) {
            showingHidden = false;
            apps = new ArrayList<>(allApps);
        }
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(8), dp(14));
        TextView title = text(activity.getString(R.string.app_library), 30,
                activity.getColor(R.color.premium_text));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        filter = null;
        if (hiddenCount > 0) {
            filter = text(showingHidden ? activity.getString(R.string.library_all_apps)
                    : activity.getString(R.string.library_hidden_filter, hiddenCount), 13,
                    activity.getColor(R.color.premium_text));
            filter.setGravity(Gravity.CENTER);
            filter.setPadding(dp(14), 0, dp(14), 0);
            filter.setFocusable(true);
            filter.setClickable(true);
            filter.setBackground(surface(activity.getColor(R.color.premium_surface), 12));
            filter.setOnFocusChangeListener((v, focused) -> focusAppearance(v, focused));
            filter.setOnClickListener(v -> {
                showingHidden = !showingHidden;
                selectedPackage = null;
                selectedPosition = 0;
                showCatalog();
            });
            filter.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) focusPosition(0);
                    return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT
                        || key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-2, dp(36));
            button.setMarginStart(dp(18));
            header.addView(filter, button);
        }
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));
        panel.setFocusableInTouchMode(apps.isEmpty() && filter == null);
        if (apps.isEmpty()) {
            heading(activity.getString(showingHidden ? R.string.library_hidden_empty : R.string.library_empty));
            panel.post(() -> {
                if (filter != null) filter.requestFocus();
                else panel.requestFocus();
            });
            return;
        }
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        catalog = list;
        list.setLayoutManager(new GridLayoutManager(activity, columns));
        list.setItemAnimator(null);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // The focused artwork grows into its item's margins; do not clip it to the unscaled row bounds.
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(4), dp(6), dp(4), dp(8));
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.setAdapter(new Catalog());
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        int position = Math.max(0, Math.min(selectedPosition, apps.size() - 1));
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).activityInfo.packageName.equals(selectedPackage)) { position = i; break; }
        }
        selectedPosition = position;
        ((GridLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        final int target = position;
        list.post(() -> focusPosition(target));
    }

    private void focusPosition(int position) {
        RecyclerView list = catalog;
        if (list == null || closed || position < 0 || position >= apps.size()) return;
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            holder.itemView.requestFocus();
            return;
        }
        list.scrollToPosition(position);
        list.post(() -> {
            if (catalog != list || closed) return;
            RecyclerView.ViewHolder target = list.findViewHolderForAdapterPosition(position);
            if (target != null) target.itemView.requestFocus();
        });
    }

    private void focusAppearance(View view, boolean focused) {
        View tile = view.findViewById(R.id.libraryArtwork);
        View target = tile != null ? tile : view;
        target.animate().cancel();
        GradientDrawable outline = surface(tile != null ? Color.TRANSPARENT
                : activity.getColor(R.color.premium_surface), tile != null ? 14 : 16);
        outline.setStroke(dp(focused ? 2 : 1), focused ? 0xE6FFFFFF : 0x20FFFFFF);
        if (tile != null) {
            tile.setForeground(outline);
            View name = view.findViewById(R.id.title);
            if (name != null) name.setAlpha(focused ? 1f : 0.9f);
        } else view.setBackground(outline);
        int duration = motionDuration();
        float scale = focused ? 1.05f : 1f;
        if (duration == 0) {
            target.setScaleX(scale);
            target.setScaleY(scale);
        } else target.animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
    }

    private void dismissActions() {
        PopupWindow menu = actionPopup;
        actionPopup = null;
        if (menu != null) menu.dismiss();
    }

    private void restoreCardFocus() {
        if (catalog == null || !popup.isShowing()) return;
        catalog.post(() -> {
            if (catalog == null || !popup.isShowing() || actionPopup != null) return;
            RecyclerView.ViewHolder holder = catalog.findViewHolderForAdapterPosition(selectedPosition);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void changeHome(ResolveInfo app, boolean add) {
        if (!LibraryApps.save(activity, app.activityInfo.packageName, add)) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        dismissActions();
        changed.run();
        showCatalog();
    }

    private void changeHidden(ResolveInfo app, boolean hidden) {
        String pkg = app.activityInfo.packageName;
        if (!LibraryApps.saveHidden(activity, pkg, hidden)) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        if (hidden) hiddenApps.add(pkg); else hiddenApps.remove(pkg);
        dismissActions();
        changed.run();
        showCatalog();
        // Supersede an in-flight package refresh that may have read the old hidden choices.
        refresh();
    }

    private void open(ResolveInfo app) {
        close();
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(app.activityInfo.packageName, app.activityInfo.name));
        // TV apps keep the leanback launch intent, so an app already running resumes instead of restarting.
        if (tvPackages.contains(app.activityInfo.packageName)) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
        }
    }

    private void showActions(ResolveInfo app, View anchor) {
        dismissActions();
        selectedPackage = app.activityInfo.packageName;
        selectedPosition = apps.indexOf(app);
        String pkg = selectedPackage;
        View menuPanel = activity.getLayoutInflater().inflate(R.layout.popup_inputs, panel, false);
        menuPanel.setBackground(surface(activity.getColor(R.color.premium_background), 16));
        LinearLayout rows = menuPanel.findViewById(R.id.rows);
        PopupWindow menu = new PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        actionPopup = menu;
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menu.setOutsideTouchable(true);
        menu.setAnimationStyle(0);
        menu.setElevation(0f);
        menu.setOnDismissListener(() -> {
            if (actionPopup == menu) actionPopup = null;
            restoreCardFocus();
        });
        addAction(rows, R.string.library_open, () -> open(app));
        boolean hidden = hiddenApps.contains(pkg);
        // The Home predicate reflects visible tiles. Keep hidden apps' original Home intent intact.
        if (!hidden) {
            boolean home = onHome.test(pkg);
            addAction(rows, home ? R.string.library_remove_shortcut : R.string.library_add_home,
                    () -> changeHome(app, !home));
        }
        addAction(rows, hidden ? R.string.library_unhide_app : R.string.library_hide_app,
                () -> changeHidden(app, !hidden));
        addAction(rows, R.string.library_app_info, () -> {
            close();
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
            }
        });
        int menuWidth = dp(220);
        for (int i = 0; i < rows.getChildCount(); i++) {
            TextView title = rows.getChildAt(i).findViewById(R.id.title);
            menuWidth = Math.max(menuWidth, (int) Math.ceil(title.getPaint().measureText(title.getText().toString())) + dp(58));
            title.setEllipsize(TextUtils.TruncateAt.END);
        }
        menuWidth = Math.min(menuWidth, panel.getWidth() - dp(24));
        menu.setWidth(menuWidth);
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int[] panelLocation = new int[2];
        panel.getLocationOnScreen(panelLocation);
        Rect screen = new Rect(panelLocation[0], panelLocation[1],
                panelLocation[0] + panel.getWidth(), panelLocation[1] + panel.getHeight());
        int x = Math.max(screen.left + dp(12), Math.min(location[0] + anchor.getWidth() - dp(12), screen.right - menuWidth - dp(12)));
        int y = Math.max(screen.top + dp(12), Math.min(location[1] + dp(8), screen.bottom - menuPanel.getMeasuredHeight() - dp(12)));
        int duration = motionDuration();
        menuPanel.setAlpha(duration > 0 ? 0f : 1f);
        menu.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.LEFT, x, y);
        if (duration > 0) menuPanel.animate().alpha(1f).setDuration(duration).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addAction(LinearLayout rows, int label, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        TextView title = row.findViewById(R.id.title);
        title.setText(label);
        title.setTextColor(activity.getColor(R.color.premium_text));
        row.getLayoutParams().height = activity.getResources().getDimensionPixelSize(R.dimen.premium_control_height);
        row.setFocusableInTouchMode(true);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v -> action.run());
        row.setOnFocusChangeListener((v, focused) -> focusAppearance(v, focused));
        rows.addView(row);
        row.setOnKeyListener((v, key, event) -> {
            int index = rows.indexOfChild(v);
            return event.getAction() == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP && index == 0
                    || key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.getChildCount() - 1);
        });
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
        view.setLetterSpacing(0.01f);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(int value) { return Math.round(value * density); }

    private final class Catalog extends RecyclerView.Adapter<Row> {
        Catalog() { setHasStableIds(true); }

        @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout row = new FrameLayout(activity);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, bannerHeight + dp(26));
            params.setMargins(dp(8), dp(6), dp(8), dp(8));
            row.setLayoutParams(params);
            row.setClipChildren(false);
            row.setFocusable(true);
            row.setClickable(true);
            row.setLongClickable(true);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setClipChildren(false);
            row.addView(content, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout tile = new FrameLayout(activity);
            tile.setId(R.id.libraryArtwork);
            tile.setBackground(surface(0xAD202735, 14));
            tile.setClipToOutline(true);
            content.addView(tile, new LinearLayout.LayoutParams(-1, bannerHeight));
            ImageView image = new ImageView(activity);
            image.setId(R.id.icon);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView name = text("", 13, activity.getColor(R.color.premium_text));
            name.setId(R.id.title);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(20));
            nameParams.topMargin = dp(6);
            content.addView(name, nameParams);
            return new Row(row);
        }

        @Override public void onBindViewHolder(@NonNull Row holder, int position) {
            ResolveInfo app = apps.get(position);
            String pkg = app.activityInfo.packageName;
            View card = holder.itemView;
            bindArtwork(card.findViewById(R.id.icon), app);
            ((TextView) card.findViewById(R.id.title)).setText(label(app));
            card.setContentDescription(hiddenApps.contains(pkg)
                    ? activity.getString(R.string.library_hidden_description, label(app)) : label(app));
            focusAppearance(card, card.hasFocus());
            card.setOnClickListener(v -> open(app));
            card.setOnLongClickListener(v -> { showActions(app, v); return true; });
            card.setOnFocusChangeListener((v, focused) -> {
                focusAppearance(v, focused);
                if (focused) {
                    selectedPackage = pkg;
                    int index = holder.getBindingAdapterPosition();
                    if (index != RecyclerView.NO_POSITION) selectedPosition = index;
                }
            });
            card.setOnKeyListener((v, key, event) -> {
                int index = holder.getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION) return false;
                if (key == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) showActions(app, v);
                    return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return LibraryNavigation.horizontalEdge(index, apps.size(), columns, rtl,
                            key == KeyEvent.KEYCODE_DPAD_LEFT);
                }
                if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                int target = LibraryNavigation.verticalTarget(index, apps.size(), columns,
                        key == KeyEvent.KEYCODE_DPAD_DOWN);
                if (target < 0) {
                    if (filter != null) filter.requestFocus();
                } else if (target != index) focusPosition(target);
                return true;
            });
        }

        @Override public void onViewRecycled(@NonNull Row holder) {
            holder.itemView.animate().cancel();
            holder.itemView.findViewById(R.id.libraryArtwork).animate().cancel();
            ImageView icon = holder.itemView.findViewById(R.id.icon);
            icon.setTag(null);
            icon.setImageDrawable(null);
        }

        @Override public int getItemCount() { return apps.size(); }
        @Override public long getItemId(int position) {
            return LibraryNavigation.stableId(apps.get(position).activityInfo.packageName);
        }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        Row(View view) { super(view); }
    }
}
