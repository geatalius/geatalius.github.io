package com.geatalius.swimlog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** 막대 / 선 그래프를 그리는 가벼운 차트 뷰. */
final class ChartView extends View {
    interface F {
        String f(double v);
    }

    static final int BAR = 0, LINE = 1;

    private final Ui ui;
    private int mode = BAR;
    private double[] ys = new double[0];
    private double[] xs;
    private String[] labels;
    private int[] colors;
    private int color;
    private F yf = new F() {
        public String f(double v) {
            return String.valueOf(Math.round(v));
        }
    };
    private F xf;
    private boolean showValues;
    private boolean zeroBase = true;
    private int heightDp = 170;
    private int highlight = -1;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    ChartView(Context c, Ui ui) {
        super(c);
        this.ui = ui;
        this.color = ui.accent;
    }

    ChartView bars(double[] ys, String[] labels) {
        this.mode = BAR;
        this.ys = ys;
        this.labels = labels;
        return this;
    }

    ChartView line(double[] ys, double[] xs, String[] labels) {
        this.mode = LINE;
        this.ys = ys;
        this.xs = xs;
        this.labels = labels;
        this.zeroBase = false;
        return this;
    }

    ChartView colors(int[] c) { this.colors = c; return this; }
    ChartView color(int c) { this.color = c; return this; }
    ChartView yFormat(F f) { this.yf = f; return this; }
    ChartView xFormat(F f) { this.xf = f; return this; }
    ChartView values(boolean b) { this.showValues = b; return this; }
    ChartView zeroBase(boolean b) { this.zeroBase = b; return this; }
    ChartView height(int dp) { this.heightDp = dp; return this; }
    ChartView highlight(int i) { this.highlight = i; return this; }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), ui.dp(heightDp));
    }

    @Override
    protected void onDraw(Canvas cv) {
        int n = ys.length;
        float W = getWidth(), H = getHeight();
        tp.setTextSize(ui.sp(10));
        tp.setColor(ui.muted);
        if (n == 0) {
            tp.setTextAlign(Paint.Align.CENTER);
            cv.drawText("데이터가 없어요", W / 2, H / 2, tp);
            return;
        }
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double y : ys) {
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (mode == BAR || zeroBase) {
            min = Math.min(0, min);
            max = max <= 0 ? 1 : max * 1.15;
        } else {
            double pad = (max - min) * 0.15;
            if (pad <= 0) pad = Math.max(1, Math.abs(max) * 0.08);
            min -= pad;
            max += pad;
        }
        final double lo = min, hi = max;

        // Y축 라벨 폭
        String[] yl = new String[4];
        float lw = 0;
        for (int k = 0; k < 4; k++) {
            yl[k] = yf.f(lo + (hi - lo) * k / 3.0);
            lw = Math.max(lw, tp.measureText(yl[k]));
        }
        float left = lw + ui.dp(8), right = W - ui.dp(6), top = ui.dp(16), bottom = H - ui.dp(22);
        float pw = right - left, ph = bottom - top;

        // 그리드
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, ui.dp(1) / 1.5f));
        p.setColor(ui.line);
        tp.setTextAlign(Paint.Align.RIGHT);
        for (int k = 0; k < 4; k++) {
            float y = bottom - ph * k / 3f;
            cv.drawLine(left, y, right, y, p);
            cv.drawText(yl[k], left - ui.dp(6), y + ui.sp(3.5f), tp);
        }

        tp.setTextAlign(Paint.Align.CENTER);
        int step = Math.max(1, (int) Math.ceil(n / 7.0));

        if (mode == BAR) {
            float slot = pw / n;
            float bw = Math.max(ui.dp(2), Math.min(slot * 0.62f, ui.dp(28)));
            float r = Math.min(bw / 2, ui.dp(5));
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float cx = left + slot * (i + 0.5f);
                float y = (float) (bottom - (ys[i] - lo) / (hi - lo) * ph);
                int col = colors != null && i < colors.length ? colors[i] : color;
                if (highlight >= 0 && i != highlight && colors == null) col = blend(col, ui.surface, 0.55f);
                p.setColor(col);
                rect.set(cx - bw / 2, Math.min(y, bottom - 1), cx + bw / 2, bottom);
                cv.drawRoundRect(rect, r, r, p);
                cv.drawRect(cx - bw / 2, Math.min(bottom - 1, rect.top + r), cx + bw / 2, bottom, p);
                if (showValues && n <= 12 && ys[i] > 0) {
                    tp.setColor(ui.fg);
                    cv.drawText(yf.f(ys[i]), cx, y - ui.dp(4), tp);
                    tp.setColor(ui.muted);
                }
                if (labels != null && i < labels.length && (n - 1 - i) % step == 0) {
                    cv.drawText(labels[i], cx, H - ui.dp(6), tp);
                }
            }
            return;
        }

        // 선 그래프
        double x0, x1;
        if (xs != null && xs.length == n) {
            x0 = xs[0];
            x1 = xs[n - 1];
        } else {
            x0 = 0;
            x1 = n - 1;
        }
        if (x1 <= x0) x1 = x0 + 1;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double xv = xs != null && xs.length == n ? xs[i] : i;
            px[i] = n == 1 ? left + pw / 2 : (float) (left + (xv - x0) / (x1 - x0) * pw);
            py[i] = (float) (bottom - (ys[i] - lo) / (hi - lo) * ph);
        }
        path.reset();
        path.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) path.lineTo(px[i], py[i]);
        // 채우기
        Path fill = new Path(path);
        fill.lineTo(px[n - 1], bottom);
        fill.lineTo(px[0], bottom);
        fill.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, top, 0, bottom, (color & 0x00FFFFFF) | 0x55000000,
                color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawPath(fill, p);
        p.setShader(null);
        // 선
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(ui.dp(2.2f));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        cv.drawPath(path, p);
        // 점
        p.setStyle(Paint.Style.FILL);
        if (n <= 30) {
            for (int i = 0; i < n; i++) {
                p.setColor(ui.surface);
                cv.drawCircle(px[i], py[i], ui.dp(3.5f), p);
                p.setColor(color);
                cv.drawCircle(px[i], py[i], ui.dp(2.3f), p);
            }
        } else {
            p.setColor(color);
            cv.drawCircle(px[n - 1], py[n - 1], ui.dp(3.5f), p);
        }
        if (showValues && n > 0) {
            tp.setColor(ui.fg);
            cv.drawText(yf.f(ys[n - 1]), Math.min(px[n - 1], right - ui.dp(14)), py[n - 1] - ui.dp(7), tp);
            tp.setColor(ui.muted);
        }
        // X 라벨
        if (xf != null) {
            for (int k = 0; k <= 4; k++) {
                float x = left + pw * k / 4f;
                tp.setTextAlign(k == 0 ? Paint.Align.LEFT : k == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                cv.drawText(xf.f(x0 + (x1 - x0) * k / 4.0), x, H - ui.dp(6), tp);
            }
        } else if (labels != null) {
            for (int i = 0; i < n && i < labels.length; i += step) {
                cv.drawText(labels[i], px[i], H - ui.dp(6), tp);
            }
        }
    }
}
