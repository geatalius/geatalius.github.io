package com.geatalius.swimlog;

import java.util.Calendar;
import java.util.Locale;

/** 표시용 포맷 함수. */
final class Fmt {
    private Fmt() {}

    private static final String[] WD = {"일", "월", "화", "수", "목", "금", "토"};

    static Calendar cal(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c;
    }

    /** 1:02:03 / 45:10 */
    static String dur(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    /** 1시간 5분 / 45분 */
    static String durKo(long sec) {
        long h = sec / 3600, m = (sec % 3600 + 30) / 60;
        if (m == 60) { h++; m = 0; }
        if (h > 0) return m > 0 ? h + "시간 " + m + "분" : h + "시간";
        return m + "분";
    }

    /** 랩 타임: 1:02.3 / 58.4 */
    static String lap(long ms) {
        long t = Math.round(ms / 100.0);
        long tenths = t % 10, total = t / 10, m = total / 60, s = total % 60;
        return m > 0 ? String.format(Locale.US, "%d:%02d.%d", m, s, tenths)
                : String.format(Locale.US, "%d.%d", s, tenths);
    }

    /** 100m 페이스 1:55 */
    static String pace(double secPer100) {
        if (secPer100 <= 0 || Double.isNaN(secPer100) || Double.isInfinite(secPer100)) return "-";
        long s = Math.round(secPer100);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    static String num(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    /** 1,500m / 12.3km */
    static String dist(long m) {
        if (m >= 10000) return String.format(Locale.US, "%.1fkm", m / 1000.0);
        return num(m) + "m";
    }

    static String km(long m) {
        return String.format(Locale.US, m >= 100000 ? "%.0f" : "%.1f", m / 1000.0);
    }

    static String weekday(long ms) {
        return WD[cal(ms).get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** 2026년 11월 22일 (일) */
    static String dateFull(long ms) {
        Calendar c = cal(ms);
        return c.get(Calendar.YEAR) + "년 " + (c.get(Calendar.MONTH) + 1) + "월 " + c.get(Calendar.DAY_OF_MONTH)
                + "일 (" + weekday(ms) + ")";
    }

    /** 11월 22일 (일) */
    static String dateShort(long ms) {
        Calendar c = cal(ms);
        return (c.get(Calendar.MONTH) + 1) + "월 " + c.get(Calendar.DAY_OF_MONTH) + "일 (" + weekday(ms) + ")";
    }

    /** 11/22 */
    static String md(long ms) {
        Calendar c = cal(ms);
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** 2026년 11월 */
    static String ym(long ms) {
        Calendar c = cal(ms);
        return c.get(Calendar.YEAR) + "년 " + (c.get(Calendar.MONTH) + 1) + "월";
    }

    /** 07:10 */
    static String time(long ms) {
        Calendar c = cal(ms);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String ymd(long ms) {
        Calendar c = cal(ms);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    /** 주의 시작(월요일 00:00) */
    static long weekStart(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK); // 일=1
        int back = (dow + 5) % 7;              // 월=0 ... 일=6
        c.add(Calendar.DAY_OF_MONTH, -back);
        return c.getTimeInMillis();
    }

    static long monthStart(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long addMonths(long ms, int n) {
        Calendar c = cal(ms);
        c.add(Calendar.MONTH, n);
        return c.getTimeInMillis();
    }
}
