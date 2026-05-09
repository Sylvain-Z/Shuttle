package com.jp.wasabeef.glide.transformations.internal;

import android.graphics.Bitmap;

/**
 * Copyright (C) 2015 Wasabeef
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class FastBlur {

    private FastBlur() {
    }

    private static class BlurState {
        final int[] pix;
        final int[] r;
        final int[] g;
        final int[] b;
        final int[] vmin;
        final int[] dv;
        final int[][] stack;
        final int w;
        final int h;
        final int wm;
        final int hm;
        final int div;
        final int radius;
        final int r1;

        BlurState(int[] pix, int w, int h, int radius) {
            this.pix = pix;
            this.w = w;
            this.h = h;
            this.wm = w - 1;
            this.hm = h - 1;
            this.radius = radius;
            this.r1 = radius + 1;
            this.div = radius + radius + 1;
            int divsum = (this.div + 1) >> 1;
            divsum *= divsum;
            this.dv = buildDivisionLookup(divsum);
            this.r = new int[w * h];
            this.g = new int[w * h];
            this.b = new int[w * h];
            this.vmin = new int[Math.max(w, h)];
            this.stack = new int[this.div][3];
        }

        private static int[] buildDivisionLookup(int divsum) {
            int[] table = new int[256 * divsum];
            for (int i = 0; i < table.length; i++) {
                table[i] = i / divsum;
            }
            return table;
        }
    }

    public static Bitmap blur(Bitmap sentBitmap, int radius, boolean canReuseInBitmap) {
        Bitmap bitmap = canReuseInBitmap ? sentBitmap : sentBitmap.copy(sentBitmap.getConfig(), true);

        if (radius < 1) {
            return null;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        BlurState state = new BlurState(pix, w, h, radius);
        blurHorizontalPass(state);
        blurVerticalPass(state);

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private static void blurHorizontalPass(BlurState s) {
        int yw = 0;
        int yi = 0;
        for (int y = 0; y < s.h; y++) {
            int rinsum = 0;
            int ginsum = 0;
            int binsum = 0;
            int routsum = 0;
            int goutsum = 0;
            int boutsum = 0;
            int rsum = 0;
            int gsum = 0;
            int bsum = 0;
            for (int i = -s.radius; i <= s.radius; i++) {
                int p = s.pix[yi + Math.min(s.wm, Math.max(i, 0))];
                int[] sir = s.stack[i + s.radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                int rbs = s.r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            int stackpointer = s.radius;
            for (int x = 0; x < s.w; x++) {
                s.r[yi] = s.dv[rsum];
                s.g[yi] = s.dv[gsum];
                s.b[yi] = s.dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                int[] sir = s.stack[(stackpointer - s.radius + s.div) % s.div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (y == 0) {
                    s.vmin[x] = Math.min(x + s.radius + 1, s.wm);
                }
                int p = s.pix[yw + s.vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % s.div;
                sir = s.stack[stackpointer % s.div];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi++;
            }
            yw += s.w;
        }
    }

    private static void blurVerticalPass(BlurState s) {
        for (int x = 0; x < s.w; x++) {
            int rinsum = 0;
            int ginsum = 0;
            int binsum = 0;
            int routsum = 0;
            int goutsum = 0;
            int boutsum = 0;
            int rsum = 0;
            int gsum = 0;
            int bsum = 0;
            int yp = -s.radius * s.w;
            for (int i = -s.radius; i <= s.radius; i++) {
                int yi = Math.max(0, yp) + x;
                int[] sir = s.stack[i + s.radius];
                sir[0] = s.r[yi];
                sir[1] = s.g[yi];
                sir[2] = s.b[yi];
                int rbs = s.r1 - Math.abs(i);
                rsum += s.r[yi] * rbs;
                gsum += s.g[yi] * rbs;
                bsum += s.b[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < s.hm) {
                    yp += s.w;
                }
            }
            int yi = x;
            int stackpointer = s.radius;
            for (int y = 0; y < s.h; y++) {
                s.pix[yi] = (0xff000000 & s.pix[yi]) | (s.dv[rsum] << 16) | (s.dv[gsum] << 8) | s.dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                int[] sir = s.stack[(stackpointer - s.radius + s.div) % s.div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (x == 0) {
                    s.vmin[y] = Math.min(y + s.r1, s.hm) * s.w;
                }
                int p = x + s.vmin[y];
                sir[0] = s.r[p];
                sir[1] = s.g[p];
                sir[2] = s.b[p];
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % s.div;
                sir = s.stack[stackpointer];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi += s.w;
            }
        }
    }
}
