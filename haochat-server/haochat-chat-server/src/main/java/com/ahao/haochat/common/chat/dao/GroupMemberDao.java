package com.ahao.haochat.common.chat.dao;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.ahao.haochat.common.chat.domain.entity.GroupMember;
import com.ahao.haochat.common.chat.domain.enums.GroupMemberStatusEnum;
import com.ahao.haochat.common.chat.domain.enums.GroupRoleEnum;
import com.ahao.haochat.common.chat.mapper.GroupMemberMapper;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ahao.haochat.common.chat.domain.enums.GroupRoleEnum.ADMIN_LIST;

/**
 * <p>
 * 群成员表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-07-16
 */
@Service
public class GroupMemberDao extends ServiceImpl<GroupMemberMapper, GroupMember> {

    @Autowired
    @Lazy
    private GroupMemberCache groupMemberCache;

    public List<Long> getMemberUidList(Long groupId) {
        List<GroupMember> list = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .select(GroupMember::getUid)
                .list();
        return list.stream().map(GroupMember::getUid).collect(Collectors.toList());
    }

    public List<Long> getMemberBatch(Long groupId, List<Long> uidList) {
        List<GroupMember> list = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .in(GroupMember::getUid, uidList)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .select(GroupMember::getUid)
                .list();
        return list.stream().map(GroupMember::getUid).collect(Collectors.toList());
    }

    /**
     * 批量获取成员群角色
     *
     * @param groupId 群ID
     * @param uidList 用户列表
     * @return 成员群角色列表
     */
    public Map<Long, Integer> getMemberMapRole(Long groupId, List<Long> uidList) {
        List<GroupMember> list = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .in(GroupMember::getUid, uidList)
                .in(GroupMember::getRole, ADMIN_LIST)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .select(GroupMember::getUid, GroupMember::getRole)
                .list();
        return list.stream().collect(Collectors.toMap(GroupMember::getUid, GroupMember::getRole));
    }

    public GroupMember getMember(Long groupId, Long uid) {
        return lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .one();
    }

    public List<GroupMember> getSelfGroup(Long uid) {
        return lambdaQuery()
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getRole, GroupRoleEnum.LEADER.getType())
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .list();
    }

    /**
     * 获取用户加入的所有群（不限角色），用于"我的群组"列表
     */
    public List<GroupMember> getMyGroups(Long uid) {
        return lambdaQuery()
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .list();
    }

    /**
     * 批量获取成员群内昵称
     */
    public Map<Long, String> getMemberMapNickname(Long groupId, List<Long> uidList) {
        List<GroupMember> list = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .in(GroupMember::getUid, uidList)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .select(GroupMember::getUid, GroupMember::getNickname)
                .list();
        return list.stream()
                .filter(m -> m.getNickname() != null)
                .collect(Collectors.toMap(GroupMember::getUid, GroupMember::getNickname));
    }

    /**
     * 设置自己的群内昵称
     */
    public void updateNickname(Long groupId, Long uid, String nickname) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getNickname, nickname);
        this.update(wrapper);
    }

    /**
     * 转让群主：原群主降为管理员，目标成员升为群主
     */
    public void transferLord(Long groupId, Long fromUid, Long toUid) {
        LambdaUpdateWrapper<GroupMember> demote = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, fromUid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getRole, GroupRoleEnum.MANAGER.getType());
        this.update(demote);
        LambdaUpdateWrapper<GroupMember> promote = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, toUid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getRole, GroupRoleEnum.LEADER.getType());
        this.update(promote);
    }

    /**
     * 判断用户是否在房间中
     *
     * @param roomId  房间ID
     * @param uidList 用户ID
     * @return 是否在群聊中
     */
    public Boolean isGroupShip(Long roomId, List<Long> uidList) {
        List<Long> memberUidList = groupMemberCache.getMemberUidList(roomId);
        return memberUidList.containsAll(uidList);
    }

    /**
     * 是否是群主
     *
     * @param id  群组ID
     * @param uid 用户ID
     * @return 是否是群主
     */
    public Boolean isLord(Long id, Long uid) {
        GroupMember groupMember = this.lambdaQuery()
                .eq(GroupMember::getGroupId, id)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getRole, GroupRoleEnum.LEADER.getType())
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .one();
        return ObjectUtil.isNotNull(groupMember);
    }

    /**
     * 是否是管理员
     *
     * @param id  群组ID
     * @param uid 用户ID
     * @return 是否是管理员
     */
    public Boolean isManager(Long id, Long uid) {
        GroupMember groupMember = this.lambdaQuery()
                .eq(GroupMember::getGroupId, id)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getRole, GroupRoleEnum.MANAGER.getType())
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .one();
        return ObjectUtil.isNotNull(groupMember);
    }

    /**
     * 获取管理员uid列表
     *
     * @param id 群主ID
     * @return 管理员uid列表
     */
    public List<Long> getManageUidList(Long id) {
        return this.lambdaQuery()
                .eq(GroupMember::getGroupId, id)
                .eq(GroupMember::getRole, GroupRoleEnum.MANAGER.getType())
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .list()
                .stream()
                .map(GroupMember::getUid)
                .collect(Collectors.toList());
    }

    /**
     * 增加管理员
     *
     * @param id      群组ID
     * @param uidList 用户列表
     */
    public void addAdmin(Long id, List<Long> uidList) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, id)
                .in(GroupMember::getUid, uidList)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getRole, GroupRoleEnum.MANAGER.getType());
        this.update(wrapper);
    }

    /**
     * 撤销管理员
     *
     * @param id      群组ID
     * @param uidList 用户列表
     */
    public void revokeAdmin(Long id, List<Long> uidList) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, id)
                .in(GroupMember::getUid, uidList)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getRole, GroupRoleEnum.MEMBER.getType());
        this.update(wrapper);
    }

    public boolean markRemoved(Long groupId, Long uid) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getStatus, GroupMemberStatusEnum.REMOVED.getStatus())
                .set(GroupMember::getUpdateTime, new Date());
        return update(wrapper);
    }

    public boolean markExited(Long groupId, Long uid) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getStatus, GroupMemberStatusEnum.EXITED.getStatus())
                .set(GroupMember::getUpdateTime, new Date());
        return update(wrapper);
    }

    public void mute(Long groupId, Long uid, Date muteEndTime) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getMuteEndTime, muteEndTime)
                .set(GroupMember::getUpdateTime, new Date());
        update(wrapper);
    }

    public void unmute(Long groupId, Long uid) {
        LambdaUpdateWrapper<GroupMember> wrapper = new UpdateWrapper<GroupMember>().lambda()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUid, uid)
                .eq(GroupMember::getStatus, GroupMemberStatusEnum.NORMAL.getStatus())
                .set(GroupMember::getMuteEndTime, null)
                .set(GroupMember::getUpdateTime, new Date());
        update(wrapper);
    }

    /**
     * 根据群组ID删除群成员
     *
     * @param groupId 群组ID
     * @param uidList 群成员列表
     * @return 是否删除成功
     */
    public Boolean removeByGroupId(Long groupId, List<Long> uidList) {
        if (CollectionUtil.isNotEmpty(uidList)) {
            LambdaQueryWrapper<GroupMember> wrapper = new QueryWrapper<GroupMember>()
                    .lambda()
                    .eq(GroupMember::getGroupId, groupId)
                    .in(GroupMember::getUid, uidList);
            return this.remove(wrapper);
        }
        return false;
    }
}
