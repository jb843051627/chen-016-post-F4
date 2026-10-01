package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 告知事项轮次册对象 t_post_due_round（每轮结局与三个数，撤回这一笔也在这里查）
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_post_due_round")
@ApiModel(value = "TPostDueRound", description = "告知事项轮次册")
public class TPostDueRound implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 本轮出手时刻 */
    @TableField("run_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本轮出手时刻")
    private Date runAt;

    /** 本轮结局 0档口静默未动手 1本轮无事 2已执行 3执行不出去 */
    @TableField("outcome")
    @ApiModelProperty(value = "本轮结局 0档口静默未动手 1本轮无事 2已执行 3执行不出去")
    private Integer outcome;

    /** 未开口条数（本轮算出的三个数之一） */
    @TableField("wait_count")
    @ApiModelProperty(value = "未开口条数（本轮算出的三个数之一）")
    private Integer waitCount;

    /** 已送达条数（本轮算出的三个数之一） */
    @TableField("done_count")
    @ApiModelProperty(value = "已送达条数（本轮算出的三个数之一）")
    private Integer doneCount;

    /** 送不下去条数（本轮算出的三个数之一） */
    @TableField("fail_count")
    @ApiModelProperty(value = "送不下去条数（本轮算出的三个数之一）")
    private Integer failCount;

    /** 本轮挑出条数 */
    @TableField("picked_count")
    @ApiModelProperty(value = "本轮挑出条数")
    private Integer pickedCount;

    /** 本轮送达条数 */
    @TableField("sent_count")
    @ApiModelProperty(value = "本轮送达条数")
    private Integer sentCount;

    /** 本轮撤回条数 */
    @TableField("withdrawn_count")
    @ApiModelProperty(value = "本轮撤回条数")
    private Integer withdrawnCount;

    /** 本轮据实一句话（无事与执行不出去分两说） */
    @TableField("message")
    @ApiModelProperty(value = "本轮据实一句话（无事与执行不出去分两说）")
    private String message;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
    }

    public Integer getOutcome() {
        return outcome;
    }

    public void setOutcome(Integer outcome) {
        this.outcome = outcome;
    }

    public Integer getWaitCount() {
        return waitCount;
    }

    public void setWaitCount(Integer waitCount) {
        this.waitCount = waitCount;
    }

    public Integer getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(Integer doneCount) {
        this.doneCount = doneCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Integer getPickedCount() {
        return pickedCount;
    }

    public void setPickedCount(Integer pickedCount) {
        this.pickedCount = pickedCount;
    }

    public Integer getSentCount() {
        return sentCount;
    }

    public void setSentCount(Integer sentCount) {
        this.sentCount = sentCount;
    }

    public Integer getWithdrawnCount() {
        return withdrawnCount;
    }

    public void setWithdrawnCount(Integer withdrawnCount) {
        this.withdrawnCount = withdrawnCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
