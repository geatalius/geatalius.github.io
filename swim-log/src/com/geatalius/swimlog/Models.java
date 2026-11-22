package com.geatalius.swimlog;

import java.util.ArrayList;
import java.util.List;

/** 수영 세션 / 랩 데이터 모델. */
final class Models {
    private Models() {}

    static final String[] STROKES = {"자유형", "배영", "평영", "접영", "혼영", "킥·기타"};
    static final int[] STROKE_COLORS = {
            0xFF0A7CFF, 0xFF22B8CF, 0xFF2FB344, 0xFFFF922B, 0xFFAE3EC9, 0xFF868E96
    };

    static String strokeName(int s) {
        return s >= 0 && s < STROKES.length ? STROKES[s] : STROKES[0];
    }

    static int strokeColor(int s) {
        return s >= 0 && s < STROKE_COLORS.length ? STROKE_COLORS[s] : STROKE_COLORS[0];
    }

    static final class Lap {
        long id;
        int distanceM;
        long timeMs;
        int stroke;
        int hr;        // 랩 평균 심박 (0 = 없음)
        int strokes;   // 스트로크 수 (0 = 없음)
        int restS;     // 랩 이후 휴식 (초)

        double pace100() {
            return distanceM > 0 ? timeMs / 1000.0 / distanceM * 100.0 : 0;
        }

        Lap copy() {
            Lap l = new Lap();
            l.distanceM = distanceM;
            l.timeMs = timeMs;
            l.stroke = stroke;
            l.hr = hr;
            l.strokes = strokes;
            l.restS = restS;
            return l;
        }
    }

    static final class Session {
        long id;
        long startMs;
        int poolLen = 25;
        int durationS;
        int distanceM;
        int avgHr;
        int maxHr;
        int kcal;
        String memo = "";
        String source = "manual"; // manual | health | sample | backup
        String extId;
        List<Lap> laps = new ArrayList<>();
        int[] hrT = new int[0];   // 시작 기준 경과 초
        int[] hrBpm = new int[0];

        /** 실제 수영한 시간 (랩 합계, 랩이 없으면 전체 시간). */
        long swimMs() {
            long s = 0;
            for (Lap l : laps) s += l.timeMs;
            return s > 0 ? s : durationS * 1000L;
        }

        /** 100m 당 평균 페이스 (초). */
        double pace100() {
            return distanceM > 0 ? swimMs() / 1000.0 / distanceM * 100.0 : 0;
        }

        String sourceLabel() {
            if ("health".equals(source)) return "Health Connect";
            if ("sample".equals(source)) return "샘플 데이터";
            if ("backup".equals(source)) return "백업에서 복원";
            return "직접 입력";
        }
    }
}
