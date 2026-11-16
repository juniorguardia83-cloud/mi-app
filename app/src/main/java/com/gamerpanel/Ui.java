package com.gamerpanel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Ayudas de dibujo. Todo se dibuja por código para que la app pese muy poco. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable gradient(int c1, int c2, float radiusPx) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable oval(int c1, int c2) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    /** Íconos simples dibujados por código. */
    static final class IconView extends View {
        static final int PREV = 0, PLAY = 1, NEXT = 2, CLOSE = 3, PAD = 4,
                PAUSE = 5, GRIP = 6, SEND = 7, BACK = 8;

        private int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int color = Color.WHITE;
        private final int accent = 0xFF2B1A66;

        IconView(Context c, int type) {
            super(c);
            this.type = type;
        }

        void setType(int type) {
            if (this.type != type) {
                this.type = type;
                invalidate();
            }
        }

        void setIconColor(int color) {
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            path.reset();

            switch (type) {
                case PLAY:
                    path.moveTo(cx - .16f * s, cy - .22f * s);
                    path.lineTo(cx - .16f * s, cy + .22f * s);
                    path.lineTo(cx + .24f * s, cy);
                    path.close();
                    cv.drawPath(path, p);
                    break;

                case PAUSE:
                    cv.drawRect(cx - .2f * s, cy - .22f * s, cx - .06f * s, cy + .22f * s, p);
                    cv.drawRect(cx + .06f * s, cy - .22f * s, cx + .2f * s, cy + .22f * s, p);
                    break;

                case PREV:
                    cv.drawRect(cx - .24f * s, cy - .2f * s, cx - .18f * s, cy + .2f * s, p);
                    path.moveTo(cx + .22f * s, cy - .2f * s);
                    path.lineTo(cx + .22f * s, cy + .2f * s);
                    path.lineTo(cx - .14f * s, cy);
                    path.close();
                    cv.drawPath(path, p);
                    break;

                case NEXT:
                    cv.drawRect(cx + .18f * s, cy - .2f * s, cx + .24f * s, cy + .2f * s, p);
                    path.moveTo(cx - .22f * s, cy - .2f * s);
                    path.lineTo(cx - .22f * s, cy + .2f * s);
                    path.lineTo(cx + .14f * s, cy);
                    path.close();
                    cv.drawPath(path, p);
                    break;

                case SEND:
                    path.moveTo(cx - .2f * s, cy - .2f * s);
                    path.lineTo(cx + .26f * s, cy);
                    path.lineTo(cx - .2f * s, cy + .2f * s);
                    path.lineTo(cx - .1f * s, cy);
                    path.close();
                    cv.drawPath(path, p);
                    break;

                case CLOSE:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(.08f * s);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(cx - .17f * s, cy - .17f * s, cx + .17f * s, cy + .17f * s, p);
                    cv.drawLine(cx + .17f * s, cy - .17f * s, cx - .17f * s, cy + .17f * s, p);
                    break;

                case BACK:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(.08f * s);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(cx + .08f * s, cy - .2f * s, cx - .1f * s, cy, p);
                    cv.drawLine(cx - .1f * s, cy, cx + .08f * s, cy + .2f * s, p);
                    break;

                case GRIP:
                    // Tres rayitas en diagonal, esquina inferior derecha (para cambiar el tamaño)
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(.06f * s);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(cx + .3f * s, cy - .3f * s, cx - .3f * s, cy + .3f * s, p);
                    cv.drawLine(cx + .3f * s, cy - .1f * s, cx - .1f * s, cy + .3f * s, p);
                    cv.drawLine(cx + .3f * s, cy + .1f * s, cx + .1f * s, cy + .3f * s, p);
                    break;

                case PAD:
                    // Cuerpo + agarraderas
                    rect.set(cx - .32f * s, cy - .18f * s, cx + .32f * s, cy + .12f * s);
                    cv.drawRoundRect(rect, .14f * s, .14f * s, p);
                    cv.drawCircle(cx - .21f * s, cy + .12f * s, .11f * s, p);
                    cv.drawCircle(cx + .21f * s, cy + .12f * s, .11f * s, p);
                    // Cruceta y botones
                    p.setColor(accent);
                    cv.drawRect(cx - .24f * s, cy - .075f * s, cx - .08f * s, cy - .025f * s, p);
                    cv.drawRect(cx - .18f * s, cy - .13f * s, cx - .14f * s, cy + .03f * s, p);
                    cv.drawCircle(cx + .12f * s, cy - .1f * s, .04f * s, p);
                    cv.drawCircle(cx + .2f * s, cy - .03f * s, .04f * s, p);
                    break;

                default:
                    break;
            }
        }
    }
}
