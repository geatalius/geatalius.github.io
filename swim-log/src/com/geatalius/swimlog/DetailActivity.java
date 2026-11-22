package com.geatalius.swimlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.util.Locale;

/** 세션 상세: 요약, 심박 그래프·구간, 랩 타임 그래프·표. */
public class DetailActivity extends Activity {
    private Ui ui;
    private long id;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getLongExtra("id", 0);
        ui = new Ui(this);
        ui.styleWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Session s = Db.get(this).load(id);
        if (s == null) {
            finish();
            return;
        }
        render(s);
    }

    private void render(final Session s) {
        LinearLayout root = ui.v();
        root.setBackgroundColor(ui.bg);
        ImageView edit = ui.iconButton(R.drawable.ic_edit, ui.fg);
        edit.setContentDescription("수정");
        edit.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class).putExtra("id", s.id)));
        ImageView del = ui.iconButton(R.drawable.ic_delete, ui.fg);
        del.setContentDescription("삭제");
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("기록 삭제")
                .setMessage("이 수영 기록을 삭제할까요? 되돌릴 수 없어요.")
                .setPositiveButton("삭제", (d, w) -> {
                    Db.get(this).delete(s.id);
                    finish();
                })
                .setNegativeButton("취소", null)
                .show());
        root.addView(ui.topBar(this, Fmt.dateShort(s.startMs), edit, del));

        ScrollView sv = new ScrollView(this);
        LinearLayout b = ui.v();
        b.setPadding(0, 0, 0, ui.dp(32));
        sv.addView(b);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        summary(b, s);
        heart(b, s);
        lapsChart(b, s);
        lapsTable(b, s);
        if (s.memo != null && s.memo.trim().length() > 0) {
            LinearLayout mc = ui.card();
            mc.addView(ui.cardTitle("메모"));
            mc.addView(ui.text(s.memo, 15, ui.fg, false));
            b.addView(mc);
        }
        setContentView(root);
    }

    private void summary(LinearLayout b, Session s) {
        LinearLayout c = ui.card();
        long end = s.startMs + s.durationS * 1000L;
        c.addView(ui.text(Fmt.dateFull(s.startMs) + "  " + Fmt.time(s.startMs) + " ~ " + Fmt.time(end), 13, ui.muted, false));
        LinearLayout big = ui.h();
        big.setGravity(Gravity.BOTTOM);
        big.setPadding(0, ui.dp(6), 0, ui.dp(14));
        big.addView(ui.text(Fmt.num(s.distanceM), 40, ui.accent, true));
        TextView m = ui.text(" m", 18, ui.muted, true);
        m.setPadding(0, 0, 0, ui.dp(8));
        big.addView(m);
        c.addView(big);
        c.addView(ui.statRow(
                ui.stat("총 시간", Fmt.dur(s.durationS), ""),
                ui.stat("수영 시간", Fmt.dur(s.swimMs() / 1000), ""),
                ui.stat("평균 페이스", Fmt.pace(s.pace100()), "/100m")));
        c.addView(ui.space(14));
        c.addView(ui.statRow(
                ui.stat("평균 심박", s.avgHr > 0 ? String.valueOf(s.avgHr) : "-", "bpm", ui.hr),
                ui.stat("최대 심박", s.maxHr > 0 ? String.valueOf(s.maxHr) : "-", "bpm", ui.hr),
                ui.stat("칼로리", s.kcal > 0 ? Fmt.num(s.kcal) : "-", "kcal")));
        c.addView(ui.divider());
        LinearLayout tags = ui.h();
        tags.addView(ui.chip("수영장 " + s.poolLen + "m", ui.accent, ui.accentSoft));
        tags.addView(ui.space(0), ui.margins(ui.dp(6), 1, 0, 0, 0, 0));
        tags.addView(ui.chip(s.laps.size() + "랩", ui.accent, ui.accentSoft));
        tags.addView(ui.space(0), ui.margins(ui.dp(6), 1, 0, 0, 0, 0));
        tags.addView(ui.chip(s.sourceLabel(), ui.muted, ui.bg));
        c.addView(tags);
        b.addView(c);
    }

    private void heart(LinearLayout b, Session s) {
        boolean lapHr = false;
        for (Lap l : s.laps) if (l.hr > 0) lapHr = true;
        if (s.hrT.length < 2 && !lapHr) return;

        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("심박수"));
        if (s.hrT.length >= 2) {
            int n = s.hrT.length;
            double[] xs = new double[n], ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = s.hrT[i];
                ys[i] = s.hrBpm[i];
            }
            c.addView(new ChartView(this, ui).line(ys, xs, null).color(ui.hr).height(180)
                    .xFormat(v -> Fmt.dur(Math.round(v))));
            zones(c, s);
        } else {
            int n = s.laps.size();
            double[] ys = new double[n];
            String[] ls = new String[n];
            for (int i = 0; i < n; i++) {
                ys[i] = s.laps.get(i).hr;
                ls[i] = String.valueOf(i + 1);
            }
            c.addView(ui.text("랩별 평균 심박수", 12, ui.muted, false));
            c.addView(new ChartView(this, ui).bars(ys, ls).color(ui.hr).values(n <= 12));
        }
        b.addView(c);
    }

    /** 최대 심박 대비 5구간 시간 분포. */
    private void zones(LinearLayout c, Session s) {
        int max = MainActivity.maxHr(this);
        String[] names = {"Z1 회복", "Z2 지방연소", "Z3 유산소", "Z4 무산소", "Z5 최대"};
        int[] cols = {0xFF8E9AAF, 0xFF22B8CF, 0xFF2FB344, 0xFFFF922B, 0xFFE5484D};
        double[] secs = new double[5];
        double total = 0;
        int n = s.hrT.length;
        for (int i = 0; i < n; i++) {
            double dt = i + 1 < n ? Math.min(30, s.hrT[i + 1] - s.hrT[i]) : 5;
            if (dt <= 0) continue;
            double pct = s.hrBpm[i] * 100.0 / max;
            int z = pct < 60 ? 0 : pct < 70 ? 1 : pct < 80 ? 2 : pct < 90 ? 3 : 4;
            secs[z] += dt;
            total += dt;
        }
        if (total <= 0) return;
        c.addView(ui.space(10));
        TextView t = ui.text("심박 구간 (최대 " + max + "bpm 기준)", 13, ui.fg, true);
        t.setPadding(0, 0, 0, ui.dp(6));
        c.addView(t);
        int[] lo = {0, 60, 70, 80, 90};
        for (int z = 4; z >= 0; z--) {
            LinearLayout r = ui.h();
            r.setPadding(0, ui.dp(3), 0, ui.dp(3));
            TextView nm = ui.text(names[z], 12, ui.fg, false);
            r.addView(nm, Ui.lp(ui.dp(84), Ui.WRAP));
            LinearLayout track = ui.h();
            track.setBackground(Ui.round(ui.bg, ui.dp(5)));
            track.setClipToOutline(true);
            View fill = new View(this);
            fill.setBackground(Ui.round(cols[z], ui.dp(5)));
            float w = (float) (secs[z] / total);
            track.addView(fill, new LinearLayout.LayoutParams(0, ui.dp(10), w));
            View rest = new View(this);
            track.addView(rest, new LinearLayout.LayoutParams(0, ui.dp(10), 1 - w));
            track.setWeightSum(1);
            r.addView(track, Ui.weight(1));
            String bpm = (z == 0 ? "~" + Math.round(max * 0.6) : Math.round(max * lo[z] / 100.0) + "+");
            TextView v = ui.text(String.format(Locale.US, "%d%%", Math.round(secs[z] * 100 / total)) + " · " + Fmt.dur(Math.round(secs[z])),
                    12, ui.muted, false);
            v.setGravity(Gravity.END);
            r.addView(v, Ui.lp(ui.dp(84), Ui.WRAP));
            r.setContentDescription(names[z] + " " + bpm);
            c.addView(r);
        }
    }

    private boolean sameDistance(Session s) {
        if (s.laps.isEmpty()) return true;
        int d = s.laps.get(0).distanceM;
        for (Lap l : s.laps) if (l.distanceM != d) return false;
        return true;
    }

    private void lapsChart(LinearLayout b, Session s) {
        if (s.laps.isEmpty()) return;
        boolean same = sameDistance(s);
        int n = s.laps.size();
        double[] ys = new double[n];
        String[] ls = new String[n];
        int[] cols = new int[n];
        int fast = 0, slow = 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            Lap l = s.laps.get(i);
            ys[i] = same ? l.timeMs / 1000.0 : l.pace100();
            ls[i] = String.valueOf(i + 1);
            cols[i] = Models.strokeColor(l.stroke);
            sum += ys[i];
            if (ys[i] < ys[fast]) fast = i;
            if (ys[i] > ys[slow]) slow = i;
        }
        double avg = sum / n, var = 0;
        for (double y : ys) var += (y - avg) * (y - avg);
        double sd = Math.sqrt(var / n);

        LinearLayout c = ui.card();
        c.addView(ui.cardTitle(same ? "랩 타임 (" + s.laps.get(0).distanceM + "m)" : "랩 페이스 (/100m)"));
        ChartView.F f = same ? (v -> Fmt.lap(Math.round(v * 1000))) : Fmt::pace;
        c.addView(new ChartView(this, ui).bars(ys, ls).colors(cols).yFormat(f).height(180));
        c.addView(ui.space(12));
        c.addView(ui.statRow(
                ui.stat("최고 랩", f.f(ys[fast]), "#" + (fast + 1), ui.good),
                ui.stat("평균", f.f(avg), ""),
                ui.stat("최저 랩", f.f(ys[slow]), "#" + (slow + 1))));
        TextView cons = ui.text("편차 ±" + String.format(Locale.US, "%.1f", sd) + "초 · "
                + (sd < avg * 0.03 ? "아주 일정한 페이스예요 👍" : sd < avg * 0.07 ? "비교적 일정한 페이스예요" : "랩마다 페이스 차이가 커요"),
                12, ui.muted, false);
        cons.setPadding(0, ui.dp(10), 0, 0);
        c.addView(cons);
        // 범례
        boolean[] used = new boolean[Models.STROKES.length];
        for (Lap l : s.laps) used[Math.max(0, Math.min(used.length - 1, l.stroke))] = true;
        LinearLayout lg = ui.h();
        lg.setPadding(0, ui.dp(8), 0, 0);
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) continue;
            View dot = new View(this);
            dot.setBackground(Ui.round(Models.strokeColor(i), ui.dp(4)));
            lg.addView(dot, ui.margins(ui.dp(8), ui.dp(8), 0, 0, 4, 0));
            lg.addView(ui.text(Models.STROKES[i] + "   ", 12, ui.muted, false));
        }
        c.addView(lg);
        b.addView(c);
    }

    private void lapsTable(LinearLayout b, Session s) {
        if (s.laps.isEmpty()) return;
        LinearLayout c = ui.card();
        c.setPadding(ui.dp(12), ui.dp(16), ui.dp(12), ui.dp(12));
        TextView t = ui.cardTitle("랩 상세");
        t.setPadding(ui.dp(4), 0, 0, ui.dp(10));
        c.addView(t);
        float[] w = {0.7f, 1.2f, 1f, 1.3f, 1.2f, 0.9f, 0.9f};
        c.addView(row(new String[]{"#", "영법", "거리", "시간", "페이스", "심박", "스트록"}, w, ui.muted, false, 0));
        long best = Long.MAX_VALUE;
        boolean same = sameDistance(s);
        for (Lap l : s.laps) best = Math.min(best, same ? l.timeMs : Math.round(l.pace100() * 1000));
        int cum = 0;
        for (int i = 0; i < s.laps.size(); i++) {
            Lap l = s.laps.get(i);
            cum += l.distanceM;
            boolean isBest = (same ? l.timeMs : Math.round(l.pace100() * 1000)) == best;
            View r = row(new String[]{
                    String.valueOf(i + 1),
                    Models.strokeName(l.stroke),
                    l.distanceM + "m",
                    Fmt.lap(l.timeMs),
                    Fmt.pace(l.pace100()),
                    l.hr > 0 ? String.valueOf(l.hr) : "-",
                    l.strokes > 0 ? String.valueOf(l.strokes) : "-"
            }, w, isBest ? ui.good : ui.fg, isBest, i % 2 == 0 ? ui.bg : 0);
            r.setContentDescription((i + 1) + "번째 랩, 누적 " + cum + "m");
            c.addView(r);
            if (l.restS > 0) {
                TextView rest = ui.text("휴식 " + l.restS + "초", 11, ui.muted, false);
                rest.setPadding(ui.dp(8), 0, 0, ui.dp(2));
                rest.setGravity(Gravity.CENTER_HORIZONTAL);
                c.addView(rest, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
        }
        TextView note = ui.text("초록색은 가장 빠른 랩이에요", 11, ui.muted, false);
        note.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        c.addView(note);
        b.addView(c);
    }

    private View row(String[] cells, float[] w, int color, boolean bold, int bg) {
        LinearLayout r = ui.h();
        r.setPadding(ui.dp(4), ui.dp(7), ui.dp(4), ui.dp(7));
        if (bg != 0) r.setBackground(Ui.round(bg, ui.dp(8)));
        for (int i = 0; i < cells.length; i++) {
            TextView t = ui.text(cells[i], 12.5f, color, bold);
            t.setGravity(i == 0 ? Gravity.START : Gravity.CENTER);
            t.setSingleLine(true);
            r.addView(t, Ui.weight(w[i]));
        }
        return r;
    }
}
