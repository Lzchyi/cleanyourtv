package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Compact launcher preferences. The sidebar only contains categories with settings to change. */
public final class LauncherSettingsActivity extends Activity {
    private static final String CATEGORY_STATE = "settings_category";
    private static final String LIBRARY_SHORTCUT_KEY = "library_shortcut";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private final TextView[] categories = new TextView[4];
    private final View[] panes = new View[4];
    private final View[] contentFocus = new View[4];
    private ScrollView contentScroll;
    private TextView wallpaperChoice;
    private Switch animationsChoice;
    private Switch advancedChoice;
    private Switch libraryShortcutChoice;
    private TextView followThemeChoice;
    private View defaultHomeChoice;
    private View tvSettingsChoice;
    private int columns;
    private int rowsBelowDock;
    private int category;
    private boolean animations;
    private boolean advancedEffects;
    private boolean libraryShortcut;
    private boolean reducedMotion;
    private boolean refreshing;
    private boolean sidebarOnRight;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        readChoices();
        contentScroll = findViewById(R.id.settingsContentScroll);
        sidebarOnRight = contentScroll.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setContentDescription(getString(R.string.columns_label) + ": " + value);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setContentDescription(getString(R.string.rows_below_dock_label) + ": " + value);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
            });
        }
        // Keep the existing Hebrew numeric order. Navigation below follows the actual child positions.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnCheckedChangeListener((button, enabled) -> {
            if (refreshing) return;
            animations = enabled;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            finishSwitchMotion(animationsChoice);
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnCheckedChangeListener((button, enabled) -> {
            if (refreshing) return;
            if (!enabled) {
                setAdvanced(false);
            } else {
                // The switch remains off until the user accepts the existing performance warning.
                refresh();
                new AlertDialog.Builder(this, R.style.RedesignSettingsDialog)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        libraryShortcutChoice = findViewById(R.id.libraryShortcutChoice);
        libraryShortcutChoice.setOnCheckedChangeListener((button, enabled) -> {
            if (refreshing) return;
            libraryShortcut = enabled;
            choices.edit().putBoolean(LIBRARY_SHORTCUT_KEY, enabled).apply();
            changed();
            finishSwitchMotion(libraryShortcutChoice);
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(LIBRARY_SHORTCUT_KEY).commit();
            readChoices();
            changed();
        });
        defaultHomeChoice = findViewById(R.id.defaultHomeChoice);
        defaultHomeChoice.setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        tvSettingsChoice = findViewById(R.id.tvSettingsChoice);
        tvSettingsChoice.setOnClickListener(v -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        configureNavigation();
        refresh();
        category = state == null ? 0 : Math.max(0, Math.min(3, state.getInt(CATEGORY_STATE, 0)));
        selectCategory(category, false);
        categories[category].requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(CATEGORY_STATE, category);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStop() {
        finishTransitions();
        super.onStop();
    }

    private void readChoices() {
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        libraryShortcut = config.showLibraryShortcut;
        reducedMotion = config.reducedMotion;
    }

    private void configureNavigation() {
        int[] categoryIds = {R.id.settingsCategoryLayout, R.id.settingsCategoryAppearance,
                R.id.settingsCategoryMotion, R.id.settingsCategorySystem};
        int[] paneIds = {R.id.settingsPaneLayout, R.id.settingsPaneAppearance,
                R.id.settingsPaneMotion, R.id.settingsPaneSystem};
        for (int i = 0; i < categories.length; i++) {
            final int index = i;
            categories[i] = findViewById(categoryIds[i]);
            panes[i] = findViewById(paneIds[i]);
            categories[i].setOnFocusChangeListener((view, focused) -> {
                animateFocus(view, focused);
                if (focused) selectCategory(index, true);
            });
            categories[i].setOnClickListener(v -> {
                selectCategory(index, true);
                focusContent();
            });
            categories[i].setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == awayFromSidebar()) {
                    focusContent();
                    return true;
                }
                if (key == towardSidebar()) return true;
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int next = index + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                    if (next >= 0 && next < categories.length) categories[next].requestFocus();
                    return true;
                }
                return false;
            });
        }
        for (int i = 0; i < columnChoices.length; i++) {
            final int index = i;
            bindContentFocus(columnChoices[i], 0);
            bindContentFocus(rowChoices[i], 0);
            columnChoices[i].setOnKeyListener((view, key, event) ->
                    segmentKey(columnChoices, index, rowChoices[index], null, key, event));
            rowChoices[i].setOnKeyListener((view, key, event) ->
                    segmentKey(rowChoices, index, libraryShortcutChoice, columnChoices[index], key, event));
        }
        bindContentFocus(libraryShortcutChoice, 0);
        libraryShortcutChoice.setOnKeyListener((view, key, event) -> contentKey(key, event,
                rowChoices[Math.max(0, Math.min(rowsBelowDock, 3))], null));
        bindContentFocus(wallpaperChoice, 1);
        wallpaperChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, null, null));
        bindContentFocus(animationsChoice, 2);
        bindContentFocus(advancedChoice, 2);
        animationsChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, null,
                advancedChoice.isEnabled() ? advancedChoice : null));
        advancedChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, animationsChoice, null));
        bindContentFocus(followThemeChoice, 3);
        bindContentFocus(defaultHomeChoice, 3);
        bindContentFocus(tvSettingsChoice, 3);
        followThemeChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, null, defaultHomeChoice));
        defaultHomeChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, followThemeChoice, tvSettingsChoice));
        tvSettingsChoice.setOnKeyListener((view, key, event) -> contentKey(key, event, defaultHomeChoice, null));
    }

    private void bindContentFocus(View view, int pane) {
        view.setOnFocusChangeListener((focusedView, focused) -> {
            animateFocus(focusedView, focused);
            if (focused) contentFocus[pane] = focusedView;
        });
    }

    private int towardSidebar() {
        return sidebarOnRight ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
    }

    private int awayFromSidebar() {
        return sidebarOnRight ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private boolean contentKey(int key, KeyEvent event, View up, View down) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (key == towardSidebar()) {
            categories[category].requestFocus();
            return true;
        }
        if (key == awayFromSidebar()) return true;
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            View target = key == KeyEvent.KEYCODE_DPAD_UP ? up : down;
            if (target != null && target.isEnabled() && target.isShown()) target.requestFocus();
            return true;
        }
        return false;
    }

    private boolean segmentKey(TextView[] values, int index, View down, View up, int key, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            LinearLayout row = (LinearLayout) values[index].getParent();
            int next = row.indexOfChild(values[index]) + (key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            // Child order is the numeric reading order. Resolve physical direction for RTL layouts too.
            if (row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                next = row.indexOfChild(values[index]) + (key == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1);
            }
            if (next >= 0 && next < row.getChildCount()) row.getChildAt(next).requestFocus();
            else if (key == towardSidebar()) categories[category].requestFocus();
            return true;
        }
        return contentKey(key, event, up, down);
    }

    private void focusContent() {
        View target = contentFocus[category];
        if (target == null || !target.isEnabled() || !target.isShown()) {
            switch (category) {
                case 1: target = wallpaperChoice; break;
                case 2: target = animationsChoice; break;
                case 3: target = followThemeChoice; break;
                default: target = columnChoices[Math.max(0, Math.min(columns - 4, 3))];
            }
        }
        target.requestFocus();
    }

    private void selectCategory(int index, boolean animate) {
        boolean changed = category != index;
        category = index;
        for (int i = 0; i < panes.length; i++) {
            categories[i].setSelected(i == category);
            panes[i].animate().cancel();
            panes[i].setAlpha(1f);
            panes[i].setVisibility(i == category ? View.VISIBLE : View.GONE);
        }
        if (changed) {
            contentScroll.scrollTo(0, 0);
            if (animate && motionEnabled()) {
                panes[category].setAlpha(0f);
                panes[category].animate().alpha(1f).setDuration(motionDuration()).start();
            }
        }
    }

    private boolean motionEnabled() {
        return animations && ValueAnimator.areAnimatorsEnabled();
    }

    private int motionDuration() {
        return reducedMotion ? Motion.REDUCED_MS : getResources().getInteger(R.integer.premium_motion_ms);
    }

    private void animateFocus(View view, boolean focused) {
        view.animate().cancel();
        float scale = focusScale(view, focused);
        if (motionEnabled()) view.animate().scaleX(scale).scaleY(scale).setDuration(motionDuration()).start();
        else {
            view.setScaleX(scale);
            view.setScaleY(scale);
        }
    }

    private float focusScale(View view, boolean focused) {
        if (!focused) return 1f;
        // Keep even a full-width row's expansion inside its 6dp focus gutter.
        // A percentage alone expands wide rows far more than the numeric segments.
        int longestSide = Math.max(view.getWidth(), view.getHeight());
        float maxGrowth = 6f * getResources().getDisplayMetrics().density;
        return longestSide > 0 ? 1f + Math.min(0.015f, maxGrowth / longestSide) : 1f;
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size() : indexOf(images, current);
        new AlertDialog.Builder(this, R.style.RedesignSettingsDialog)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                            which == images.size() ? "none" : images.get(which).getName()).apply();
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled && animations;
        choices.edit().putBoolean("advanced_effects", advancedEffects).apply();
        changed();
    }

    private void changed() {
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        refreshing = true;
        for (int i = 0; i < columnChoices.length; i++) columnChoices[i].setSelected(columns == i + 4);
        for (int i = 0; i < rowChoices.length; i++) rowChoices[i].setSelected(rowsBelowDock == i);
        animationsChoice.setChecked(animations);
        advancedChoice.setChecked(advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.45f);
        libraryShortcutChoice.setChecked(libraryShortcut);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(current) ? getString(R.string.wallpaper_none)
                        : current != null ? current : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
        refreshing = false;
        // Switching motion off must finish transitions already in flight too.
        if (!motionEnabled()) finishTransitions();
    }

    private void finishTransitions() {
        for (View pane : panes) {
            if (pane == null) continue;
            pane.animate().cancel();
            pane.setAlpha(1f);
        }
        for (View view : categories) settleFocus(view);
        for (View view : columnChoices) settleFocus(view);
        for (View view : rowChoices) settleFocus(view);
        settleFocus(wallpaperChoice);
        settleFocus(animationsChoice);
        settleFocus(advancedChoice);
        settleFocus(libraryShortcutChoice);
        settleFocus(followThemeChoice);
        settleFocus(defaultHomeChoice);
        settleFocus(tvSettingsChoice);
        if (animationsChoice != null) animationsChoice.jumpDrawablesToCurrentState();
        if (advancedChoice != null) advancedChoice.jumpDrawablesToCurrentState();
        if (libraryShortcutChoice != null) libraryShortcutChoice.jumpDrawablesToCurrentState();
    }

    private void finishSwitchMotion(Switch control) {
        // Switch starts its thumb animation after notifying its listener; settle it on the next UI turn.
        if (!motionEnabled()) control.post(control::jumpDrawablesToCurrentState);
    }

    private void settleFocus(View view) {
        if (view == null) return;
        view.animate().cancel();
        float scale = focusScale(view, view.hasFocus());
        view.setScaleX(scale);
        view.setScaleY(scale);
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(LIBRARY_SHORTCUT_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
