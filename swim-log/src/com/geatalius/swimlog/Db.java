package com.geatalius.swimlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SQLite 저장소. */
final class Db extends SQLiteOpenHelper {
    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "swimlog.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL,"
                + " pool_len INTEGER, duration_s INTEGER, distance_m INTEGER, avg_hr INTEGER, max_hr INTEGER,"
                + " kcal INTEGER, memo TEXT, source TEXT, ext_id TEXT)");
        db.execSQL("CREATE UNIQUE INDEX idx_sessions_ext ON sessions(ext_id)");
        db.execSQL("CREATE TABLE laps (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL,"
                + " idx INTEGER, distance_m INTEGER, time_ms INTEGER, stroke INTEGER, hr INTEGER,"
                + " strokes INTEGER, rest_s INTEGER)");
        db.execSQL("CREATE INDEX idx_laps_session ON laps(session_id)");
        db.execSQL("CREATE TABLE hr (session_id INTEGER NOT NULL, t_s INTEGER, bpm INTEGER)");
        db.execSQL("CREATE INDEX idx_hr_session ON hr(session_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    /** 세션 저장 (신규/수정). replaceHr=true 이면 심박 샘플도 교체. */
    synchronized long save(Session s, boolean replaceHr) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("start_ms", s.startMs);
            v.put("pool_len", s.poolLen);
            v.put("duration_s", s.durationS);
            v.put("distance_m", s.distanceM);
            v.put("avg_hr", s.avgHr);
            v.put("max_hr", s.maxHr);
            v.put("kcal", s.kcal);
            v.put("memo", s.memo == null ? "" : s.memo);
            v.put("source", s.source);
            if (s.extId != null) v.put("ext_id", s.extId);
            else v.putNull("ext_id");
            if (s.id > 0) {
                db.update("sessions", v, "id=?", new String[]{String.valueOf(s.id)});
            } else {
                s.id = db.insertOrThrow("sessions", null, v);
            }
            String[] arg = {String.valueOf(s.id)};
            db.delete("laps", "session_id=?", arg);
            int idx = 0;
            for (Lap l : s.laps) {
                ContentValues lv = new ContentValues();
                lv.put("session_id", s.id);
                lv.put("idx", idx++);
                lv.put("distance_m", l.distanceM);
                lv.put("time_ms", l.timeMs);
                lv.put("stroke", l.stroke);
                lv.put("hr", l.hr);
                lv.put("strokes", l.strokes);
                lv.put("rest_s", l.restS);
                l.id = db.insert("laps", null, lv);
            }
            if (replaceHr) {
                db.delete("hr", "session_id=?", arg);
                for (int i = 0; i < s.hrT.length; i++) {
                    ContentValues hv = new ContentValues();
                    hv.put("session_id", s.id);
                    hv.put("t_s", s.hrT[i]);
                    hv.put("bpm", s.hrBpm[i]);
                    db.insert("hr", null, hv);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return s.id;
    }

    synchronized void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        String[] arg = {String.valueOf(id)};
        db.beginTransaction();
        try {
            db.delete("laps", "session_id=?", arg);
            db.delete("hr", "session_id=?", arg);
            db.delete("sessions", "id=?", arg);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized int deleteBySource(String source) {
        List<Long> ids = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id FROM sessions WHERE source=?", new String[]{source});
        try {
            while (c.moveToNext()) ids.add(c.getLong(0));
        } finally {
            c.close();
        }
        for (long id : ids) delete(id);
        return ids.size();
    }

    synchronized boolean hasExt(String ext) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM sessions WHERE ext_id=? LIMIT 1", new String[]{ext});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    synchronized boolean hasSame(long startMs, int distanceM) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM sessions WHERE start_ms=? AND distance_m=? LIMIT 1",
                new String[]{String.valueOf(startMs), String.valueOf(distanceM)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static Session readSession(Cursor c) {
        Session s = new Session();
        s.id = c.getLong(0);
        s.startMs = c.getLong(1);
        s.poolLen = c.getInt(2);
        s.durationS = c.getInt(3);
        s.distanceM = c.getInt(4);
        s.avgHr = c.getInt(5);
        s.maxHr = c.getInt(6);
        s.kcal = c.getInt(7);
        s.memo = c.isNull(8) ? "" : c.getString(8);
        s.source = c.isNull(9) ? "manual" : c.getString(9);
        s.extId = c.isNull(10) ? null : c.getString(10);
        return s;
    }

    private static final String COLS = "id,start_ms,pool_len,duration_s,distance_m,avg_hr,max_hr,kcal,memo,source,ext_id";
    private static final String LAP_COLS = "session_id,id,distance_m,time_ms,stroke,hr,strokes,rest_s";

    private static Lap readLap(Cursor c) {
        Lap l = new Lap();
        l.id = c.getLong(1);
        l.distanceM = c.getInt(2);
        l.timeMs = c.getLong(3);
        l.stroke = c.getInt(4);
        l.hr = c.getInt(5);
        l.strokes = c.getInt(6);
        l.restS = c.getInt(7);
        return l;
    }

    /** 모든 세션 (최신순, 랩 포함 / 심박 샘플 제외). */
    synchronized List<Session> all() {
        SQLiteDatabase db = getReadableDatabase();
        List<Session> out = new ArrayList<>();
        Map<Long, Session> byId = new HashMap<>();
        Cursor c = db.rawQuery("SELECT " + COLS + " FROM sessions ORDER BY start_ms DESC", null);
        try {
            while (c.moveToNext()) {
                Session s = readSession(c);
                out.add(s);
                byId.put(s.id, s);
            }
        } finally {
            c.close();
        }
        c = db.rawQuery("SELECT " + LAP_COLS + " FROM laps ORDER BY session_id, idx", null);
        try {
            while (c.moveToNext()) {
                Session s = byId.get(c.getLong(0));
                if (s != null) s.laps.add(readLap(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** 세션 한 개 (랩 + 심박 샘플 포함). */
    synchronized Session load(long id) {
        SQLiteDatabase db = getReadableDatabase();
        String[] arg = {String.valueOf(id)};
        Session s = null;
        Cursor c = db.rawQuery("SELECT " + COLS + " FROM sessions WHERE id=?", arg);
        try {
            if (c.moveToFirst()) s = readSession(c);
        } finally {
            c.close();
        }
        if (s == null) return null;
        c = db.rawQuery("SELECT " + LAP_COLS + " FROM laps WHERE session_id=? ORDER BY idx", arg);
        try {
            while (c.moveToNext()) s.laps.add(readLap(c));
        } finally {
            c.close();
        }
        c = db.rawQuery("SELECT t_s,bpm FROM hr WHERE session_id=? ORDER BY t_s", arg);
        try {
            int n = c.getCount();
            s.hrT = new int[n];
            s.hrBpm = new int[n];
            int i = 0;
            while (c.moveToNext()) {
                s.hrT[i] = c.getInt(0);
                s.hrBpm[i] = c.getInt(1);
                i++;
            }
        } finally {
            c.close();
        }
        return s;
    }

    synchronized List<Session> allFull() {
        List<Session> out = new ArrayList<>();
        for (Session s : all()) {
            Session f = load(s.id);
            if (f != null) out.add(f);
        }
        return out;
    }
}
