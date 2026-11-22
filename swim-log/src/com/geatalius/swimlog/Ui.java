package com.geatalius.swimlog;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 코드로 화면을 구성하기 위한 작은 UI 헬퍼. */
final class Ui {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    final Context c;
    final int bg, surface, fg, muted, line, accent, accentSoft, hr, hrSoft, good;
    final boolean night;

    Ui(Context c) {
        this.c = c;
        bg = c.getColor(R.color.bg);
        surface = c.getColor(R.color.surface);
        fg = c.getColor(R.color.fg);
        muted = c.getColor(R.color.muted);
        line = c.getColor(R.color.line);
        accent = c.getColor(R.color.accent);
        accentSoft = c.getColor(R.color.accent_soft);
        hr = c.getColor(R.color.hr);
        hrSoft = c.getColor(R.color.hr_soft);
        good = c.getColor(R.color.good);
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 상태바/내비게이션바 색 맞추기. */
    void styleWindow(Activity a) {
        a.getWindow().setStatusBarColor(bg);
        a.getWindow().setNavigationBarColor(surface);
        if (!night) {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    int dp(float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    GradientDrawable outline(int fill, int stroke, float radius) {
        GradientDrawable g = round(fill, radius);
        g.setStroke(dp(1), stroke);
        return g;
    }

    void clickable(View v, float radius) {
        v.setClickable(true);
        v.setFocusable(true);
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(night ? 0x33FFFFFF : 0x1F0A7CFF), null,
                round(0xFFFFFFFF, radius)));
    }

    TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    LinearLayout v() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout h() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, WRAP, w);
    }

    LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    LinearLayout card() {
        LinearLayout l = v();
        l.setBackground(round(surface, dp(20)));
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setElevation(dp(night ? 0 : 1));
        l.setLayoutParams(margins(MATCH, WRAP, 16, 6, 16, 6));
        return l;
    }

    TextView cardTitle(String s) {
        TextView t = text(s, 16, fg, true);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    /** 라벨 / 값 / 단위 묶음. */
    LinearLayout stat(String label, String value, String unit, int valueColor) {
        LinearLayout box = v();
        box.addView(text(label, 12, muted, false));
        LinearLayout row = h();
        row.setGravity(Gravity.BOTTOM);
        TextView val = text(value, 22, valueColor, true);
        row.addView(val);
        if (unit != null && unit.length() > 0) {
            TextView u = text(" " + unit, 12, muted, false);
            u.setPadding(0, 0, 0, dp(3));
            row.addView(u);
        }
        box.addView(row);
        box.setLayoutParams(weight(1));
        return box;
    }

    LinearLayout stat(String label, String value, String unit) {
        return stat(label, value, unit, fg);
    }

    LinearLayout statRow(View... stats) {
        LinearLayout r = h();
        r.setGravity(Gravity.TOP);
        for (View v : stats) r.addView(v);
        return r;
    }

    View space(int dpH) {
        View v = new View(c);
        v.setLayoutParams(lp(MATCH, dp(dpH)));
        return v;
    }

    View divider() {
        View v = new View(c);
        v.setBackgroundColor(line);
        LinearLayout.LayoutParams p = margins(MATCH, WRAP, 0, 10, 0, 10);
        p.height = Math.max(1, dp(1));
        v.setLayoutParams(p);
        return v;
    }

    ImageView icon(int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** 원형 터치 영역을 가진 아이콘 버튼. */
    ImageView iconButton(int res, int color) {
        ImageView iv = icon(res, color, 44);
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        clickable(iv, dp(22));
        return iv;
    }

    Button button(String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setTextColor(primary ? 0xFFFFFFFF : accent);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                round(primary ? accent : accentSoft, dp(14)), null));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(48));
        b.setPadding(dp(16), 0, dp(16), 0);
        return b;
    }

    EditText field(String hint, boolean number, boolean decimal) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(fg);
        e.setHintTextColor(muted);
        e.setSingleLine(true);
        e.setBackground(outline(bg, line, dp(12)));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (number) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        }
        return e;
    }

    /** "라벨" + 입력창 세로 묶음. */
    LinearLayout labeled(String label, View field) {
        LinearLayout box = v();
        TextView t = text(label, 12, muted, false);
        t.setPadding(dp(2), 0, 0, dp(4));
        box.addView(t);
        box.addView(field, lp(MATCH, WRAP));
        return box;
    }

    /** 좁은 칩 형태 라벨. */
    TextView chip(String s, int fgColor, int bgColor) {
        TextView t = text(s, 11, fgColor, true);
        t.setBackground(round(bgColor, dp(10)));
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        return t;
    }

    /** 상단 바: 뒤로가기 + 제목 + 액션들. */
    LinearLayout topBar(final Activity a, String title, View... actions) {
        LinearLayout bar = h();
        bar.setPadding(dp(4), dp(6), dp(8), dp(6));
        ImageView back = iconButton(R.drawable.ic_back, fg);
        back.setOnClickListener(v -> a.finish());
        bar.addView(back);
        TextView t = text(title, 18, fg, true);
        t.setPadding(dp(4), 0, 0, 0);
        t.setSingleLine(true);
        bar.addView(t, weight(1));
        for (View v : actions) bar.addView(v);
        return bar;
    }
}
