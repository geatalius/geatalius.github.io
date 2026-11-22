package com.geatalius.swimlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final int REQ_HC = 41, REQ_CSV = 42, REQ_BACKUP = 43, REQ_RESTORE = 44;

    private Ui ui;
    private FrameLayout content;
    private TextView title;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private int tab = 0;
    private List<Session> sessions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) tab = state.getInt("tab", 0);
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void build() {
        ui = new Ui(this);
        ui.styleWindow(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.bg);

        LinearLayout col = ui.v();
        // 헤더
        LinearLayout header = ui.h();
        header.setPadding(ui.dp(20), ui.dp(12), ui.dp(8), ui.dp(4));
        title = ui.text("", 24, ui.fg, true);
        header.addView(title, Ui.weight(1));
        final ImageView more = ui.iconButton(R.drawable.ic_more, ui.fg);
        more.setOnClickListener(v -> showMenu(more));
        header.addView(more);
        col.addView(header);

        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        // 하단 탭
        LinearLayout nav = ui.h();
        nav.setBackgroundColor(ui.surface);
        nav.setElevation(ui.dp(8));
        nav.setPadding(0, ui.dp(6), 0, ui.dp(6));
        String[] names = {"홈", "기록", "통계"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_list, R.drawable.ic_chart};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout t = ui.v();
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, ui.dp(4), 0, ui.dp(4));
            t.addView(ui.icon(icons[i], ui.muted, 24));
            TextView label = ui.text(names[i], 12, ui.muted, true);
            label.setGravity(Gravity.CENTER);
            t.addView(label);
            ui.clickable(t, ui.dp(16));
            t.setOnClickListener(v -> {
                tab = idx;
                render();
            });
            tabs[i] = t;
            nav.addView(t, Ui.weight(1));
        }
        col.addView(nav);
        root.addView(col);

        // 추가 버튼
        ImageView fab = ui.icon(R.drawable.ic_add, 0xFFFFFFFF, 58);
        fab.setPadding(ui.dp(15), ui.dp(15), ui.dp(15), ui.dp(15));
        fab.setBackground(Ui.round(ui.accent, ui.dp(29)));
        fab.setElevation(ui.dp(6));
        ui.clickable(fab, ui.dp(29));
        fab.setContentDescription("기록 추가");
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ui.dp(58), ui.dp(58), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, ui.dp(20), ui.dp(86));
        root.addView(fab, fp);

        setContentView(root);
    }

    private void reload() {
        sessions = Db.get(this).all();
        render();
    }

    private void render() {
        String[] titles = {getString(R.string.app_name), "수영 기록", "통계"};
        title.setText(titles[tab]);
        for (int i = 0; i < 3; i++) {
            int c = i == tab ? ui.accent : ui.muted;
            ((ImageView) tabs[i].getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(c));
            ((TextView) tabs[i].getChildAt(1)).setTextColor(c);
        }
        content.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = ui.v();
        body.setPadding(0, ui.dp(4), 0, ui.dp(100));
        if (sessions.isEmpty()) {
            empty(body);
        } else if (tab == 0) {
            home(body);
        } else if (tab == 1) {
            history(body);
        } else {
            stats(body);
        }
        sv.addView(body);
        content.addView(sv);
    }

    // ───────────────────────── 빈 화면 ─────────────────────────

    private void empty(LinearLayout b) {
        LinearLayout c = ui.card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(24));
        TextView wave = ui.text("🏊", 44, ui.fg, false);
        c.addView(wave);
        TextView t = ui.text("아직 수영 기록이 없어요", 18, ui.fg, true);
        t.setPadding(0, ui.dp(8), 0, ui.dp(6));
        c.addView(t);
        TextView d = ui.text("랩 타임과 심박수를 직접 입력하거나,\n갤럭시 워치 등 웨어러블 기록을 Health Connect에서 가져올 수 있어요.",
                14, ui.muted, false);
        d.setGravity(Gravity.CENTER);
        c.addView(d);
        c.addView(ui.space(18));
        android.widget.Button add = ui.button("기록 직접 추가", true);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        c.addView(add, Ui.lp(Ui.MATCH, Ui.WRAP));
        c.addView(ui.space(8));
        android.widget.Button hc = ui.button("Health Connect에서 가져오기", false);
        hc.setOnClickListener(v -> startHealthImport());
        c.addView(hc, Ui.lp(Ui.MATCH, Ui.WRAP));
        c.addView(ui.space(8));
        android.widget.Button sample = ui.button("샘플 데이터로 둘러보기", false);
        sample.setOnClickListener(v -> addSamples());
        c.addView(sample, Ui.lp(Ui.MATCH, Ui.WRAP));
        b.addView(c);
    }

    // ───────────────────────── 홈 ─────────────────────────

    private void home(LinearLayout b) {
        long now = System.currentTimeMillis();
        long ws = Fmt.weekStart(now), lws = ws - 7L * 86400000L;
        int wDist = 0, wCount = 0, lwDist = 0;
        long wTime = 0;
        for (Session s : sessions) {
            if (s.startMs >= ws) {
                wDist += s.distanceM;
                wCount++;
                wTime += s.durationS;
            } else if (s.startMs >= lws) {
                lwDist += s.distanceM;
            }
        }
        TextView date = ui.text(Fmt.dateFull(now), 13, ui.muted, false);
        date.setPadding(ui.dp(20), 0, 0, ui.dp(8));
        b.addView(date);

        // 이번 주
        LinearLayout wk = ui.card();
        wk.addView(ui.cardTitle("이번 주"));
        wk.addView(ui.statRow(
                ui.stat("거리", Fmt.num(wDist), "m", ui.accent),
                ui.stat("횟수", String.valueOf(wCount), "회"),
                ui.stat("시간", Fmt.durKo(wTime), "")));
        int diff = wDist - lwDist;
        String cmp = lwDist == 0 && wDist == 0 ? "이번 주 첫 수영을 기록해보세요"
                : lwDist == 0 ? "지난주 기록 없음"
                : diff >= 0 ? "지난주보다 " + Fmt.num(diff) + "m 더 수영했어요 👏"
                : "지난주보다 " + Fmt.num(-diff) + "m 적어요";
        TextView ct = ui.text(cmp, 13, diff >= 0 && lwDist > 0 ? ui.good : ui.muted, false);
        ct.setPadding(0, ui.dp(10), 0, 0);
        wk.addView(ct);
        b.addView(wk);

        // 최근 8주 거리
        LinearLayout ch = ui.card();
        ch.addView(ui.cardTitle("주간 거리 (최근 8주)"));
        double[] ys = new double[8];
        String[] ls = new String[8];
        for (int i = 0; i < 8; i++) {
            long st = ws - (7L - i) * 7L * 86400000L;
            long en = st + 7L * 86400000L;
            ls[i] = Fmt.md(st);
            for (Session s : sessions) if (s.startMs >= st && s.startMs < en) ys[i] += s.distanceM;
        }
        ch.addView(new ChartView(this, ui).bars(ys, ls).highlight(7).values(true)
                .yFormat(v -> v >= 1000 ? String.format(java.util.Locale.US, "%.1fk", v / 1000) : String.valueOf(Math.round(v))));
        b.addView(ch);

        // 최근 수영
        LinearLayout rc = ui.card();
        rc.addView(ui.cardTitle("최근 수영"));
        int shown = 0;
        for (Session s : sessions) {
            if (shown > 0) rc.addView(ui.divider());
            rc.addView(sessionRow(s));
            if (++shown >= 3) break;
        }
        if (sessions.size() > 3) {
            TextView all = ui.text("전체 기록 보기 →", 14, ui.accent, true);
            all.setPadding(0, ui.dp(14), 0, ui.dp(2));
            all.setOnClickListener(v -> {
                tab = 1;
                render();
            });
            rc.addView(all);
        }
        b.addView(rc);

        // 누적
        int tDist = 0;
        long tTime = 0;
        double bestPace = 0;
        int longest = 0;
        for (Session s : sessions) {
            tDist += s.distanceM;
            tTime += s.durationS;
            longest = Math.max(longest, s.distanceM);
            double p = s.pace100();
            if (p > 0 && s.distanceM >= 200 && (bestPace == 0 || p < bestPace)) bestPace = p;
        }
        LinearLayout tot = ui.card();
        tot.addView(ui.cardTitle("누적 기록"));
        tot.addView(ui.statRow(
                ui.stat("총 거리", Fmt.km(tDist), "km", ui.accent),
                ui.stat("총 횟수", String.valueOf(sessions.size()), "회"),
                ui.stat("총 시간", String.valueOf(Math.round(tTime / 3600.0 * 10) / 10.0), "시간")));
        tot.addView(ui.space(14));
        tot.addView(ui.statRow(
                ui.stat("최고 평균 페이스", Fmt.pace(bestPace), "/100m"),
                ui.stat("최장 거리", Fmt.num(longest), "m")));
        b.addView(tot);
    }

    /** 세션 요약 한 줄. */
    private View sessionRow(final Session s) {
        LinearLayout row = ui.h();
        row.setPadding(0, ui.dp(4), 0, ui.dp(4));

        LinearLayout dbox = ui.v();
        dbox.setGravity(Gravity.CENTER);
        dbox.setBackground(Ui.round(ui.accentSoft, ui.dp(14)));
        TextView day = ui.text(String.valueOf(Fmt.cal(s.startMs).get(java.util.Calendar.DAY_OF_MONTH)), 18, ui.accent, true);
        day.setGravity(Gravity.CENTER);
        dbox.addView(day);
        TextView wd = ui.text(Fmt.weekday(s.startMs), 11, ui.accent, false);
        wd.setGravity(Gravity.CENTER);
        dbox.addView(wd);
        row.addView(dbox, Ui.lp(ui.dp(50), ui.dp(54)));

        LinearLayout mid = ui.v();
        mid.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        LinearLayout top = ui.h();
        top.addView(ui.text(Fmt.num(s.distanceM) + "m", 17, ui.fg, true));
        TextView du = ui.text("  ·  " + Fmt.dur(s.durationS), 15, ui.fg, false);
        top.addView(du);
        mid.addView(top);
        StringBuilder sub = new StringBuilder();
        sub.append(Fmt.time(s.startMs)).append(" · ").append(Fmt.pace(s.pace100())).append("/100m");
        if (!s.laps.isEmpty()) sub.append(" · ").append(s.laps.size()).append("랩");
        mid.addView(ui.text(sub.toString(), 13, ui.muted, false));
        row.addView(mid, Ui.weight(1));

        if (s.avgHr > 0) {
            LinearLayout hrb = ui.h();
            hrb.addView(ui.icon(R.drawable.ic_heart, ui.hr, 16));
            TextView hv = ui.text(" " + s.avgHr, 15, ui.hr, true);
            hrb.addView(hv);
            row.addView(hrb);
        }
        ui.clickable(row, ui.dp(12));
        row.setOnClickListener(v -> openDetail(s.id));
        return row;
    }

    private void openDetail(long id) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("id", id));
    }

    // ───────────────────────── 기록 ─────────────────────────

    private void history(LinearLayout b) {
        long curMonth = -1;
        LinearLayout card = null;
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            long m = Fmt.monthStart(s.startMs);
            if (m != curMonth) {
                curMonth = m;
                int mDist = 0, mCount = 0;
                for (Session o : sessions) {
                    if (Fmt.monthStart(o.startMs) == m) {
                        mDist += o.distanceM;
                        mCount++;
                    }
                }
                LinearLayout hdr = ui.h();
                hdr.setPadding(ui.dp(22), ui.dp(14), ui.dp(22), ui.dp(4));
                hdr.addView(ui.text(Fmt.ym(m), 15, ui.fg, true), Ui.weight(1));
                hdr.addView(ui.text(mCount + "회 · " + Fmt.dist(mDist), 13, ui.muted, false));
                b.addView(hdr);
                card = ui.card();
                card.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
                b.addView(card);
            } else if (card != null) {
                card.addView(ui.divider());
            }
            if (card != null) card.addView(sessionRow(s));
        }
    }

    // ───────────────────────── 통계 ─────────────────────────

    private void stats(LinearLayout b) {
        List<Session> chrono = new ArrayList<>(sessions);
        Collections.reverse(chrono);

        // 페이스 추이
        List<Session> paced = new ArrayList<>();
        for (Session s : chrono) if (s.pace100() > 0) paced.add(s);
        if (paced.size() > 20) paced = paced.subList(paced.size() - 20, paced.size());
        LinearLayout pc = ui.card();
        pc.addView(ui.cardTitle("평균 페이스 추이 (/100m)"));
        double[] py = new double[paced.size()];
        String[] pl = new String[paced.size()];
        for (int i = 0; i < paced.size(); i++) {
            py[i] = paced.get(i).pace100();
            pl[i] = Fmt.md(paced.get(i).startMs);
        }
        pc.addView(new ChartView(this, ui).line(py, null, pl).yFormat(Fmt::pace).values(true));
        TextView pn = ui.text("값이 낮을수록 빠른 페이스예요", 12, ui.muted, false);
        pn.setPadding(0, ui.dp(6), 0, 0);
        pc.addView(pn);
        b.addView(pc);

        // 심박 추이
        List<Session> hrs = new ArrayList<>();
        for (Session s : chrono) if (s.avgHr > 0) hrs.add(s);
        if (hrs.size() > 20) hrs = hrs.subList(hrs.size() - 20, hrs.size());
        LinearLayout hc = ui.card();
        hc.addView(ui.cardTitle("평균 심박수 추이 (bpm)"));
        double[] hy = new double[hrs.size()];
        String[] hl = new String[hrs.size()];
        for (int i = 0; i < hrs.size(); i++) {
            hy[i] = hrs.get(i).avgHr;
            hl[i] = Fmt.md(hrs.get(i).startMs);
        }
        hc.addView(new ChartView(this, ui).line(hy, null, hl).color(ui.hr).values(true));
        b.addView(hc);

        // 개인 최고 기록
        LinearLayout pb = ui.card();
        pb.addView(ui.cardTitle("개인 최고 기록 (랩 기준)"));
        Map<Integer, Lap> best = new TreeMap<>();
        Map<Integer, Long> bestDate = new TreeMap<>();
        for (Session s : sessions) {
            for (Lap l : s.laps) {
                if (l.distanceM <= 0 || l.timeMs <= 0) continue;
                int key = l.stroke * 100000 + l.distanceM;
                Lap cur = best.get(key);
                if (cur == null || l.timeMs < cur.timeMs) {
                    best.put(key, l);
                    bestDate.put(key, s.startMs);
                }
            }
        }
        if (best.isEmpty()) {
            pb.addView(ui.text("랩 기록이 있으면 영법·거리별 최고 기록이 표시돼요.", 13, ui.muted, false));
        }
        for (Map.Entry<Integer, Lap> e : best.entrySet()) {
            Lap l = e.getValue();
            LinearLayout r = ui.h();
            r.setPadding(0, ui.dp(6), 0, ui.dp(6));
            View dot = new View(this);
            dot.setBackground(Ui.round(Models.strokeColor(l.stroke), ui.dp(5)));
            r.addView(dot, ui.margins(ui.dp(10), ui.dp(10), 0, 0, 10, 0));
            r.addView(ui.text(Models.strokeName(l.stroke) + " " + l.distanceM + "m", 15, ui.fg, false), Ui.weight(1));
            r.addView(ui.text(Fmt.lap(l.timeMs), 16, ui.fg, true));
            TextView d = ui.text("  " + Fmt.md(bestDate.get(e.getKey())), 12, ui.muted, false);
            r.addView(d);
            pb.addView(r);
        }
        b.addView(pb);

        // 영법 비중
        int[] sd = new int[Models.STROKES.length];
        int total = 0;
        for (Session s : sessions) {
            for (Lap l : s.laps) {
                int st = Math.max(0, Math.min(sd.length - 1, l.stroke));
                sd[st] += l.distanceM;
                total += l.distanceM;
            }
        }
        if (total > 0) {
            LinearLayout sc = ui.card();
            sc.addView(ui.cardTitle("영법별 거리"));
            LinearLayout bar = ui.h();
            bar.setBackground(Ui.round(ui.line, ui.dp(7)));
            bar.setClipToOutline(true);
            for (int i = 0; i < sd.length; i++) {
                if (sd[i] == 0) continue;
                View seg = new View(this);
                seg.setBackgroundColor(Models.strokeColor(i));
                bar.addView(seg, new LinearLayout.LayoutParams(0, ui.dp(14), sd[i]));
            }
            sc.addView(bar);
            sc.addView(ui.space(10));
            for (int i = 0; i < sd.length; i++) {
                if (sd[i] == 0) continue;
                LinearLayout r = ui.h();
                r.setPadding(0, ui.dp(4), 0, ui.dp(4));
                View dot = new View(this);
                dot.setBackground(Ui.round(Models.strokeColor(i), ui.dp(5)));
                r.addView(dot, ui.margins(ui.dp(10), ui.dp(10), 0, 0, 10, 0));
                r.addView(ui.text(Models.STROKES[i], 14, ui.fg, false), Ui.weight(1));
                r.addView(ui.text(Fmt.dist(sd[i]) + "  " + Math.round(sd[i] * 100.0 / total) + "%", 14, ui.muted, false));
                sc.addView(r);
            }
            b.addView(sc);
        }

        // 월별 거리
        LinearLayout mc = ui.card();
        mc.addView(ui.cardTitle("월별 거리 (최근 6개월)"));
        long ms = Fmt.monthStart(System.currentTimeMillis());
        double[] my = new double[6];
        String[] ml = new String[6];
        for (int i = 0; i < 6; i++) {
            long st = Fmt.addMonths(ms, i - 5), en = Fmt.addMonths(ms, i - 4);
            ml[i] = (Fmt.cal(st).get(java.util.Calendar.MONTH) + 1) + "월";
            for (Session s : sessions) if (s.startMs >= st && s.startMs < en) my[i] += s.distanceM;
        }
        mc.addView(new ChartView(this, ui).bars(my, ml).highlight(5).values(true)
                .yFormat(v -> v >= 1000 ? String.format(java.util.Locale.US, "%.1fk", v / 1000) : String.valueOf(Math.round(v))));
        b.addView(mc);
    }

    // ───────────────────────── 메뉴 ─────────────────────────

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Health Connect에서 가져오기");
        m.add(0, 2, 0, "기록 직접 추가");
        m.add(0, 3, 0, "최대 심박수 설정");
        m.add(0, 4, 0, "백업 내보내기 (JSON)");
        m.add(0, 5, 0, "백업 불러오기");
        m.add(0, 6, 0, "CSV로 내보내기");
        m.add(0, 7, 0, "샘플 데이터 추가");
        m.add(0, 8, 0, "샘플 데이터 삭제");
        m.add(0, 9, 0, "앱 정보");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: startHealthImport(); break;
                case 2: startActivity(new Intent(this, EditActivity.class)); break;
                case 3: editMaxHr(); break;
                case 4: createDoc("application/json", "swimlog-backup-" + Fmt.ymd(System.currentTimeMillis()) + ".json", REQ_BACKUP); break;
                case 5: openDoc(); break;
                case 6: createDoc("text/csv", "swimlog-" + Fmt.ymd(System.currentTimeMillis()) + ".csv", REQ_CSV); break;
                case 7: addSamples(); break;
                case 8: {
                    int n = Db.get(this).deleteBySource("sample");
                    toast("샘플 기록 " + n + "개를 삭제했어요");
                    reload();
                    break;
                }
                case 9: startActivity(new Intent(this, PrivacyActivity.class)); break;
                default: return false;
            }
            return true;
        });
        pm.show();
    }

    static int maxHr(android.content.Context c) {
        return c.getSharedPreferences("prefs", MODE_PRIVATE).getInt("max_hr", 190);
    }

    private void editMaxHr() {
        final EditText e = ui.field("예: 190", true, false);
        e.setText(String.valueOf(maxHr(this)));
        LinearLayout box = ui.v();
        box.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);
        TextView d = ui.text("심박 구간(Zone) 계산에 사용돼요. 모르면 220 − 나이로 계산해보세요.", 13, ui.muted, false);
        d.setPadding(0, 0, 0, ui.dp(10));
        box.addView(d);
        box.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("최대 심박수")
                .setView(box)
                .setPositiveButton("저장", (dlg, w) -> {
                    int v = 0;
                    try {
                        v = Integer.parseInt(e.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    if (v < 100 || v > 240) {
                        toast("100~240 사이 값을 입력해주세요");
                        return;
                    }
                    SharedPreferences.Editor ed = getSharedPreferences("prefs", MODE_PRIVATE).edit();
                    ed.putInt("max_hr", v).apply();
                    toast("최대 심박수를 " + v + "bpm으로 저장했어요");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void addSamples() {
        int n = Sample.add(this);
        toast("샘플 기록 " + n + "개를 추가했어요");
        reload();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ───────────────────────── Health Connect ─────────────────────────

    private void startHealthImport() {
        if (Build.VERSION.SDK_INT < 34) {
            new AlertDialog.Builder(this)
                    .setTitle("Health Connect")
                    .setMessage("Health Connect 연동은 Android 14 이상에서 지원돼요.\n지금은 기록을 직접 입력해 사용해주세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String p : HealthImporter.PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) {
            runHealthImport();
        } else {
            requestPermissions(missing.toArray(new String[0]), REQ_HC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_HC || Build.VERSION.SDK_INT < 34) return;
        if (checkSelfPermission(HealthImporter.PERMS[0]) == PackageManager.PERMISSION_GRANTED) {
            runHealthImport();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("권한이 필요해요")
                    .setMessage("운동 기록 읽기 권한이 없어서 가져올 수 없어요.\n설정 > 보안 및 개인정보 > Health Connect > 앱 권한에서 '나의 수영 기록'을 허용해주세요.")
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private void runHealthImport() {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Health Connect")
                .setMessage("최근 90일 수영 기록을 가져오는 중…")
                .setCancelable(false)
                .show();
        HealthImporter.run(this, 90, (imported, found, error) -> {
            progress.dismiss();
            String msg;
            if (error != null) {
                msg = "가져오기 중 오류가 발생했어요.\n" + error;
            } else if (found == 0) {
                msg = "최근 90일 동안 Health Connect에 저장된 수영 기록이 없어요.\n\n삼성 헬스를 쓰신다면 삼성 헬스 > 설정 > Health Connect에서 '운동'과 '심박수' 동기화를 켜주세요.";
            } else {
                msg = "수영 기록 " + found + "개 중 새 기록 " + imported + "개를 가져왔어요.";
            }
            new AlertDialog.Builder(this).setTitle("Health Connect").setMessage(msg)
                    .setPositiveButton("확인", null).show();
            reload();
        });
    }

    // ───────────────────────── 백업 / CSV ─────────────────────────

    private void createDoc(String mime, String name, int req) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("파일 저장 화면을 열 수 없어요");
        }
    }

    private void openDoc() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            toast("파일 선택 화면을 열 수 없어요");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_BACKUP || req == REQ_CSV) {
                List<Session> all = Db.get(this).allFull();
                String text = req == REQ_BACKUP ? Backup.toJson(all) : Backup.toCsv(all);
                OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                if (os == null) throw new Exception("파일을 열 수 없어요");
                try {
                    if (req == REQ_CSV) os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // 엑셀 한글 호환
                    os.write(text.getBytes(StandardCharsets.UTF_8));
                } finally {
                    os.close();
                }
                toast(all.size() + "개 기록을 저장했어요");
            } else if (req == REQ_RESTORE) {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("파일을 열 수 없어요");
                StringBuilder sb = new StringBuilder();
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    char[] buf = new char[8192];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                } finally {
                    is.close();
                }
                int[] r = Backup.restore(this, sb.toString());
                toast("새 기록 " + r[0] + "개 복원 (중복 " + r[1] + "개 건너뜀)");
                reload();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("오류").setMessage(String.valueOf(e.getMessage()))
                    .setPositiveButton("확인", null).show();
        }
    }
}
