package com.geatalius.swimlog;

import android.annotation.TargetApi;
import android.content.Context;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Length;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Android 14+ 내장 Health Connect 에서 수영 세션(랩·영법), 심박수, 거리, 칼로리를 읽어 온다.
 * 삼성 헬스 · 갤럭시 워치 · 기타 웨어러블 앱이 Health Connect 에 기록한 데이터를 사용한다.
 */
@TargetApi(34)
final class HealthImporter {
    interface Callback {
        void done(int imported, int found, String error);
    }

    static final String[] PERMS = {
            HealthPermissions.READ_EXERCISE,
            HealthPermissions.READ_HEART_RATE,
            HealthPermissions.READ_DISTANCE,
            HealthPermissions.READ_TOTAL_CALORIES_BURNED,
    };

    private final HealthConnectManager hcm;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    private HealthImporter(Context c) {
        hcm = c.getSystemService(HealthConnectManager.class);
    }

    static void run(final Context ctx, final int days, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int[] res = {0, 0};
            String err = null;
            HealthImporter h = null;
            try {
                h = new HealthImporter(app);
                if (h.hcm == null) throw new Exception("이 기기에서 Health Connect를 사용할 수 없어요.");
                res = h.importSwims(app, days);
            } catch (Throwable t) {
                err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            } finally {
                if (h != null) h.ex.shutdown();
            }
            final int[] r = res;
            final String e = err;
            main.post(() -> cb.done(r[0], r[1], e));
        }, "hc-import").start();
    }

    private int[] importSwims(Context c, int days) throws Exception {
        Db db = Db.get(c);
        Instant end = Instant.now(), start = end.minus(days, ChronoUnit.DAYS);
        List<ExerciseSessionRecord> list = readAll(ExerciseSessionRecord.class, start, end);
        int imported = 0, found = 0;
        for (ExerciseSessionRecord r : list) {
            int type = r.getExerciseType();
            if (type != ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_POOL
                    && type != ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_OPEN_WATER) continue;
            found++;
            String ext = "hc:" + r.getMetadata().getId();
            if (db.hasExt(ext)) continue;
            Session s = convert(r);
            s.extId = ext;
            db.save(s, true);
            imported++;
        }
        return new int[]{imported, found};
    }

    private Session convert(ExerciseSessionRecord r) {
        Instant s0 = r.getStartTime(), e0 = r.getEndTime();
        Session s = new Session();
        s.source = "health";
        s.startMs = s0.toEpochMilli();
        s.durationS = (int) Math.max(0, (e0.toEpochMilli() - s0.toEpochMilli()) / 1000);
        StringBuilder memo = new StringBuilder();
        if (r.getTitle() != null && r.getTitle().length() > 0) memo.append(r.getTitle());
        if (r.getNotes() != null && r.getNotes().length() > 0) {
            if (memo.length() > 0) memo.append('\n');
            memo.append(r.getNotes());
        }
        if (r.getExerciseType() == ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_OPEN_WATER) {
            if (memo.length() > 0) memo.append('\n');
            memo.append("오픈워터 수영");
        }
        s.memo = memo.toString();

        // 심박수
        List<long[]> samples = new ArrayList<>();
        try {
            for (HeartRateRecord hr : readAll(HeartRateRecord.class, s0, e0)) {
                for (HeartRateRecord.HeartRateSample x : hr.getSamples()) {
                    Instant t = x.getTime();
                    if (t.isBefore(s0) || t.isAfter(e0)) continue;
                    samples.add(new long[]{t.toEpochMilli(), x.getBeatsPerMinute()});
                }
            }
        } catch (Exception ignored) {
            // 심박 권한이 없으면 건너뜀
        }
        Collections.sort(samples, (a, b) -> Long.compare(a[0], b[0]));
        s.hrT = new int[samples.size()];
        s.hrBpm = new int[samples.size()];
        long sum = 0;
        for (int i = 0; i < samples.size(); i++) {
            s.hrT[i] = (int) ((samples.get(i)[0] - s.startMs) / 1000);
            s.hrBpm[i] = (int) samples.get(i)[1];
            sum += s.hrBpm[i];
            s.maxHr = Math.max(s.maxHr, s.hrBpm[i]);
        }
        if (!samples.isEmpty()) s.avgHr = (int) Math.round(sum / (double) samples.size());

        // 거리
        double dist = 0;
        try {
            for (DistanceRecord d : readAll(DistanceRecord.class, s0, e0)) dist += d.getDistance().getInMeters();
        } catch (Exception ignored) {
        }
        // 칼로리
        try {
            double cal = 0;
            for (TotalCaloriesBurnedRecord t : readAll(TotalCaloriesBurnedRecord.class, s0, e0)) {
                cal += t.getEnergy().getInCalories();
            }
            s.kcal = (int) Math.round(cal / 1000.0);
        } catch (Exception ignored) {
        }

        // 랩
        List<ExerciseLap> laps = new ArrayList<>(r.getLaps());
        Collections.sort(laps, (a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        Map<Integer, Integer> lenCount = new HashMap<>();
        for (ExerciseLap l : laps) {
            int m = lapLen(l);
            if (m > 0) lenCount.put(m, lenCount.containsKey(m) ? lenCount.get(m) + 1 : 1);
        }
        int pool = 25, bestCnt = 0;
        for (Map.Entry<Integer, Integer> e : lenCount.entrySet()) {
            if (e.getValue() > bestCnt) {
                bestCnt = e.getValue();
                pool = e.getKey();
            }
        }
        s.poolLen = pool;
        List<ExerciseSegment> segs = r.getSegments();
        int lapDist = 0;
        for (int i = 0; i < laps.size(); i++) {
            ExerciseLap el = laps.get(i);
            Lap l = new Lap();
            long ls = el.getStartTime().toEpochMilli(), le = el.getEndTime().toEpochMilli();
            l.timeMs = Math.max(0, le - ls);
            l.distanceM = lapLen(el) > 0 ? lapLen(el) : pool;
            l.stroke = strokeAt(segs, (ls + le) / 2);
            if (i + 1 < laps.size()) {
                long next = laps.get(i + 1).getStartTime().toEpochMilli();
                l.restS = (int) Math.max(0, (next - le) / 1000);
            }
            int hs = 0, hn = 0;
            for (long[] x : samples) {
                if (x[0] >= ls && x[0] <= le) {
                    hs += x[1];
                    hn++;
                }
            }
            if (hn > 0) l.hr = Math.round(hs / (float) hn);
            lapDist += l.distanceM;
            s.laps.add(l);
        }
        s.distanceM = dist > 0 ? (int) Math.round(dist) : lapDist;
        return s;
    }

    private static int lapLen(ExerciseLap l) {
        Length len = l.getLength();
        return len == null ? 0 : (int) Math.round(len.getInMeters());
    }

    private static int strokeAt(List<ExerciseSegment> segs, long t) {
        if (segs == null) return 0;
        for (ExerciseSegment sg : segs) {
            if (sg.getStartTime().toEpochMilli() <= t && sg.getEndTime().toEpochMilli() >= t) {
                switch (sg.getSegmentType()) {
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE: return 0;
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_BACKSTROKE: return 1;
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE: return 2;
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_BUTTERFLY: return 3;
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_MIXED: return 4;
                    case ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_SWIMMING_OTHER: return 5;
                    default: return 0;
                }
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private <T extends Record> List<T> readAll(Class<T> cls, Instant s, Instant e) throws Exception {
        List<T> out = new ArrayList<>();
        long token = -1;
        int guard = 0;
        do {
            ReadRecordsRequestUsingFilters.Builder<T> b = new ReadRecordsRequestUsingFilters.Builder<>(cls)
                    .setTimeRangeFilter(new TimeInstantRangeFilter.Builder().setStartTime(s).setEndTime(e).build())
                    .setPageSize(1000);
            if (token != -1) b.setPageToken(token);
            final CountDownLatch latch = new CountDownLatch(1);
            final Object[] res = new Object[2];
            hcm.readRecords(b.build(), ex, new OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException>() {
                @Override
                public void onResult(ReadRecordsResponse<T> r) {
                    res[0] = r;
                    latch.countDown();
                }

                @Override
                public void onError(HealthConnectException err) {
                    res[1] = err;
                    latch.countDown();
                }
            });
            if (!latch.await(60, TimeUnit.SECONDS)) throw new Exception("Health Connect 응답 시간 초과");
            if (res[1] != null) {
                Throwable t = (Throwable) res[1];
                throw new Exception(t.getMessage() != null ? t.getMessage() : "Health Connect 오류", t);
            }
            ReadRecordsResponse<T> r = (ReadRecordsResponse<T>) res[0];
            out.addAll(r.getRecords());
            token = r.getNextPageToken();
        } while (token != -1 && ++guard < 50);
        return out;
    }
}
