package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TPostDueRound;
import com.fc.v2.model.auto.TPostDueTask;
import com.fc.v2.service.ITPostDueTaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 到期告知事项 Controller。
 * 界面既添不上一条，也撤不掉一条：只读翻单子、看本轮局面、跑一轮、改提前几日。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "到期告知事项")
@Controller
@RequestMapping("/PostDueTaskController")
public class PostDueTaskController extends BaseController {

    private final String prefix = "admin/postDueTask";

    @Autowired
    private ITPostDueTaskService postDueTaskService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("post:postDueTask:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "到期告知事项集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询（未开口/已送达/送不下去都翻得着）")
    @GetMapping("/list")
    @RequiresPermissions("post:postDueTask:list")
    @ResponseBody
    public ResultTable list(TPostDueTask record) {
        startPage();
        com.github.pagehelper.PageInfo<TPostDueTask> page =
                new com.github.pagehelper.PageInfo<TPostDueTask>(postDueTaskService.selectTPostDueTaskList(record));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "一屏局面", notes = "屏上三条数与库里三条数同出一回计算；屏上只管本轮")
    @GetMapping("/stat")
    @RequiresPermissions("post:postDueTask:list")
    @ResponseBody
    public AjaxResult stat() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("counts", postDueTaskService.countByStatus());
        data.put("round", postDueTaskService.selectLatestRound());
        data.put("inWindow", postDueTaskService.isInWindow(new Date()));
        return retobject(200, data);
    }

    @Log(title = "到期告知事项轮次册查询", action = "roundList")
    @ApiOperation(value = "轮次册分页查询", notes = "每轮结局与三个数，撤回这一笔在这里查")
    @GetMapping("/roundList")
    @RequiresPermissions("post:postDueTask:list")
    @ResponseBody
    public ResultTable roundList(TPostDueRound record) {
        startPage();
        com.github.pagehelper.PageInfo<TPostDueRound> page =
                new com.github.pagehelper.PageInfo<TPostDueRound>(postDueTaskService.selectTPostDueRoundList(record));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "到期告知事项手动跑一轮", action = "run")
    @ApiOperation(value = "跑一轮", notes = "跑没跑成看它给回什么（本轮轮次册）")
    @PostMapping("/run")
    @RequiresPermissions("post:postDueTask:run")
    @ResponseBody
    public AjaxResult run() {
        TPostDueRound round = new TPostDueRound();
        int sent = postDueTaskService.runOnce(new Date(), round);
        if (sent < 0) {
            return error(500, round.getMessage() == null ? "执行不出去" : round.getMessage());
        }
        return success(200, round.getMessage(), round);
    }

    @Log(title = "到期告知事项修改提前几日", action = "editAmount")
    @ApiOperation(value = "修改提前几日", notes = "能改的只有提前几日那一栏，头回定的日子不许后推")
    @PostMapping("/editAmount")
    @RequiresPermissions("post:postDueTask:edit")
    @ResponseBody
    public AjaxResult editAmount(Long id, BigDecimal amount) {
        return toAjax(postDueTaskService.updateAdvanceDays(id, amount));
    }
}
