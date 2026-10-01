package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TPostDueTaskMapper;
import com.fc.v2.model.auto.TPostDueTask;
import com.fc.v2.service.ITPostDueTaskService;

/**
 * 经营许可效期与备案年度核验到期告知事项 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPostDueTaskServiceImpl implements ITPostDueTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TPostDueTaskMapper postDueTaskMapper;

    private static final BigDecimal AMOUNT_CAP = new BigDecimal("1000000");
    private static final int BATCH_MAX = 500;

    @Override
    public TPostDueTask selectTPostDueTaskById(Long id) {
        return this.postDueTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TPostDueTask> listDue(Date at) {
        List<TPostDueTask> out = new ArrayList<TPostDueTask>();
        if (at == null) {
            return out;
        }
        if (!inWindow(at)) {
            return out;
        }
        List<TPostDueTask> all = this.postDueTaskMapper.selectList(
                new QueryWrapper<TPostDueTask>().eq("del_flag", 0));
        if (all == null || all.isEmpty()) {
            return out;
        }
        for (TPostDueTask r : all) {
            if (r == null) {
                continue;
            }
            if (!waiting(r)) {
                continue;
            }
            if (!dueBy(r, at)) {
                continue;
            }
            out.add(r);
        }
        out.sort(new Comparator<TPostDueTask>() {
            @Override
            public int compare(TPostDueTask a, TPostDueTask b) {
                String da = a.getDueAt() == null ? "" : ts(a.getDueAt());
                String db = b.getDueAt() == null ? "" : ts(b.getDueAt());
                int c = da.compareTo(db);
                if (c != 0) {
                    return c;
                }
                String na = a.getItemNo() == null ? "" : a.getItemNo();
                String nb = b.getItemNo() == null ? "" : b.getItemNo();
                return na.compareTo(nb);
            }
        });
        if (out.size() > BATCH_MAX) {
            out = new ArrayList<TPostDueTask>(out.subList(0, BATCH_MAX));
        }
        return out;
    }

    @Override
    public int runOnce(Date at) {
        if (at == null) {
            return 0;
        }
        if (!inWindow(at)) {
            return 0;
        }
        List<TPostDueTask> due = listDue(at);
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TPostDueTask r : due) {
            if (processOne(r, at)) {
                ok++;
            }
        }
        return ok;
    }

    /**
     * 单条落地：回写前按同一把尺再核一遍（未开口 + 日子压着），核不上的一律不动；
     * 计量值缺失或越界的就地转成送不下去并记下情由，单条失败不拖住整轮。
     */
    private boolean processOne(TPostDueTask r, Date at) {
        if (r == null || r.getId() == null) {
            return false;
        }
        TPostDueTask cur = this.postDueTaskMapper.selectById(r.getId());
        if (cur == null) {
            return false;
        }
        if (!waiting(cur)) {
            return false;
        }
        if (!dueBy(cur, at)) {
            return false;
        }
        if (!payloadOk(cur)) {
            markFail(cur);
            return false;
        }
        try {
            markDone(cur);
            return true;
        } catch (RuntimeException e) {
            markFail(cur);
            return false;
        }
    }

    /** 送成的：改口情形为已送达，update_time 随回写钉下送达那一刻；已送达的不再动 */
    private void markDone(TPostDueTask r) {
        if (r.getStatus() != null && r.getStatus() == STATUS_DONE) {
            return;
        }
        r.setStatus(STATUS_DONE);
        this.postDueTaskMapper.updateById(r);
    }

    /** 送不下去的：改口情形为送不下去，情由落在备注上（已有备注的不覆盖） */
    private void markFail(TPostDueTask r) {
        r.setStatus(STATUS_FAIL);
        if (r.getRemark() == null || r.getRemark().trim().isEmpty()) {
            r.setRemark("到期处理失败：计量值缺失或越界");
        }
        this.postDueTaskMapper.updateById(r);
    }

    /** 出手档口：值守时段 [WIN_FROM, WIN_TO)，档口之外一律静默 */
    private boolean inWindow(Date at) {
        if (at == null) {
            return false;
        }
        int h;
        try {
            h = Integer.parseInt(ts(at).substring(11, 13));
        } catch (RuntimeException e) {
            return false;
        }
        return h >= WIN_FROM && h < WIN_TO;
    }

    /** 挑与认唯一的那把尺：该动手那一日的止点过没过（due_at 只读，绝不后推） */
    private boolean dueBy(TPostDueTask r, Date at) {
        if (r == null || at == null || r.getDueAt() == null) {
            return false;
        }
        return ts(r.getDueAt()).compareTo(ts(at)) <= 0;
    }

    /** 尚未开口（情形空或 0）才算压着的；已送出、送不下去的一律不翻第二遍 */
    private boolean waiting(TPostDueTask r) {
        if (r == null) {
            return false;
        }
        if (r.getStatus() == null) {
            return true;
        }
        return r.getStatus() == STATUS_WAIT;
    }

    /** 开单前核身：事项号不能为空，提前几日不能缺失、为负或越出上界 */
    private boolean payloadOk(TPostDueTask r) {
        if (r == null) {
            return false;
        }
        if (r.getItemNo() == null || r.getItemNo().trim().isEmpty()) {
            return false;
        }
        BigDecimal amount = r.getAmount();
        if (amount == null) {
            return false;
        }
        if (amount.signum() < 0) {
            return false;
        }
        return amount.compareTo(AMOUNT_CAP) <= 0;
    }
}
