package com.ahao.haochat.common.user.dao;

import cn.hutool.core.collection.CollectionUtil;
import com.ahao.haochat.common.common.domain.enums.NormalOrNoEnum;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.utils.CursorUtils;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.ChatActiveStatusEnum;
import com.ahao.haochat.common.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
@Service
public class UserDao extends ServiceImpl<UserMapper, User> {

    public User getByOpenId(String openId) {
        LambdaQueryWrapper<User> wrapper = new QueryWrapper<User>().lambda().eq(User::getOpenId, openId);
        return getOne(wrapper);
    }

    public User getByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    /**
     * 按关键词搜索用户（模糊匹配用户名和昵称）
     */
    public List<User> searchByKeyword(String keyword, Long excludeUid) {
        return lambdaQuery()
                .and(w -> w.like(User::getUsername, keyword).or().like(User::getName, keyword))
                .ne(excludeUid != null, User::getId, excludeUid)
                .eq(User::getStatus, NormalOrNoEnum.NORMAL.getStatus())
                .last("limit 20")
                .select(User::getId, User::getUsername, User::getName, User::getAvatar, User::getActiveStatus)
                .list();
    }

    public void modifyName(Long uid, String name) {
        User update = new User();
        update.setId(uid);
        update.setName(name);
        updateById(update);
    }

    public void wearingBadge(Long uid, Long badgeId) {
        User update = new User();
        update.setId(uid);
        update.setItemId(badgeId);
        updateById(update);
    }

    public User getByName(String name) {
        return lambdaQuery().eq(User::getName, name).one();
    }

    public List<User> getMemberList() {
        return lambdaQuery()
                .eq(User::getStatus, NormalOrNoEnum.NORMAL.getStatus())
                .orderByDesc(User::getLastOptTime)//最近活跃的1000个人，可以用lastOptTime字段，但是该字段没索引，updateTime可平替
                .last("limit 1000")//毕竟是大群聊，人数需要做个限制
                .select(User::getId, User::getName, User::getAvatar)
                .list();

    }

    public List<User> getFriendList(List<Long> uids) {
        return lambdaQuery()
                .in(User::getId, uids)
                .select(User::getId, User::getActiveStatus, User::getName, User::getAvatar)
                .list();

    }

    public Integer getOnlineCount() {
        return getOnlineCount(null);
    }

    public Integer getOnlineCount(List<Long> memberUidList) {
        return lambdaQuery()
                .eq(User::getActiveStatus, ChatActiveStatusEnum.ONLINE.getStatus())
                .in(CollectionUtil.isNotEmpty(memberUidList), User::getId, memberUidList)
                .count();
    }

    public CursorPageBaseResp<User> getCursorPage(List<Long> memberUidList, CursorPageBaseReq request, ChatActiveStatusEnum online) {
        return CursorUtils.getCursorPageByMysql(this, request, wrapper -> {
            wrapper.eq(User::getActiveStatus, online.getStatus());//筛选上线或者离线的
            wrapper.in(CollectionUtils.isNotEmpty(memberUidList), User::getId, memberUidList);//普通群对uid列表做限制
        }, User::getLastOptTime);
    }

}
