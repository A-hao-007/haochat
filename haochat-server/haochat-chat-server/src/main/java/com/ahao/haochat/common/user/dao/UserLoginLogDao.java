package com.ahao.haochat.common.user.dao;

import com.ahao.haochat.common.user.domain.entity.UserLoginLog;
import com.ahao.haochat.common.user.mapper.UserLoginLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserLoginLogDao extends ServiceImpl<UserLoginLogMapper, UserLoginLog> {
}
