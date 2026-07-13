package com.ahao.haochat.common.chat.service.impl;

import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.chat.dao.GroupMemberDao;
import com.ahao.haochat.common.chat.dao.GroupOperationLogDao;
import com.ahao.haochat.common.chat.dao.RoomDao;
import com.ahao.haochat.common.chat.dao.RoomGroupDao;
import com.ahao.haochat.common.chat.domain.entity.GroupMember;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomGroup;
import com.ahao.haochat.common.chat.domain.enums.GroupOperationTypeEnum;
import com.ahao.haochat.common.chat.domain.enums.GroupRoleEnum;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.ChatMessageReq;
import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.admin.AdminRevokeReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberExitReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberMuteReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.TransferLordReq;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.IGroupMemberService;
import com.ahao.haochat.common.chat.service.adapter.MemberAdapter;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.common.exception.GroupErrorEnum;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.event.GroupMemberLeftEvent;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.domain.vo.response.ws.WSMemberChange;
import com.ahao.haochat.common.user.service.cache.UserInfoCache;
import com.ahao.haochat.common.user.service.impl.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.ahao.haochat.common.chat.constant.GroupConst.MAX_MANAGE_COUNT;

@Service
public class GroupMemberServiceImpl implements IGroupMemberService {

    @Autowired
    private GroupMemberDao groupMemberDao;
    @Autowired
    private RoomGroupDao roomGroupDao;
    @Autowired
    private RoomDao roomDao;
    @Autowired
    private ContactDao contactDao;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private PushService pushService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private UserInfoCache userInfoCache;
    @Autowired
    private GroupOperationLogDao groupOperationLogDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAdmin(Long uid, AdminAddReq request) {
        RoomGroup roomGroup = getRoomGroup(request.getRoomId());
        assertLord(roomGroup, uid);
        for (Long targetUid : request.getUidList()) {
            GroupMember target = requireMember(roomGroup.getId(), targetUid);
            AssertUtil.equal(target.getRole(), GroupRoleEnum.MEMBER.getType(), "只能把普通成员设置为管理员");
        }
        List<Long> manageUidList = groupMemberDao.getManageUidList(roomGroup.getId());
        long newAdminCount = request.getUidList().stream()
                .filter(targetUid -> !manageUidList.contains(targetUid))
                .count();
        AssertUtil.isFalse(manageUidList.size() + newAdminCount > MAX_MANAGE_COUNT, GroupErrorEnum.MANAGE_COUNT_EXCEED);
        groupMemberDao.addAdmin(roomGroup.getId(), request.getUidList());
        request.getUidList().forEach(targetUid -> recordAndSend(roomGroup, uid, targetUid,
                GroupOperationTypeEnum.ADD_ADMIN, userName(uid) + "将" + userName(targetUid) + "设为管理员"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAdmin(Long uid, AdminRevokeReq request) {
        RoomGroup roomGroup = getRoomGroup(request.getRoomId());
        assertLord(roomGroup, uid);
        for (Long targetUid : request.getUidList()) {
            GroupMember target = requireMember(roomGroup.getId(), targetUid);
            AssertUtil.equal(target.getRole(), GroupRoleEnum.MANAGER.getType(), "只能取消管理员身份");
        }
        groupMemberDao.revokeAdmin(roomGroup.getId(), request.getUidList());
        request.getUidList().forEach(targetUid -> recordAndSend(roomGroup, uid, targetUid,
                GroupOperationTypeEnum.REVOKE_ADMIN, userName(uid) + "取消了" + userName(targetUid) + "的管理员身份"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exitGroup(Long uid, MemberExitReq request) {
        RoomGroup roomGroup = getRoomGroup(request.getRoomId());
        Room room = roomDao.getById(request.getRoomId());
        AssertUtil.isNotEmpty(room, GroupErrorEnum.GROUP_NOT_EXIST);
        AssertUtil.isFalse(room.isHotRoom(), GroupErrorEnum.NOT_ALLOWED_FOR_EXIT_GROUP);
        GroupMember self = requireMember(roomGroup.getId(), uid);
        AssertUtil.isFalse(Objects.equals(self.getRole(), GroupRoleEnum.LEADER.getType()), "群主不能直接退出，请先转让群主");

        List<Long> memberUidList = groupMemberCache.getMemberUidList(roomGroup.getRoomId());
        AssertUtil.isTrue(groupMemberDao.markExited(roomGroup.getId(), uid), "退出群聊失败");
        contactDao.remove(uid, roomGroup.getRoomId());
        String content = userName(uid) + "退出了群聊";
        groupOperationLogDao.record(roomGroup.getRoomId(), roomGroup.getId(), uid, uid, GroupOperationTypeEnum.EXIT, content);
        applicationEventPublisher.publishEvent(new GroupMemberLeftEvent(this, roomGroup.getId(), roomGroup.getRoomId(), uid, uid));
        sendSystemMsg(roomGroup.getRoomId(), content);
        WSBaseResp<WSMemberChange> ws = MemberAdapter.buildMemberRemoveWS(roomGroup.getRoomId(), uid);
        pushService.sendPushMsg(ws, memberUidList);
        groupMemberCache.evictMemberUidList(room.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLord(Long uid, TransferLordReq request) {
        Long roomId = request.getRoomId();
        Long targetUid = request.getTargetUid();
        AssertUtil.isFalse(targetUid.equals(uid), GroupErrorEnum.CANNOT_TRANSFER_TO_SELF);
        RoomGroup roomGroup = getRoomGroup(roomId);
        assertLord(roomGroup, uid);
        requireMember(roomGroup.getId(), targetUid);
        groupMemberDao.transferLord(roomGroup.getId(), uid, targetUid);
        roomGroupDao.updateOwner(roomGroup.getId(), targetUid);
        groupMemberCache.evictMemberUidList(roomId);
        recordAndSend(roomGroup, uid, targetUid, GroupOperationTypeEnum.TRANSFER_OWNER,
                userName(uid) + "将群主转让给" + userName(targetUid));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMember(Long uid, MemberMuteReq request) {
        RoomGroup roomGroup = getRoomGroup(request.getRoomId());
        GroupMember operator = requireMember(roomGroup.getId(), uid);
        GroupMember target = requireMember(roomGroup.getId(), request.getUid());
        AssertUtil.isTrue(canManage(operator, target), GroupErrorEnum.NOT_ALLOWED_OPERATION);
        AssertUtil.isFalse(Objects.equals(target.getRole(), GroupRoleEnum.LEADER.getType()), "不能禁言群主");
        AssertUtil.isTrue(request.getMinutes() != null && request.getMinutes() > 0, "禁言时长必须大于0分钟");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, request.getMinutes());
        groupMemberDao.mute(roomGroup.getId(), request.getUid(), calendar.getTime());
        recordAndSend(roomGroup, uid, request.getUid(), GroupOperationTypeEnum.MUTE,
                userName(uid) + "禁言了" + userName(request.getUid()) + request.getMinutes() + "分钟");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmuteMember(Long uid, MemberMuteReq request) {
        RoomGroup roomGroup = getRoomGroup(request.getRoomId());
        GroupMember operator = requireMember(roomGroup.getId(), uid);
        GroupMember target = requireMember(roomGroup.getId(), request.getUid());
        AssertUtil.isTrue(canManage(operator, target), GroupErrorEnum.NOT_ALLOWED_OPERATION);
        groupMemberDao.unmute(roomGroup.getId(), request.getUid());
        recordAndSend(roomGroup, uid, request.getUid(), GroupOperationTypeEnum.UNMUTE,
                userName(uid) + "取消了" + userName(request.getUid()) + "的禁言");
    }

    private RoomGroup getRoomGroup(Long roomId) {
        RoomGroup roomGroup = roomGroupDao.getByRoomId(roomId);
        AssertUtil.isNotEmpty(roomGroup, GroupErrorEnum.GROUP_NOT_EXIST);
        return roomGroup;
    }

    private void assertLord(RoomGroup roomGroup, Long uid) {
        AssertUtil.isTrue(groupMemberDao.isLord(roomGroup.getId(), uid), GroupErrorEnum.NOT_ALLOWED_OPERATION);
    }

    private GroupMember requireMember(Long groupId, Long uid) {
        GroupMember member = groupMemberDao.getMember(groupId, uid);
        AssertUtil.isNotEmpty(member, GroupErrorEnum.USER_NOT_IN_GROUP);
        return member;
    }

    private boolean canManage(GroupMember operator, GroupMember target) {
        if (Objects.equals(operator.getRole(), GroupRoleEnum.LEADER.getType())) {
            return !Objects.equals(target.getUid(), operator.getUid());
        }
        if (Objects.equals(operator.getRole(), GroupRoleEnum.MANAGER.getType())) {
            return Objects.equals(target.getRole(), GroupRoleEnum.MEMBER.getType());
        }
        return false;
    }

    private void recordAndSend(RoomGroup roomGroup, Long operatorUid, Long targetUid, GroupOperationTypeEnum type, String content) {
        groupOperationLogDao.record(roomGroup.getRoomId(), roomGroup.getId(), operatorUid, targetUid, type, content);
        sendSystemMsg(roomGroup.getRoomId(), content);
    }

    private void sendSystemMsg(Long roomId, String content) {
        ChatMessageReq req = new ChatMessageReq();
        req.setRoomId(roomId);
        req.setMsgType(MessageTypeEnum.SYSTEM.getType());
        req.setBody(content);
        chatService.sendMsg(req, User.UID_SYSTEM);
    }

    private String userName(Long uid) {
        User user = userInfoCache.get(uid);
        return user == null ? String.valueOf(uid) : user.getName();
    }
}
