package com.ahao.haochat.common.user.dao;

import com.ahao.haochat.common.user.domain.entity.Role;
import com.ahao.haochat.common.user.mapper.RoleMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-06-04
 */
@Service
public class RoleDao extends ServiceImpl<RoleMapper, Role> {

}
