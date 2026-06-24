package com.ahao.haochat.common.chat.mapper;

import com.ahao.haochat.common.chat.domain.entity.Contact;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 会话列表 Mapper 接口
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
public interface ContactMapper extends BaseMapper<Contact> {

    void refreshOrCreateActiveTime(@Param("roomId") Long roomId, @Param("memberUidList") List<Long> memberUidList, @Param("msgId") Long msgId, @Param("activeTime") Date activeTime);
}
