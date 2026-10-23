package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final ImageView softened;
    private final View navigationShade;
    private Bitmap blurBitmap;
    private int blurGeneration;
    private int navigationDepth;
    private boolean navigationMotion;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private volatile boolean closed;
    private final boolean motionEnabled;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        motionEnabled = config.animations && !config.reducedMotion;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        softened = new ImageView(context);
        softened.setScaleType(ImageView.ScaleType.CENTER_CROP);
        softened.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        softened.setAlpha(0f);
        navigationShade = new View(context);
        navigationShade.setBackgroundColor(0xFF000000);
        navigationShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        navigationShade.setAlpha(0f);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(softened, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(navigationShade, 2, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                requestBlur(first);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(files, previous, order, random);
        }
    }

    void start() {
        active = true;
        showCaption(false);
        update();
    }

    void stop() {
        active = false;
        if (caption != null) caption.animate().withEndAction(null).cancel();
        softened.animate().cancel();
        navigationShade.animate().cancel();
        applyDepth(false);
    }

    void close() {
        active = false;
        closed = true;
        if (caption != null) caption.animate().withEndAction(null).cancel();
        blurGeneration++;
        decoder.shutdownNow();
        softened.animate().cancel();
        navigationShade.animate().cancel();
        image.setImageDrawable(null);
        softened.setImageDrawable(null);
        Bitmap sharp = current, blurred = blurBitmap;
        current = blurBitmap = null;
        main.postDelayed(() -> {
            if (sharp != null && !sharp.isRecycled()) sharp.recycle();
            if (blurred != null && !blurred.isRecycled()) blurred.recycle();
        }, 500);
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            main.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                requestBlur(next);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /** Called only on focus changes: zero is the header, one the dock, two the ordinary rows. */
    void setNavigationDepth(int depth, boolean animate) {
        int next = Math.max(0, Math.min(2, depth));
        if (next == navigationDepth && animate == navigationMotion) return;
        navigationDepth = next;
        navigationMotion = animate;
        applyDepth(animate && active);
    }

    private void applyDepth(boolean animate) {
        float blur = blurBitmap == null ? 0f : navigationDepth == 2 ? 0.92f : navigationDepth == 1 ? 0.38f : 0f;
        float shade = navigationDepth == 2 ? 0.18f : navigationDepth == 1 ? 0.05f : 0f;
        softened.animate().cancel();
        navigationShade.animate().cancel();
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            softened.setAlpha(blur);
            navigationShade.setAlpha(shade);
        } else {
            softened.animate().alpha(blur).setDuration(280).start();
            navigationShade.animate().alpha(shade).setDuration(280).start();
        }
    }

    private void requestBlur(File source) {
        int generation = ++blurGeneration;
        decoder.execute(() -> {
            Bitmap thumbnail = blurredThumbnail(source);
            if (closed) {
                if (thumbnail != null) thumbnail.recycle();
                return;
            }
            main.post(() -> {
                if (closed || generation != blurGeneration) {
                    if (thumbnail != null) thumbnail.recycle();
                    return;
                }
                Bitmap old = blurBitmap;
                blurBitmap = thumbnail;
                softened.setImageBitmap(thumbnail);
                applyDepth(active && navigationMotion);
                if (old != null) main.postDelayed(old::recycle, 500);
            });
        });
    }

    /** One bounded worker-side cache per picture. Any decode/allocation failure leaves cheap dimming. */
    private Bitmap blurredThumbnail(File source) {
        Bitmap decoded = null, thumbnail = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            int w = Math.min(320, width), h = Math.max(1, Math.min(320, Math.round(w * height / (float) width)));
            options.inSampleSize = WallpaperSize.sampleSize(options.outWidth, options.outHeight, w, h);
            // Extreme aspect ratios need not allocate a second large decode just to make a preview.
            if ((long) (options.outWidth / options.inSampleSize)
                    * (options.outHeight / options.inSampleSize) > 204_800L) return null;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            decoded = BitmapFactory.decodeFile(source.getPath(), options);
            if (decoded == null) return null;
            thumbnail = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(thumbnail);
            canvas.drawColor(0xFF000000);
            float scale = Math.max(w / (float) decoded.getWidth(), h / (float) decoded.getHeight());
            float dw = decoded.getWidth() * scale, dh = decoded.getHeight() * scale;
            canvas.drawBitmap(decoded, null, new RectF((w - dw) / 2f, (h - dh) / 2f,
                    (w + dw) / 2f, (h + dh) / 2f), new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels = new int[w * h];
            thumbnail.getPixels(pixels, 0, w, 0, 0, w, h);
            WallpaperBlur.blur(pixels, w, h, 4);
            thumbnail.setPixels(pixels, 0, w, 0, 0, w, h);
            if (dimColor != 0) canvas.drawColor(dimColor);
            Bitmap gpu = thumbnail.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) { Bitmap result = thumbnail; thumbnail = null; return result; }
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            Log.w("TvHome.Wallpaper", "Using navigation dimming without blur");
            return null;
        } finally {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
        }
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || !motionEnabled || !ValueAnimator.areAnimatorsEnabled() || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            if (closed || !active) return;
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) {
                Bitmap software = decoded;
                decoded = null;   // ownership transfers to the ImageView instead of the finally block
                return software;
            }
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        } finally {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
        }
    }
}
