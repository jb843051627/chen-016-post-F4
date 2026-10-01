package com.fc.v2.common.quartz.task;

import cn.hutool.core.date.DateUtil;
import com.fc.v2.service.ITPostDueTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 到期告知事项定时调度：每年头一季度回执要的三类事（证照到期、网点满一年核验、
 * 责令改正期限将至）交给系统按日子去跑。sys_job 里配 postDueNoticeTask.runOnce() 即可。
 *
 * @author fuce
 */
@Component("postDueNoticeTask")
public class PostDueNoticeTask {

    @Autowired
    private ITPostDueTaskService postDueTaskService;

    /**
     * 跑一轮。档口静默、本轮无事、已执行、执行不出去四种结局都落在轮次册上，
     * 跑没跑成看轮次册给回什么。
     */
    public void runOnce() {
        int sent = postDueTaskService.runOnce(new Date());
        System.out.println("到期告知事项本轮执行完，送达条数：" + sent + "，执行时间：" + DateUtil.now());
    }
}
