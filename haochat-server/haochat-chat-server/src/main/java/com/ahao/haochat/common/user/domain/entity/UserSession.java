package com.ahao.haochat.common.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_session")
public class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long uid;
    private String refreshTokenHash;
    private String deviceId;
    private String deviceName;
    private String userAgent;
    private String ip;
    private String ipRegion;
    /**
     * 0 active, 1 revoked.
     */
    private Integer status;
    private Date expireTime;
    private Date lastActiveTime;
    private Date createTime;
    private Date updateTime;
}
