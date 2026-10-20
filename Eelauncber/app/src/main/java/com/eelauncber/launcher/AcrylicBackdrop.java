package com.eelauncber.launcher;

import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/** Blur a downsampled snapshot of the wallpaper and launcher, without relying on window blur flags. */
final class AcrylicBackdrop {
    private AcrylicBackdrop() {}

    static View create(LauncherActivity a) {
        int rootWidth = a.root == null ? 0 : a.root.getWidth();
        int rootHeight = a.root == null ? 0 : a.root.getHeight();
        int displayWidth = a.getResources().getDisplayMetrics().widthPixels;
        int displayHeight = a.getResources().getDisplayMetrics().heightPixels;
        int fullWidth = rootWidth > 0 ? rootWidth : displayWidth;
        int fullHeight = rootHeight > 0 ? rootHeight : displayHeight;
        int smallWidth = Math.max(96, Math.min(210, fullWidth / 7));
        int smallHeight = Math.max(160, Math.round((float) fullHeight * smallWidth / Math.max(1, fullWidth)));
        Bitmap bitmap = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);

        int base = Color.rgb(32, 43, 60);
        Drawable wallpaper = null;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(a);
            if (Build.VERSION.SDK_INT >= 27) {
                WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null && colors.getPrimaryColor() != null) {
                    int col = colors.getPrimaryColor().toArgb();
                    base = Color.rgb((Color.red(col) + 34) / 2,
                            (Color.green(col) + 48) / 2,
                            (Color.blue(col) + 65) / 2);
                }
            }
            wallpaper = manager.peekDrawable();
            if (wallpaper == null) wallpaper = manager.getDrawable();
        } catch (Exception ignored) {
            // Android 13+ sometimes restricts access to the actual wallpaper bytes.
        }
        c.drawColor(base);
        if (wallpaper != null) {
            try {
                wallpaper.setBounds(0, 0, smallWidth, smallHeight);
                wallpaper.draw(c);
            } catch (Exception ignored) {}
        }
        if (a.root != null && rootWidth > 0 && rootHeight > 0) {
            int checkpoint = c.save();
            c.scale((float) smallWidth / rootWidth, (float) smallHeight / rootHeight);
            a.root.draw(c);
            c.restoreToCount(checkpoint);
        }
        blur(bitmap, 7, 3);
        ImageView image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImageBitmap(bitmap);
        image.setAlpha(.82f);
        image.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return image;
    }

    /** Separable box passes approximate Gaussian blur, O(width*height), including old Android. */
    private static void blur(Bitmap bitmap, int radius, int passes) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] src = new int[w * h];
        int[] tmp = new int[src.length];
        bitmap.getPixels(src, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < h; y++) {
                int r = 0, g = 0, b = 0;
                for (int x = -radius; x <= radius; x++) {
                    int color = src[y*w + clamp(x, w)];
                    r += Color.red(color); g += Color.green(color); b += Color.blue(color);
                }
                int kernel = radius * 2 + 1;
                for (int x = 0; x < w; x++) {
                    tmp[y*w+x] = Color.rgb(r/kernel, g/kernel, b/kernel);
                    int remove = src[y*w+clamp(x-radius, w)];
                    int add = src[y*w+clamp(x+radius+1, w)];
                    r += Color.red(add) - Color.red(remove);
                    g += Color.green(add) - Color.green(remove);
                    b += Color.blue(add) - Color.blue(remove);
                }
            }
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int y = -radius; y <= radius; y++) {
                    int color = tmp[clamp(y, h)*w+x];
                    r += Color.red(color); g += Color.green(color); b += Color.blue(color);
                }
                int kernel = radius * 2 + 1;
                for (int y = 0; y < h; y++) {
                    src[y*w+x] = Color.rgb(r/kernel, g/kernel, b/kernel);
                    int remove = tmp[clamp(y-radius, h)*w+x];
                    int add = tmp[clamp(y+radius+1, h)*w+x];
                    r += Color.red(add) - Color.red(remove);
                    g += Color.green(add) - Color.green(remove);
                    b += Color.blue(add) - Color.blue(remove);
                }
            }
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h);
    }

    private static int clamp(int value, int extent) {
        return Math.max(0, Math.min(extent-1, value));
    }
}
