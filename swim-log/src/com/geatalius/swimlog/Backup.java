package com.geatalius.swimlog;

import android.content.Context;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** JSON 백업 / 복원, CSV 내보내기. */
final class Backup {
    private Backup() {}

    static String toJson(List<Session> list) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "swimlog");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        for (Session s : list) {
            JSONObject o = new JSONObject();
            o.put("startMs", s.startMs);
            o.put("poolLen", s.poolLen);
            o.put("durationS", s.durationS);
            o.put("distanceM", s.distanceM);
            o.put("avgHr", s.avgHr);
            o.put("maxHr", s.maxHr);
            o.put("kcal", s.kcal);
            o.put("memo", s.memo);
            o.put("source", s.source);
            if (s.extId != null) o.put("extId", s.extId);
            JSONArray laps = new JSONArray();
            for (Lap l : s.laps) {
                JSONObject lo = new JSONObject();
                lo.put("d", l.distanceM);
                lo.put("t", l.timeMs);
                lo.put("s", l.stroke);
                lo.put("hr", l.hr);
                lo.put("sc", l.strokes);
                lo.put("r", l.restS);
                laps.put(lo);
            }
            o.put("laps", laps);
            JSONArray hr = new JSONArray();
            for (int i = 0; i < s.hrT.length; i++) {
                hr.put(new JSONArray().put(s.hrT[i]).put(s.hrBpm[i]));
            }
            o.put("hr", hr);
            arr.put(o);
        }
        root.put("sessions", arr);
        return root.toString();
    }

    /** @return {복원 수, 건너뛴 수} */
    static int[] restore(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"swimlog".equals(root.optString("app"))) throw new Exception("나의 수영 기록 백업 파일이 아니에요.");
        JSONArray arr = root.getJSONArray("sessions");
        Db db = Db.get(c);
        int ok = 0, skip = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Session s = new Session();
            s.startMs = o.getLong("startMs");
            s.poolLen = o.optInt("poolLen", 25);
            s.durationS = o.optInt("durationS");
            s.distanceM = o.optInt("distanceM");
            s.avgHr = o.optInt("avgHr");
            s.maxHr = o.optInt("maxHr");
            s.kcal = o.optInt("kcal");
            s.memo = o.optString("memo", "");
            s.source = o.optString("source", "backup");
            s.extId = o.has("extId") ? o.getString("extId") : null;
            if ((s.extId != null && db.hasExt(s.extId)) || db.hasSame(s.startMs, s.distanceM)) {
                skip++;
                continue;
            }
            JSONArray laps = o.optJSONArray("laps");
            if (laps != null) {
                for (int j = 0; j < laps.length(); j++) {
                    JSONObject lo = laps.getJSONObject(j);
                    Lap l = new Lap();
                    l.distanceM = lo.optInt("d");
                    l.timeMs = lo.optLong("t");
                    l.stroke = lo.optInt("s");
                    l.hr = lo.optInt("hr");
                    l.strokes = lo.optInt("sc");
                    l.restS = lo.optInt("r");
                    s.laps.add(l);
                }
            }
            JSONArray hr = o.optJSONArray("hr");
            if (hr != null) {
                s.hrT = new int[hr.length()];
                s.hrBpm = new int[hr.length()];
                for (int j = 0; j < hr.length(); j++) {
                    JSONArray p = hr.getJSONArray(j);
                    s.hrT[j] = p.getInt(0);
                    s.hrBpm[j] = p.getInt(1);
                }
            }
            db.save(s, true);
            ok++;
        }
        return new int[]{ok, skip};
    }

    private static String esc(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 랩 1개당 1행 (랩이 없는 세션은 1행). */
    static String toCsv(List<Session> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("날짜,시작,총거리(m),총시간,평균페이스(/100m),평균심박,최대심박,칼로리,수영장(m),랩번호,영법,랩거리(m),랩시간,랩페이스(/100m),랩심박,스트로크,휴식(초),메모\n");
        for (Session s : list) {
            String head = Fmt.ymd(s.startMs) + "," + Fmt.time(s.startMs) + "," + s.distanceM + "," + Fmt.dur(s.durationS)
                    + "," + Fmt.pace(s.pace100()) + "," + s.avgHr + "," + s.maxHr + "," + s.kcal + "," + s.poolLen;
            if (s.laps.isEmpty()) {
                sb.append(head).append(",,,,,,,,,").append(esc(s.memo)).append('\n');
                continue;
            }
            for (int i = 0; i < s.laps.size(); i++) {
                Lap l = s.laps.get(i);
                sb.append(head).append(',').append(i + 1).append(',').append(Models.strokeName(l.stroke)).append(',')
                        .append(l.distanceM).append(',').append(Fmt.lap(l.timeMs)).append(',').append(Fmt.pace(l.pace100()))
                        .append(',').append(l.hr).append(',').append(l.strokes).append(',').append(l.restS).append(',')
                        .append(i == 0 ? esc(s.memo) : "").append('\n');
            }
        }
        return sb.toString();
    }
}
