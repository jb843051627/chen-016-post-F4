package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.conf.PostDueProperties;
import com.fc.v2.mapper.auto.TPostCompanyArchMapper;
import com.fc.v2.mapper.auto.TPostDueRoundMapper;
import com.fc.v2.mapper.auto.TPostDueTaskMapper;
import com.fc.v2.model.auto.TPostCompanyArch;
import com.fc.v2.model.auto.TPostDueRound;
import com.fc.v2.model.auto.TPostDueTask;
import com.fc.v2.service.ITPostDueTaskService;
import com.fc.v2.util.StringUtils;

import cn.hutool.core.date.DateUtil;

/**
 * 经营许可效期与备案年度核验到期告知事项 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * <p>挑与认只有一条说法：起办日（该动手那一日往前推"提前几日"）过没过——没过的一律不动，
 * 压着的归本轮。翻单子（listDue）与回写（runOnce）同用 {@link #actionable} 这一把尺，
 * 年底两本册子才对得齐。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPostDueTaskServiceImpl implements ITPostDueTaskService {

    /** 条目情形：未开口 */
    static final int STATUS_WAIT = 0;
    /** 条目情形：已送达 */
    static final int STATUS_DONE = 1;
    /** 条目情形：送不下去 */
    static final int STATUS_FAIL = 2;

    /** 本轮结局：档口静默未动手 */
    static final int OUTCOME_MUTED = 0;
    /** 本轮结局：本轮无事 */
    static final int OUTCOME_IDLE = 1;
    /** 本轮结局：已执行 */
    static final int OUTCOME_DONE = 2;
    /** 本轮结局：执行不出去 */
    static final int OUTCOME_ERROR = 3;

    /** 送不下去情由：被告知企业已注销 */
    static final String REASON_COMPANY_GONE = "被告知企业已注销";
    /** 送不下去情由：被告知企业不在册 */
    static final String REASON_COMPANY_UNKNOWN = "被告知企业不在册";
    /** 送不下去情由：送达途径整季停摆 */
    static final String REASON_CHANNEL_DOWN = "送达途径整季停摆";
    /** 撤回情由：日子过了还没送出去 */
    static final String REASON_OVERDUE = "日子过了还没送出去，本轮撤回";

    /** 单条落点：本轮前一回已落地，不再动 */
    private static final int RC_SKIPPED = 0;
    /** 单条落点：送达 */
    private static final int RC_SENT = 1;
    /** 单条落点：送不下去（企业已注销/不在册） */
    private static final int RC_FAIL_GONE = 2;
    /** 单条落点：送不下去（送达途径整季停摆） */
    private static final int RC_FAIL_CHANNEL = 3;
    /** 单条落点：撤回（日子过了还没送出去） */
    private static final int RC_WITHDRAWN = 4;

    @javax.annotation.Resource
    private TPostDueTaskMapper postDueTaskMapper;

    @javax.annotation.Resource
    private TPostDueRoundMapper postDueRoundMapper;

    @javax.annotation.Resource
    private TPostCompanyArchMapper postCompanyArchMapper;

    @Autowired
    private PostDueProperties postDueProperties;

    @Override
    public TPostDueTask selectTPostDueTaskById(Long id) {
        return this.postDueTaskMapper.selectById(id);
    }

    @Override
    public List<TPostDueTask> selectTPostDueTaskList(TPostDueTask record) {
        QueryWrapper<TPostDueTask> queryWrapper = new QueryWrapper<TPostDueTask>();
        queryWrapper.eq("del_flag", 0);
        if (record != null) {
            if (StringUtils.isNotEmpty(record.getItemNo())) {
                queryWrapper.like("item_no", record.getItemNo());
            }
            if (record.getBizType() != null) {
                queryWrapper.eq("biz_type", record.getBizType());
            }
            if (record.getStatus() != null) {
                queryWrapper.eq("status", record.getStatus());
            }
            if (StringUtils.isNotEmpty(record.getSiteNo())) {
                queryWrapper.eq("site_no", record.getSiteNo());
            }
        }
        queryWrapper.orderByAsc("due_at").orderByAsc("id");
        return this.postDueTaskMapper.selectList(queryWrapper);
    }

    /**
     * 挑与认同用的一把尺：起办日过没过。
     * 日子比较全部在内存里做——JDBC 的 serverTimezone 与本机时区不对称，把
     * java.util.Date 作参数传进 SQL 会整体偏移，"恰好到期"这类边界用例会错判。
     */
    static boolean actionable(TPostDueTask r, Date at) {
        if (r == null || at == null || r.getDueAt() == null) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_WAIT) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        // 起办日没到的一律不动，压着的归本轮
        return !startAt(r).after(at);
    }

    /** 起办日＝该动手那一日往前推"提前几日"，自当日 00:00:00 起算 */
    static Date startAt(TPostDueTask r) {
        int ahead = r.getAmount() == null ? 0 : r.getAmount().intValue();
        return DateUtil.beginOfDay(DateUtil.offsetDay(r.getDueAt(), -ahead));
    }

    @Override
    public List<TPostDueTask> listDue(Date at) {
        List<TPostDueTask> waiting = this.postDueTaskMapper.selectList(new QueryWrapper<TPostDueTask>()
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0));
        List<TPostDueTask> due = new ArrayList<TPostDueTask>();
        for (TPostDueTask r : waiting) {
            if (actionable(r, at)) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public boolean isInWindow(Date at) {
        return inWindow(at, this.postDueProperties);
    }

    /**
     * 出手的时辰：法定节假日整段静默；旺季半月两段照值守班表，其余日子照平日档口；
     * 段外（含夜里）空有到期也不动。
     */
    static boolean inWindow(Date at, PostDueProperties cfg) {
        if (at == null || cfg == null) {
            return false;
        }
        if (onHoliday(at, cfg.getHolidays())) {
            return false;
        }
        String segs = isPeakDay(at, cfg.getPeakStart(), cfg.getPeakEnd())
                ? cfg.getPeakRoster() : cfg.getWindows();
        int minute = DateUtil.hour(at, true) * 60 + DateUtil.minute(at);
        for (int[] span : parseSegments(segs)) {
            if (minute >= span[0] && minute <= span[1]) {
                return true;
            }
        }
        return false;
    }

    /** 当日是否法定节假日（yyyy-MM-dd 逐日比对） */
    static boolean onHoliday(Date at, String holidays) {
        if (StringUtils.isEmpty(holidays)) {
            return false;
        }
        String day = DateUtil.formatDate(at);
        for (String h : holidays.split(",")) {
            if (day.equals(h.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 当日是否落在旺季半月内（月-日起止，含当日；起大于止按跨年算） */
    static boolean isPeakDay(Date at, String peakStart, String peakEnd) {
        if (StringUtils.isEmpty(peakStart) || StringUtils.isEmpty(peakEnd)) {
            return false;
        }
        String md = DateUtil.format(at, "MM-dd");
        if (peakStart.compareTo(peakEnd) <= 0) {
            return md.compareTo(peakStart) >= 0 && md.compareTo(peakEnd) <= 0;
        }
        return md.compareTo(peakStart) >= 0 || md.compareTo(peakEnd) <= 0;
    }

    /** 解析"09:00-11:30,14:00-17:00"为起止分钟对；配错的一段丢弃，全不配则整日静默 */
    static List<int[]> parseSegments(String text) {
        List<int[]> segs = new ArrayList<int[]>();
        if (StringUtils.isEmpty(text)) {
            return segs;
        }
        for (String seg : text.split(",")) {
            String[] parts = seg.trim().split("-");
            if (parts.length != 2) {
                continue;
            }
            int from = toMinute(parts[0]);
            int to = toMinute(parts[1]);
            if (from >= 0 && to >= from) {
                segs.add(new int[]{from, to});
            }
        }
        return segs;
    }

    private static int toMinute(String hhmm) {
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return -1;
            }
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runOnce(Date at) {
        return runOnce(at, null);
    }

    /**
     * 同名重载：一轮执行。窗口、挑选、回写、三个数全在这一回里算清再写回去；
     * 每一轮都留一册，round 非空时把本轮局面抄给出参带回。
     *
     * @return 成功条数；执行不出去返回 -1（与"本轮无事"的 0 分两说）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runOnce(Date at, TPostDueRound round) {
        Date now = at != null ? at : new Date();
        TPostDueRound book = new TPostDueRound();
        book.setRunAt(now);
        int sent;
        try {
            sent = doRound(book, now);
        } catch (Exception e) {
            // 执行不出去——与"本轮无事"分两说，册上记明白
            book.setOutcome(OUTCOME_ERROR);
            book.setMessage("执行不出去：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            sent = -1;
        }
        // 三个数与库里的三个数须出自同一回计算：回写落定后当场点数、当场入册
        fillThreeCounts(book);
        book.setDelFlag(0);
        this.postDueRoundMapper.insert(book);
        if (round != null) {
            copyRound(book, round);
        }
        return sent;
    }

    /** 一轮的本体：静默、无事、已执行三种落点 */
    private int doRound(TPostDueRound book, Date now) {
        // 档口之外（夜里/法定节假日/段外）整段静默：一条不动，留原地等下一轮
        if (!isInWindow(now)) {
            book.setOutcome(OUTCOME_MUTED);
            book.setPickedCount(0);
            book.setSentCount(0);
            book.setWithdrawnCount(0);
            book.setMessage("档口静默（夜里/法定节假日/段外），本轮未动手，到期条目留原地等下一轮");
            return 0;
        }
        // 翻单子与回写同一把尺
        List<TPostDueTask> due = listDue(now);
        book.setPickedCount(due.size());
        if (due.isEmpty()) {
            book.setOutcome(OUTCOME_IDLE);
            book.setSentCount(0);
            book.setWithdrawnCount(0);
            book.setMessage("本轮无事：没有到日子的告知事项");
            return 0;
        }
        int sent = 0, withdrawn = 0, gone = 0, channel = 0, broken = 0;
        for (TPostDueTask r : due) {
            int rc;
            try {
                rc = settleOne(r, now);
            } catch (Exception e) {
                // 单条失败跳过继续，不因为卡了这一条就整轮歇住
                broken++;
                continue;
            }
            switch (rc) {
                case RC_SENT:
                    sent++;
                    break;
                case RC_WITHDRAWN:
                    withdrawn++;
                    break;
                case RC_FAIL_GONE:
                    gone++;
                    break;
                case RC_FAIL_CHANNEL:
                    channel++;
                    break;
                default:
                    break;
            }
        }
        book.setOutcome(OUTCOME_DONE);
        book.setSentCount(sent);
        book.setWithdrawnCount(withdrawn);
        book.setMessage(String.format("本轮挑出%d条：送达%d条，撤回%d条，送不下去%d条%s",
                due.size(), sent, withdrawn, gone + channel,
                broken > 0 ? "，单条失败" + broken + "条" : ""));
        return sent;
    }

    /**
     * 一条的落点。回写只动情形、送达时刻、情由与更新时刻；
     * 头回定的日子（due_at）与已钉下的送达时刻（delivered_at）不许被后来的覆盖掉。
     */
    private int settleOne(TPostDueTask r, Date now) {
        // 日子过了还没送出去的：撤回、转成送不下去，情由落在行上
        if (r.getDueAt().before(now)) {
            return markFail(r, now, REASON_OVERDUE) > 0 ? RC_WITHDRAWN : RC_SKIPPED;
        }
        // 开单前这家企业已经注销（或不在册）→ 送不下去
        String goneReason = companyGoneReason(r.getSiteNo());
        if (goneReason != null) {
            return markFail(r, now, goneReason) > 0 ? RC_FAIL_GONE : RC_SKIPPED;
        }
        // 送达途径整季停摆 → 送不下去
        if (Boolean.TRUE.equals(this.postDueProperties.getChannelSuspended())) {
            return markFail(r, now, REASON_CHANNEL_DOWN) > 0 ? RC_FAIL_CHANNEL : RC_SKIPPED;
        }
        // 送达：改口情形、把送达那一刻的时刻钉下来，两样同时落，缺一不算成；
        // 条件里咬住"未开口"，一轮之内同一事项只认头一次落地
        int n = this.postDueTaskMapper.update(null, new UpdateWrapper<TPostDueTask>()
                .set("status", STATUS_DONE)
                .set("delivered_at", now)
                .set("update_time", now)
                .eq("id", r.getId())
                .eq("status", STATUS_WAIT));
        return n > 0 ? RC_SENT : RC_SKIPPED;
    }

    /** 转成送不下去/撤回：情形与情由跟着这行走，情由落在这行上 */
    private int markFail(TPostDueTask r, Date now, String reason) {
        return this.postDueTaskMapper.update(null, new UpdateWrapper<TPostDueTask>()
                .set("status", STATUS_FAIL)
                .set("fail_reason", reason)
                .set("update_time", now)
                .eq("id", r.getId())
                .eq("status", STATUS_WAIT));
    }

    /** 开单前核对企业底档：已注销或不在册给出情由，否则为 null */
    private String companyGoneReason(String siteNo) {
        if (StringUtils.isEmpty(siteNo)) {
            return REASON_COMPANY_UNKNOWN;
        }
        TPostCompanyArch arch = this.postCompanyArchMapper.selectOne(new QueryWrapper<TPostCompanyArch>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0)
                .last("limit 1"));
        if (arch == null) {
            return REASON_COMPANY_UNKNOWN;
        }
        return arch.getStatus() != null && arch.getStatus() == 1 ? REASON_COMPANY_GONE : null;
    }

    /** 三个数当场点清：与屏上、与轮次册同出一回计算 */
    private void fillThreeCounts(TPostDueRound book) {
        Map<String, Integer> counts = countByStatus();
        book.setWaitCount(counts.get("wait"));
        book.setDoneCount(counts.get("done"));
        book.setFailCount(counts.get("fail"));
    }

    @Override
    public Map<String, Integer> countByStatus() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        counts.put("wait", this.postDueTaskMapper.selectCount(new QueryWrapper<TPostDueTask>()
                .eq("status", STATUS_WAIT).eq("del_flag", 0)));
        // 已送达：改了口情形、钉了送达时刻，两样缺一不算成
        counts.put("done", this.postDueTaskMapper.selectCount(new QueryWrapper<TPostDueTask>()
                .eq("status", STATUS_DONE).isNotNull("delivered_at").eq("del_flag", 0)));
        counts.put("fail", this.postDueTaskMapper.selectCount(new QueryWrapper<TPostDueTask>()
                .eq("status", STATUS_FAIL).eq("del_flag", 0)));
        return counts;
    }

    @Override
    public int insertTPostDueTask(TPostDueTask record) {
        if (record == null || StringUtils.isEmpty(record.getItemNo()) || record.getDueAt() == null) {
            return 0;
        }
        // 同一桩事当日第二回进来的不予入账，先落下的那一条原样留着
        List<TPostDueTask> sameItem = this.postDueTaskMapper.selectList(new QueryWrapper<TPostDueTask>()
                .eq("item_no", record.getItemNo())
                .eq("del_flag", 0));
        String today = DateUtil.formatDate(new Date());
        for (TPostDueTask r : sameItem) {
            if (r.getCreateTime() != null && today.equals(DateUtil.formatDate(r.getCreateTime()))) {
                return 0;
            }
        }
        // 入账一律从未开口起；送达时刻与情由不许带进来
        record.setId(null);
        record.setStatus(STATUS_WAIT);
        record.setDeliveredAt(null);
        record.setFailReason(null);
        record.setDelFlag(0);
        return this.postDueTaskMapper.insert(record);
    }

    @Override
    public int updateAdvanceDays(Long id, BigDecimal amount) {
        if (id == null || amount == null || amount.signum() < 0) {
            return 0;
        }
        // 能改的只有提前几日那一栏；把那一天往后推来凑数是绝对不许的
        return this.postDueTaskMapper.update(null, new UpdateWrapper<TPostDueTask>()
                .set("amount", amount)
                .set("update_time", new Date())
                .eq("id", id)
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0));
    }

    @Override
    public TPostDueRound selectLatestRound() {
        return this.postDueRoundMapper.selectOne(new QueryWrapper<TPostDueRound>()
                .eq("del_flag", 0)
                .orderByDesc("run_at")
                .orderByDesc("id")
                .last("limit 1"));
    }

    @Override
    public List<TPostDueRound> selectTPostDueRoundList(TPostDueRound record) {
        QueryWrapper<TPostDueRound> queryWrapper = new QueryWrapper<TPostDueRound>();
        queryWrapper.eq("del_flag", 0);
        if (record != null && record.getOutcome() != null) {
            queryWrapper.eq("outcome", record.getOutcome());
        }
        queryWrapper.orderByDesc("run_at").orderByDesc("id");
        return this.postDueRoundMapper.selectList(queryWrapper);
    }

    /** 把册上的本轮局面抄给出参 */
    private static void copyRound(TPostDueRound from, TPostDueRound to) {
        to.setId(from.getId());
        to.setRunAt(from.getRunAt());
        to.setOutcome(from.getOutcome());
        to.setWaitCount(from.getWaitCount());
        to.setDoneCount(from.getDoneCount());
        to.setFailCount(from.getFailCount());
        to.setPickedCount(from.getPickedCount());
        to.setSentCount(from.getSentCount());
        to.setWithdrawnCount(from.getWithdrawnCount());
        to.setMessage(from.getMessage());
    }
}
