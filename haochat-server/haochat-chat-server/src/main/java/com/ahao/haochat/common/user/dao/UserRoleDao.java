package com.ahao.haochat.common.user.dao;

import com.ahao.haochat.common.user.domain.entity.UserRole;
import com.ahao.haochat.common.user.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 用户角色关系表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-06-04
 */
@Service
public class UserRoleDao extends ServiceImpl<UserRoleMapper, UserRole> {
    public List<UserRole> listByUid(Long uid) {
        return lambdaQuery()
                .eq(UserRole::getUid, Objects.requireNonNull(uid))
                .list();
    }
}
