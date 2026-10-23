package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class WallpaperBlurTest {
    @Test public void uniformImageAndSinglePixelKeepTheirColour() {
        int[] pixels = new int[35];
        Arrays.fill(pixels, 0xFF27384A);
        WallpaperBlur.blur(pixels, 7, 5, 4);
        for (int pixel : pixels) assertEquals(0xFF27384A, pixel);
        int[] single = {0xFF123456};
        WallpaperBlur.blur(single, 1, 1, 5);
        assertEquals(0xFF123456, single[0]);
    }

    @Test public void highContrastEdgeSoftensWithoutWrappingAcrossImage() {
        int[] pixels = new int[9];
        Arrays.fill(pixels, 0, 4, 0xFF000000);
        Arrays.fill(pixels, 4, 9, 0xFFFFFFFF);
        WallpaperBlur.blur(pixels, 9, 1, 1);
        assertEquals(0xFF000000, pixels[0]);
        assertEquals(0xFFFFFFFF, pixels[8]);
        assertTrue((pixels[3] & 255) > 0 && (pixels[3] & 255) < 255);
        for (int i = 1; i < pixels.length; i++) assertTrue((pixels[i] & 255) >= (pixels[i - 1] & 255));
    }

    @Test public void rectangularImageIsSymmetricAcrossBothAxes() {
        int[] pixels = new int[25];
        Arrays.fill(pixels, 0xFF000000);
        pixels[12] = 0xFFFFFFFF;
        WallpaperBlur.blur(pixels, 5, 5, 1);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            assertEquals(pixels[y * 5 + x], pixels[y * 5 + 4 - x]);
            assertEquals(pixels[y * 5 + x], pixels[(4 - y) * 5 + x]);
        }
    }

    @Test(expected = IllegalArgumentException.class) public void refusesFullScreenBuffer() {
        WallpaperBlur.blur(new int[1920 * 1080], 1920, 1080, 4);
    }

    @Test(expected = IllegalArgumentException.class) public void refusesMismatchedBuffer() {
        WallpaperBlur.blur(new int[10], 4, 4, 4);
    }
}
