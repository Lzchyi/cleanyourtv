package com.gathin.tvhome;

/** Bounded, separable box blur for a single tiny wallpaper thumbnail, never an animation frame. */
final class WallpaperBlur {
    private WallpaperBlur() { }

    static void blur(int[] pixels, int width, int height, int radius) {
        if (width < 1 || height < 1 || width > 320 || height > 320
                || pixels.length != width * height || radius < 1 || radius > 12) {
            throw new IllegalArgumentException("Wallpaper thumbnail exceeds its bounds");
        }
        int[] temporary = new int[pixels.length];
        // Two small box passes soften the downsampled image without a Gaussian kernel or library.
        for (int pass = 0; pass < 2; pass++) {
            filter(pixels, temporary, width, height, radius, false);
            filter(temporary, pixels, width, height, radius, true);
        }
    }

    private static void filter(int[] source, int[] target, int width, int height,
                               int radius, boolean vertical) {
        int lines = vertical ? width : height;
        int length = vertical ? height : width;
        int step = vertical ? width : 1;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = vertical ? line : line * width;
            int alpha = 0, red = 0, green = 0, blue = 0;
            for (int k = -radius; k <= radius; k++) {
                int value = source[base + Math.max(0, Math.min(length - 1, k)) * step];
                alpha += value >>> 24;
                red += value >> 16 & 255;
                green += value >> 8 & 255;
                blue += value & 255;
            }
            for (int position = 0; position < length; position++) {
                target[base + position * step] = (alpha / count << 24) | (red / count << 16)
                        | (green / count << 8) | blue / count;
                int leaving = source[base + Math.max(0, position - radius) * step];
                int entering = source[base + Math.min(length - 1, position + radius + 1) * step];
                alpha += (entering >>> 24) - (leaving >>> 24);
                red += (entering >> 16 & 255) - (leaving >> 16 & 255);
                green += (entering >> 8 & 255) - (leaving >> 8 & 255);
                blue += (entering & 255) - (leaving & 255);
            }
        }
    }
}
