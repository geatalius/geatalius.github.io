package com.geatalius.swimlog;

import android.content.Context;

import com.geatalius.swimlog.Models.Lap;
import com.geatalius.swimlog.Models.Session;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/** 앱을 둘러볼 수 있도록 그럴듯한 샘플 기록을 만든다. */
final class Sample {
    private Sample() {}

    static int add(Context c) {
        Random r = new Random();
        Db db = Db.get(c);
        int n = 14, made = 0;
        for (int i = 0; i < n; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -(1 + (n - 1 - i) * 3 + r.nextInt(2)));
            boolean morning = r.nextBoolean();
            cal.set(Calendar.HOUR_OF_DAY, morning ? 6 : 19);
            cal.set(Calendar.MINUTE, morning ? 30 : 10 + r.nextInt(30));
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            double progress = i / (double) (n - 1); // 시간이 갈수록 조금씩 빨라짐

            Session s = new Session();
            s.source = "sample";
            s.startMs = cal.getTimeInMillis();
            s.poolLen = r.nextInt(3) == 0 ? 50 : 25;
            List<int[]> plan = new ArrayList<>(); // {stroke, baseSec, count, rest, targetHr}
            plan.add(new int[]{0, 62, 4, 15, 128});           // 워밍업 자유형
            plan.add(new int[]{5, 78, 2, 20, 135});           // 킥
            int main = 8 + r.nextInt(5);
            plan.add(new int[]{0, 54, main, 20, 158});        // 메인 자유형
            if (r.nextBoolean()) plan.add(new int[]{4, 66, 4, 30, 160}); // 혼영
            else plan.add(new int[]{2, 68, 4, 25, 150});      // 평영
            plan.add(new int[]{1, 70, 2, 10, 132});           // 쿨다운 배영

            List<Integer> hrT = new ArrayList<>(), hrB = new ArrayList<>();
            double hr = 92;
            int t = 0;
            int lapIdxInPlan;
            for (int[] p : plan) {
                for (lapIdxInPlan = 0; lapIdxInPlan < p[2]; lapIdxInPlan++) {
                    Lap l = new Lap();
                    l.stroke = p[0];
                    l.distanceM = 50;
                    double fatigue = lapIdxInPlan * 0.25;
                    double sec = p[1] - progress * 3.0 + fatigue + r.nextGaussian() * 1.2;
                    l.timeMs = Math.round(sec * 1000);
                    l.restS = p[3];
                    l.strokes = l.stroke == 5 ? 0 : (int) Math.round((l.stroke == 2 ? 20 : 36) + r.nextGaussian() * 2);
                    // 심박 샘플 (5초 간격)
                    int lapEnd = t + (int) Math.round(sec);
                    int hs = 0, hn = 0;
                    for (; t < lapEnd; t += 5) {
                        hr += (p[4] + lapIdxInPlan * 0.6 - hr) * 0.12 + r.nextGaussian() * 1.5;
                        hrT.add(t);
                        hrB.add((int) Math.round(hr));
                        hs += (int) Math.round(hr);
                        hn++;
                    }
                    l.hr = hn > 0 ? Math.round(hs / (float) hn) : 0;
                    int restEnd = t + l.restS;
                    for (; t < restEnd; t += 5) {
                        hr += (112 - hr) * 0.08 + r.nextGaussian();
                        hrT.add(t);
                        hrB.add((int) Math.round(hr));
                    }
                    s.laps.add(l);
                    s.distanceM += l.distanceM;
                }
                t += 30; // 세트 사이 휴식
            }
            if (!s.laps.isEmpty()) s.laps.get(s.laps.size() - 1).restS = 0;
            s.durationS = t;
            s.hrT = new int[hrT.size()];
            s.hrBpm = new int[hrB.size()];
            long sum = 0;
            for (int k = 0; k < hrT.size(); k++) {
                s.hrT[k] = hrT.get(k);
                s.hrBpm[k] = hrB.get(k);
                sum += s.hrBpm[k];
                s.maxHr = Math.max(s.maxHr, s.hrBpm[k]);
            }
            s.avgHr = hrT.isEmpty() ? 0 : (int) Math.round(sum / (double) hrT.size());
            s.kcal = (int) Math.round(s.durationS / 60.0 * 8.5);
            s.memo = i == n - 1 ? "메인 세트 페이스가 많이 좋아졌다!" : "";
            db.save(s, true);
            made++;
        }
        return made;
    }
}
