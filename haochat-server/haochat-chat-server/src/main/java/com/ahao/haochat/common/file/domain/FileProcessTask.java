package com.ahao.haochat.common.file.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("file_process_task")
public class FileProcessTask {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("asset_id")
    private Long assetId;
    @TableField("task_type")
    private String taskType;
    private Integer status;
    @TableField("retry_count")
    private Integer retryCount;
    @TableField("error_msg")
    private String errorMsg;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
