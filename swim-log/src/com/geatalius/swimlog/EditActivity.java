package com.geatalius.swimlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 수영 기록 추가 / 수정. */
public class EditActivity extends Activity {
    private Ui ui;
    private Session s;
    private final Calendar cal = Calendar.getInstance();
    private final List<Lap> laps = new ArrayList<>();

    private Button dateBtn, timeBtn, pool25, pool50;
    private EditText poolEt, distEt, durEt, avgHrEt, maxHrEt, kcalEt, memoEt;
    private LinearLayout lapsBox;
    private TextView lapsSummary;
    private int poolLen = 25;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        ui.styleWindow(this);
        long id = getIntent().getLongExtra("id", 0);
        if (id > 0) s = Db.get(this).load(id);
        if (s == null) {
            s = new Session();
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            s.startMs = now.getTimeInMillis();
            s.poolLen = getSharedPreferences("prefs", MODE_PRIVATE).getInt("pool_len", 25);
        }
        cal.setTimeInMillis(s.startMs);
        poolLen = s.poolLen > 0 ? s.poolLen : 25;
        for (Lap l : s.laps) laps.add(l.copy());
        build();
    }

    private void build() {
        LinearLayout root = ui.v();
        root.setBackgroundColor(ui.bg);
        TextView save = ui.text("저장", 16, ui.accent, true);
        save.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        ui.clickable(save, ui.dp(12));
        save.setOnClickListener(v -> save());
        root.addView(ui.topBar(this, s.id > 0 ? "기록 수정" : "수영 기록 추가", save));

        ScrollView sv = new ScrollView(this);
        LinearLayout b = ui.v();
        b.setPadding(0, 0, 0, ui.dp(40));
        sv.addView(b);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        // 기본 정보
        LinearLayout basic = ui.card();
        basic.addView(ui.cardTitle("기본 정보"));
        LinearLayout dt = ui.h();
        dateBtn = pickerButton();
        dateBtn.setOnClickListener(v -> new DatePickerDialog(this, (dp, y, m, d) -> {
            cal.set(y, m, d);
            refreshDate();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());
        timeBtn = pickerButton();
        timeBtn.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m);
            refreshDate();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show());
        LinearLayout dBox = ui.labeled("날짜", dateBtn);
        dt.addView(dBox, Ui.weight(1.4f));
        dt.addView(ui.space(0), Ui.lp(ui.dp(10), 1));
        dt.addView(ui.labeled("시작 시간", timeBtn), Ui.weight(1));
        basic.addView(dt);
        refreshDate();

        basic.addView(ui.space(14));
        LinearLayout poolRow = ui.h();
        pool25 = ui.button("25m", false);
        pool50 = ui.button("50m", false);
        poolEt = ui.field("직접", true, false);
        pool25.setOnClickListener(v -> setPool(25));
        pool50.setOnClickListener(v -> setPool(50));
        poolEt.setOnFocusChangeListener((v, f) -> {
            if (!f) {
                int p = intOf(poolEt);
                if (p > 0) setPool(p);
            }
        });
        poolRow.addView(pool25, Ui.weight(1));
        poolRow.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        poolRow.addView(pool50, Ui.weight(1));
        poolRow.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        poolRow.addView(poolEt, Ui.weight(1));
        basic.addView(ui.labeled("수영장 길이", poolRow));
        setPool(poolLen);

        basic.addView(ui.space(14));
        LinearLayout dd = ui.h();
        distEt = ui.field("랩 합계 자동", true, false);
        durEt = ui.field("자동 계산", true, false);
        if (s.id > 0) {
            distEt.setText(String.valueOf(s.distanceM));
            if (s.durationS > 0) durEt.setText(String.valueOf(Math.round(s.durationS / 60.0)));
        }
        dd.addView(ui.labeled("총 거리 (m)", distEt), Ui.weight(1));
        dd.addView(ui.space(0), Ui.lp(ui.dp(10), 1));
        dd.addView(ui.labeled("총 운동 시간 (분)", durEt), Ui.weight(1));
        basic.addView(dd);
        b.addView(basic);

        // 심박 / 칼로리
        LinearLayout hrc = ui.card();
        hrc.addView(ui.cardTitle("심박수 · 칼로리"));
        LinearLayout hrRow = ui.h();
        avgHrEt = ui.field("bpm", true, false);
        maxHrEt = ui.field("bpm", true, false);
        kcalEt = ui.field("kcal", true, false);
        if (s.avgHr > 0) avgHrEt.setText(String.valueOf(s.avgHr));
        if (s.maxHr > 0) maxHrEt.setText(String.valueOf(s.maxHr));
        if (s.kcal > 0) kcalEt.setText(String.valueOf(s.kcal));
        hrRow.addView(ui.labeled("평균 심박", avgHrEt), Ui.weight(1));
        hrRow.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        hrRow.addView(ui.labeled("최대 심박", maxHrEt), Ui.weight(1));
        hrRow.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        hrRow.addView(ui.labeled("칼로리", kcalEt), Ui.weight(1));
        hrc.addView(hrRow);
        TextView hint = ui.text("비워두면 랩별 심박수로 자동 계산돼요.", 12, ui.muted, false);
        hint.setPadding(0, ui.dp(8), 0, 0);
        hrc.addView(hint);
        b.addView(hrc);

        // 랩
        LinearLayout lc = ui.card();
        LinearLayout lh = ui.h();
        TextView lt = ui.text("랩 기록", 16, ui.fg, true);
        lh.addView(lt, Ui.weight(1));
        lapsSummary = ui.text("", 13, ui.muted, false);
        lh.addView(lapsSummary);
        lh.setPadding(0, 0, 0, ui.dp(10));
        lc.addView(lh);
        lapsBox = ui.v();
        lc.addView(lapsBox);
        lc.addView(ui.space(10));
        Button addLap = ui.button("+ 랩 추가", true);
        addLap.setOnClickListener(v -> lapDialog(-1));
        lc.addView(addLap, Ui.lp(Ui.MATCH, Ui.WRAP));
        b.addView(lc);
        refreshLaps();

        // 메모
        LinearLayout mc = ui.card();
        mc.addView(ui.cardTitle("메모"));
        memoEt = ui.field("컨디션, 훈련 내용 등", false, false);
        memoEt.setSingleLine(false);
        memoEt.setMinLines(3);
        memoEt.setGravity(Gravity.TOP | Gravity.START);
        memoEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        memoEt.setText(s.memo);
        mc.addView(memoEt);
        b.addView(mc);

        setContentView(root);
    }

    private Button pickerButton() {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextColor(ui.fg);
        btn.setTextSize(15);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setBackground(ui.outline(ui.bg, ui.line, ui.dp(12)));
        btn.setStateListAnimator(null);
        btn.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        btn.setMinHeight(ui.dp(46));
        return btn;
    }

    private void refreshDate() {
        dateBtn.setText(Fmt.dateShort(cal.getTimeInMillis()));
        timeBtn.setText(Fmt.time(cal.getTimeInMillis()));
    }

    private void setPool(int p) {
        poolLen = p;
        boolean is25 = p == 25, is50 = p == 50;
        styleToggle(pool25, is25);
        styleToggle(pool50, is50);
        if (!is25 && !is50) {
            if (intOf(poolEt) != p) poolEt.setText(String.valueOf(p));
        } else {
            poolEt.setText("");
        }
    }

    private void styleToggle(Button btn, boolean on) {
        btn.setTextColor(on ? 0xFFFFFFFF : ui.accent);
        btn.setBackground(Ui.round(on ? ui.accent : ui.accentSoft, ui.dp(12)));
    }

    private static int intOf(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double doubleOf(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void refreshLaps() {
        lapsBox.removeAllViews();
        int dist = 0;
        long t = 0;
        for (int i = 0; i < laps.size(); i++) {
            final int idx = i;
            Lap l = laps.get(i);
            dist += l.distanceM;
            t += l.timeMs;
            LinearLayout r = ui.h();
            r.setPadding(ui.dp(6), ui.dp(6), 0, ui.dp(6));
            TextView n = ui.text(String.valueOf(i + 1), 13, ui.muted, true);
            r.addView(n, Ui.lp(ui.dp(26), Ui.WRAP));
            View dot = new View(this);
            dot.setBackground(Ui.round(Models.strokeColor(l.stroke), ui.dp(4)));
            r.addView(dot, ui.margins(ui.dp(8), ui.dp(8), 0, 0, 8, 0));
            LinearLayout mid = ui.v();
            mid.addView(ui.text(Models.strokeName(l.stroke) + " " + l.distanceM + "m   " + Fmt.lap(l.timeMs), 15, ui.fg, true));
            StringBuilder sb = new StringBuilder();
            sb.append(Fmt.pace(l.pace100())).append("/100m");
            if (l.hr > 0) sb.append(" · ♥ ").append(l.hr);
            if (l.strokes > 0) sb.append(" · ").append(l.strokes).append("스트록");
            if (l.restS > 0) sb.append(" · 휴식 ").append(l.restS).append("초");
            mid.addView(ui.text(sb.toString(), 12, ui.muted, false));
            r.addView(mid, Ui.weight(1));
            ImageView del = ui.iconButton(R.drawable.ic_close, ui.muted);
            del.setContentDescription("랩 삭제");
            del.setOnClickListener(v -> {
                laps.remove(idx);
                refreshLaps();
            });
            r.addView(del);
            ui.clickable(r, ui.dp(10));
            r.setOnClickListener(v -> lapDialog(idx));
            lapsBox.addView(r);
        }
        if (laps.isEmpty()) {
            TextView e = ui.text("랩을 추가하면 랩 타임 그래프와 개인 최고 기록이 만들어져요.\n같은 랩을 여러 번 했다면 '반복 횟수'로 한 번에 추가할 수 있어요.", 13, ui.muted, false);
            lapsBox.addView(e);
            lapsSummary.setText("");
        } else {
            lapsSummary.setText(laps.size() + "랩 · " + Fmt.num(dist) + "m · " + Fmt.dur(t / 1000));
        }
    }

    private void lapDialog(final int index) {
        final boolean isNew = index < 0;
        Lap base = isNew ? (laps.isEmpty() ? null : laps.get(laps.size() - 1)) : laps.get(index);

        LinearLayout box = ui.v();
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);

        final Spinner stroke = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Models.STROKES);
        stroke.setAdapter(ad);
        stroke.setSelection(base != null ? base.stroke : 0);
        box.addView(ui.labeled("영법", stroke));
        box.addView(ui.space(10));

        LinearLayout r1 = ui.h();
        final EditText dist = ui.field("m", true, false);
        dist.setText(String.valueOf(base != null ? base.distanceM : poolLen));
        final EditText min = ui.field("분", true, false);
        final EditText sec = ui.field("초 (예: 52.3)", true, true);
        if (!isNew && base != null) {
            long tenths = Math.round(base.timeMs / 100.0);
            long m = tenths / 600;
            double sc = (tenths - m * 600) / 10.0;
            if (m > 0) min.setText(String.valueOf(m));
            sec.setText(String.format(Locale.US, "%.1f", sc));
        }
        r1.addView(ui.labeled("거리", dist), Ui.weight(1));
        r1.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        r1.addView(ui.labeled("분", min), Ui.weight(0.8f));
        r1.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        r1.addView(ui.labeled("초", sec), Ui.weight(1.3f));
        box.addView(r1);
        box.addView(ui.space(10));

        LinearLayout r2 = ui.h();
        final EditText hr = ui.field("bpm", true, false);
        final EditText strokes = ui.field("회", true, false);
        final EditText rest = ui.field("초", true, false);
        if (!isNew && base != null) {
            if (base.hr > 0) hr.setText(String.valueOf(base.hr));
            if (base.strokes > 0) strokes.setText(String.valueOf(base.strokes));
            if (base.restS > 0) rest.setText(String.valueOf(base.restS));
        } else if (base != null && base.restS > 0) {
            rest.setText(String.valueOf(base.restS));
        }
        r2.addView(ui.labeled("심박", hr), Ui.weight(1));
        r2.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        r2.addView(ui.labeled("스트로크", strokes), Ui.weight(1));
        r2.addView(ui.space(0), Ui.lp(ui.dp(8), 1));
        r2.addView(ui.labeled("휴식", rest), Ui.weight(1));
        box.addView(r2);

        final EditText repeat = ui.field("1", true, false);
        if (isNew) {
            box.addView(ui.space(10));
            repeat.setText("1");
            box.addView(ui.labeled("반복 횟수 (같은 랩 여러 개 추가)", repeat));
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(isNew ? "랩 추가" : "랩 " + (index + 1) + " 수정")
                .setView(sv)
                .setPositiveButton(isNew ? "추가" : "저장", null)
                .setNegativeButton("취소", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int dm = intOf(dist);
            double total = intOf(min) * 60.0 + doubleOf(sec);
            if (dm <= 0) {
                toast("거리를 입력해주세요");
                return;
            }
            if (total <= 0) {
                toast("랩 시간을 입력해주세요");
                return;
            }
            Lap l = new Lap();
            l.stroke = stroke.getSelectedItemPosition();
            l.distanceM = dm;
            l.timeMs = Math.round(total * 1000);
            l.hr = intOf(hr);
            l.strokes = intOf(strokes);
            l.restS = intOf(rest);
            if (isNew) {
                int n = Math.max(1, Math.min(100, intOf(repeat)));
                for (int i = 0; i < n; i++) laps.add(l.copy());
            } else {
                laps.set(index, l);
            }
            refreshLaps();
            dlg.dismiss();
        }));
        dlg.show();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void save() {
        if (poolEt.hasFocus()) {
            int p = intOf(poolEt);
            if (p > 0) poolLen = p;
        }
        int lapDist = 0;
        long lapSec = 0;
        int hrSum = 0, hrN = 0, hrMax = 0;
        for (Lap l : laps) {
            lapDist += l.distanceM;
            lapSec += l.timeMs / 1000 + l.restS;
            if (l.hr > 0) {
                hrSum += l.hr;
                hrN++;
                hrMax = Math.max(hrMax, l.hr);
            }
        }
        int dist = intOf(distEt);
        if (dist <= 0) dist = lapDist;
        if (dist <= 0) {
            toast("총 거리를 입력하거나 랩을 추가해주세요");
            return;
        }
        int durMin = intOf(durEt);
        int dur = durMin > 0 ? durMin * 60 : (int) lapSec;
        if (dur <= 0) {
            toast("총 운동 시간을 입력하거나 랩을 추가해주세요");
            return;
        }
        s.startMs = cal.getTimeInMillis();
        s.poolLen = poolLen;
        s.distanceM = dist;
        s.durationS = dur;
        s.avgHr = intOf(avgHrEt) > 0 ? intOf(avgHrEt) : hrN > 0 ? Math.round(hrSum / (float) hrN) : 0;
        s.maxHr = intOf(maxHrEt) > 0 ? intOf(maxHrEt) : hrMax;
        s.kcal = intOf(kcalEt);
        s.memo = memoEt.getText().toString().trim();
        s.laps = laps;
        Db.get(this).save(s, false);
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("pool_len", poolLen).apply();
        toast("저장했어요");
        finish();
    }
}
