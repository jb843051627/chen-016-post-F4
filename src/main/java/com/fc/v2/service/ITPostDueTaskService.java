package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TPostDueRound;
import com.fc.v2.model.auto.TPostDueTask;

/**
 * 经营许可效期与备案年度核验到期告知事项 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITPostDueTaskService {

    /** 按主键回查条目 */
    TPostDueTask selectTPostDueTaskById(Long id);

    /** 页面上翻单子（未开口/已送达/送不下去都翻得着），只读 */
    List<TPostDueTask> selectTPostDueTaskList(TPostDueTask record);

    /** 该时刻可处理的条目（起办日已到 + 未开口 + 未删除）——翻单子与回写同用这一把尺 */
    List<TPostDueTask> listDue(Date at);

    /** 该时刻是否落在出手段内（false＝档口静默：夜里/法定节假日/段外） */
    boolean isInWindow(Date at);

    /** 执行一次，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /** 同名重载：执行一次，本轮局面（轮次册）由出参带回并落库 */
    int runOnce(Date at, TPostDueRound round);

    /** 库里三个数（wait未开口/done已送达/fail送不下去），与轮次册同出一回计算 */
    Map<String, Integer> countByStatus();

    /** 入账：同一桩事当日第二回进来的不予入账，先落下的那一条原样留着 */
    int insertTPostDueTask(TPostDueTask record);

    /** 能改的只有提前几日那一栏（头回定的日子不许后推），且只对未开口的条目 */
    int updateAdvanceDays(Long id, BigDecimal amount);

    /** 最近一轮轮次册（屏上只管本轮） */
    TPostDueRound selectLatestRound();

    /** 轮次册翻页（撤回这一笔在这里查） */
    List<TPostDueRound> selectTPostDueRoundList(TPostDueRound record);
}
