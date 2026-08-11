package com.ahao.haochat.common.chatai.mapper;

import com.ahao.haochat.common.chatai.agent.task.AgentTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * <p>
 * Agent 任务状态机 Mapper 接口
 * </p>
 */
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Update("UPDATE agent_task SET status=1, last_run_time=#{now}, lease_until=#{leaseUntil}, "
            + "lease_token=#{leaseToken}, run_count=run_count+1 "
            + "WHERE id=#{taskId} AND next_run_time<=#{now} AND "
            + "(status IN (0,2) OR (status=1 AND lease_until IS NOT NULL AND lease_until<=#{now}))")
    int claimDue(@Param("taskId") Long taskId, @Param("now") Date now,
                 @Param("leaseUntil") Date leaseUntil, @Param("leaseToken") String leaseToken);

    @Update("UPDATE agent_task SET status=3, lease_until=NULL, lease_token=NULL, last_error=NULL "
            + "WHERE id=#{taskId} AND status=1 AND lease_token=#{leaseToken}")
    int markDone(@Param("taskId") Long taskId, @Param("leaseToken") String leaseToken);
}
