package com.fc.v2.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fc.v2.common.conf.PostDueProperties;
import com.fc.v2.model.auto.TPostDueTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 到期告知事项核心说法的自测：同一把尺（起办日）与出手段（档口/旺季班表/节假日）。
 * 纯内存逻辑，不连库。
 */
public class TPostDueTaskServiceImplTest {

    private static Date at(String ymdHms) {
        return DateUtil.parse(ymdHms, "yyyy-MM-dd HH:mm:ss");
    }

    private static TPostDueTask task(String dueAt, String amount) {
        TPostDueTask t = new TPostDueTask();
        t.setDueAt(at(dueAt));
        t.setAmount(amount == null ? null : new BigDecimal(amount));
        t.setStatus(0);
        t.setDelFlag(0);
        return t;
    }

    private static PostDueProperties cfg() {
        PostDueProperties p = new PostDueProperties();
        p.setWindows("09:00-11:30,14:00-17:00");
        p.setPeakStart("11-01");
        p.setPeakEnd("11-15");
        p.setPeakRoster("08:30-12:00,13:30-18:30");
        p.setHolidays("2026-10-01,2026-10-02,2026-10-03,2026-10-04,2026-10-05,2026-10-06,2026-10-07");
        return p;
    }

    // ---------- 同一把尺：起办日过没过 ----------

    @Test
    public void actionable_起办日当天算压着() {
        // 止点 10-05，提前 7 日 → 起办日 09-28
        TPostDueTask t = task("2026-10-05 23:59:59", "7");
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-09-27 23:59:59")), "起办日没到的一律不动");
        assertTrue(TPostDueTaskServiceImpl.actionable(t, at("2026-09-28 00:00:00")), "起办日当日即归本轮");
        assertTrue(TPostDueTaskServiceImpl.actionable(t, at("2026-09-30 10:00:00")), "压着的归本轮");
    }

    @Test
    public void actionable_提前几日可改_起办日跟着动() {
        TPostDueTask t = task("2026-10-05 23:59:59", "0");
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-10-04 23:59:59")));
        assertTrue(TPostDueTaskServiceImpl.actionable(t, at("2026-10-05 00:00:00")), "提前 0 日＝止点当日才动手");
    }

    @Test
    public void actionable_已开口过的不翻第二遍() {
        TPostDueTask t = task("2026-10-05 23:59:59", "7");
        t.setStatus(1);
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-09-30 10:00:00")), "已送达不再当未开口挑出来");
        t.setStatus(2);
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-09-30 10:00:00")), "送不下去不再挑");
        t.setStatus(0);
        t.setDelFlag(1);
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-09-30 10:00:00")), "删了的不挑");
    }

    @Test
    public void actionable_缺日子缺时刻不进本轮() {
        TPostDueTask t = task("2026-10-05 23:59:59", null);
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-10-04 12:00:00")), "提前几日空缺按 0 算");
        assertTrue(TPostDueTaskServiceImpl.actionable(t, at("2026-10-05 00:00:00")));
        t.setDueAt(null);
        assertFalse(TPostDueTaskServiceImpl.actionable(t, at("2026-10-05 00:00:00")), "没日子的不挑");
    }

    // ---------- 出手段：平日档口 ----------

    @Test
    public void inWindow_平日两段档口() {
        PostDueProperties p = cfg();
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 09:00:00"), p), "段首可出手");
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 11:30:00"), p), "段尾可出手");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 12:00:00"), p), "两段之间静默");
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 14:30:00"), p), "下午段可出手");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 08:59:00"), p), "开段前静默");
    }

    @Test
    public void inWindow_夜里整段静默() {
        PostDueProperties p = cfg();
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 23:30:00"), p), "夜里静默");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 02:00:00"), p), "后半夜静默");
    }

    @Test
    public void inWindow_法定节假日整段静默() {
        PostDueProperties p = cfg();
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-10-01 10:00:00"), p), "国庆假日静默");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-10-07 15:00:00"), p), "假日末日也静默");
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-10-08 10:00:00"), p), "假日过了照档口");
    }

    // ---------- 出手段：旺季半月照值守班表 ----------

    @Test
    public void inWindow_旺季走班表不走平日档口() {
        PostDueProperties p = cfg();
        // 旺季 11-01 至 11-15，班表 08:30-12:00,13:30-18:30
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-11-10 17:30:00"), p), "班表段内可出手（平日档口此时已关）");
        assertTrue(TPostDueTaskServiceImpl.inWindow(at("2026-11-10 08:45:00"), p), "班表早段可出手（平日档口此时未开）");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-11-10 12:30:00"), p), "班表段间也静默");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-11-16 17:30:00"), p), "旺季过了回平日档口");
    }

    @Test
    public void isPeakDay_起止含当日_跨年也算() {
        assertTrue(TPostDueTaskServiceImpl.isPeakDay(at("2026-11-01 00:00:00"), "11-01", "11-15"));
        assertTrue(TPostDueTaskServiceImpl.isPeakDay(at("2026-11-15 23:59:59"), "11-01", "11-15"));
        assertFalse(TPostDueTaskServiceImpl.isPeakDay(at("2026-10-31 23:59:59"), "11-01", "11-15"));
        assertTrue(TPostDueTaskServiceImpl.isPeakDay(at("2026-01-05 12:00:00"), "12-28", "01-10"), "跨年旺季");
        assertTrue(TPostDueTaskServiceImpl.isPeakDay(at("2026-12-30 12:00:00"), "12-28", "01-10"), "跨年旺季");
        assertFalse(TPostDueTaskServiceImpl.isPeakDay(at("2026-06-01 12:00:00"), "12-28", "01-10"));
    }

    // ---------- 段解析 ----------

    @Test
    public void parseSegments_配错的段丢弃() {
        List<int[]> segs = TPostDueTaskServiceImpl.parseSegments("09:00-11:30,坏段,14:00-17:00,25:00-26:00");
        assertEquals(2, segs.size());
        assertArrayEquals(new int[]{540, 690}, segs.get(0));
        assertArrayEquals(new int[]{840, 1020}, segs.get(1));
        assertTrue(TPostDueTaskServiceImpl.parseSegments("").isEmpty());
        assertTrue(TPostDueTaskServiceImpl.parseSegments(null).isEmpty());
    }

    @Test
    public void inWindow_档口全不配则整日静默() {
        PostDueProperties p = cfg();
        p.setWindows("");
        assertFalse(TPostDueTaskServiceImpl.inWindow(at("2026-09-30 10:00:00"), p), "没配档口宁可静默");
    }
}
