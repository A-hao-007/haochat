package com.ahao.haochat.common.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 群成员表
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("group_member")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群组id
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 成员uid
     */
    @TableField("uid")
    private Long uid;

    /**
     * 成员角色1群主(可撤回，可移除，可解散) 2管理员(可撤回，可移除) 3普通成员
     *
     * @see com.ahao.haochat.common.chat.domain.enums.GroupRoleEnum
     */
    @TableField("role")
    private Integer role;

    /**
     * 成员状态 0正常 1被移除 2主动退出
     *
     * @see com.ahao.haochat.common.chat.domain.enums.GroupMemberStatusEnum
     */
    @TableField("status")
    private Integer status;

    /**
     * 禁言截止时间，空表示未禁言
     */
    @TableField("mute_end_time")
    private Date muteEndTime;

    /**
     * 群内昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private Date updateTime;


}
