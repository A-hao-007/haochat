package com.ahao.haochat.common.chat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.chat.dao.GroupMemberDao;
import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.dto.GroupNoticeDTO;
import com.ahao.haochat.common.chat.domain.dto.RoomBaseInfo;
import com.ahao.haochat.common.chat.domain.entity.*;
import com.ahao.haochat.common.chat.domain.enums.GroupRoleAPPEnum;
import com.ahao.haochat.common.chat.domain.enums.GroupRoleEnum;
import com.ahao.haochat.common.chat.domain.enums.HotFlagEnum;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.ChatMessageMemberReq;
import com.ahao.haochat.common.chat.domain.vo.request.GroupAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberAddReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberDelReq;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberReq;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberListResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatRoomResp;
import com.ahao.haochat.common.chat.domain.vo.response.MemberResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.RoomAppService;
import com.ahao.haochat.common.chat.service.RoomService;
import com.ahao.haochat.common.chat.service.adapter.ChatAdapter;
import com.ahao.haochat.common.chat.service.adapter.MemberAdapter;
import com.ahao.haochat.common.chat.service.adapter.RoomAdapter;
import com.ahao.haochat.common.chat.service.cache.*;
import com.ahao.haochat.common.chatai.handler.ChatAIHandlerFactory;
import com.ahao.haochat.common.chat.service.strategy.msg.AbstractMsgHandler;
import com.ahao.haochat.common.chat.service.strategy.msg.MsgHandlerFactory;
import com.ahao.haochat.common.common.annotation.RedissonLock;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.event.GroupMemberAddEvent;
import com.ahao.haochat.common.common.exception.GroupErrorEnum;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.RoleEnum;
import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.domain.vo.response.ws.ChatMemberResp;
import com.ahao.haochat.common.user.domain.vo.response.ws.WSMemberChange;
import com.ahao.haochat.common.user.service.IRoleService;
import com.ahao.haochat.common.user.service.cache.UserCache;
import com.ahao.haochat.common.user.service.cache.UserInfoCache;
import com.ahao.haochat.common.user.service.impl.PushService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description:
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-07-22
 */
@Service
public class RoomAppServiceImpl implements RoomAppService {

    @Autowired
    private ContactDao contactDao;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private RoomGroupCache roomGroupCache;
    @Autowired
    private RoomFriendCache roomFriendCache;
    @Autowired
    private UserInfoCache userInfoCache;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private HotRoomCache hotRoomCache;
    @Autowired
    private UserCache userCache;
    @Autowired
    private GroupMemberDao groupMemberDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ChatService chatService;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private RoomService roomService;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private PushService pushService;
    @Autowired
    private com.ahao.haochat.common.chat.dao.RoomGroupDao roomGroupDao;

    @Override
    public CursorPageBaseResp<ChatRoomResp> getContactPage(CursorPageBaseReq request, Long uid) {
        // 查出用户要展示的会话列表
        CursorPageBaseResp<Long> page;
        if (Objects.nonNull(uid)) {
            Double hotEnd = getCursorOrNull(request.getCursor());
            Double hotStart = null;
            // 用户基础会话
            CursorPageBaseResp<Contact> contactPage = contactDao.getContactPage(uid, request);
            List<Long> baseRoomIds = contactPage.getList().stream().map(Contact::getRoomId).collect(Collectors.toList());
            if (!contactPage.getIsLast()) {
                hotStart = getCursorOrNull(contactPage.getCursor());
            }
            // 热门房间
            Set<ZSetOperations.TypedTuple<String>> typedTuples = hotRoomCache.getRoomRange(hotStart, hotEnd);
            List<Long> hotRoomIds = Optional.ofNullable(typedTuples).orElse(Collections.emptySet())
                    .stream()
                    .map(ZSetOperations.TypedTuple::getValue)
                    .map(this::parseRoomId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            baseRoomIds.addAll(hotRoomIds);
            // 基础会话和热门房间合并
            page = CursorPageBaseResp.init(contactPage, baseRoomIds);
        } else {// 用户未登录，只查全局房间
            CursorPageBaseResp<Pair<Long, Double>> roomCursorPage = hotRoomCache.getRoomCursorPage(request);
            List<Long> roomIds = roomCursorPage.getList().stream().map(Pair::getKey).filter(Objects::nonNull).collect(Collectors.toList());
            page = CursorPageBaseResp.init(roomCursorPage, roomIds);
        }
        if (CollectionUtil.isEmpty(page.getList())) {
            return CursorPageBaseResp.empty();
        }
        // 最后组装会话信息（名称，头像，未读数等）
        List<ChatRoomResp> result = buildContactResp(uid, page.getList());
        return CursorPageBaseResp.init(page, result);
    }

    @Override
    public ChatRoomResp getContactDetail(Long uid, Long roomId) {
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(room, "房间号有误");
        List<ChatRoomResp> contacts = buildContactResp(uid, Collections.singletonList(roomId));
        AssertUtil.isNotEmpty(contacts, "房间号有误");
        return contacts.get(0);
    }

    @Override
    public ChatRoomResp getContactDetailByFriend(Long uid, Long friendUid) {
        RoomFriend friendRoom = roomService.getFriendRoom(uid, friendUid);
        // AI助手自动成为好友，无需手动添加
        if (friendRoom == null && (friendUid == 10001 || friendUid == 10002)) {
            friendRoom = roomService.createFriendRoom(Arrays.asList(uid, friendUid));
        }
        AssertUtil.isNotEmpty(friendRoom, "他不是您的好友");
        List<ChatRoomResp> contacts = buildContactResp(uid, Collections.singletonList(friendRoom.getRoomId()));
        AssertUtil.isNotEmpty(contacts, "他不是您的好友");
        return contacts.get(0);
    }

    @Override
    public MemberResp getGroupDetail(Long uid, long roomId) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
        Long onlineNum;
        if (isHotGroup(room)) {// 热点群从redis取人数
            onlineNum = userCache.getOnlineNum();
        } else {
            onlineNum = userDao.getOnlineCount(memberUidList).longValue();
        }
        GroupRoleAPPEnum groupRole = getGroupRole(uid, roomGroup, room);
        GroupMember self = Objects.isNull(uid) ? null : groupMemberDao.getMember(roomGroup.getId(), uid);
        GroupNoticeDTO noticeDTO = roomGroupDao.getNotice(roomGroup);
        long readCount = memberUidList.stream().filter(noticeDTO.getReadUidList()::contains).count();
        return MemberResp.builder()
                .avatar(roomGroup.getAvatar())
                .roomId(roomId)
                .groupName(roomGroup.getName())
                .onlineNum(onlineNum)
                .role(groupRole.getType())
                .notice(noticeDTO.getNotice())
                .noticeUid(noticeDTO.getNoticeUid())
                .noticeTime(noticeDTO.getNoticeTime())
                .noticeReadCount((int) readCount)
                .noticeTotalCount(memberUidList.size())
                .noticeReadByMe(uid != null && noticeDTO.getReadUidList().contains(uid))
                .nickname(self == null ? null : self.getNickname())
                .build();
    }

    @Override
    public void updateNotice(Long uid, Long roomId, String notice) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        boolean canEdit = groupMemberDao.isLord(roomGroup.getId(), uid) || groupMemberDao.isManager(roomGroup.getId(), uid);
        AssertUtil.isTrue(canEdit, GroupErrorEnum.NOT_ALLOWED_OPERATION);
        roomGroupDao.updateNotice(roomGroup.getId(), notice, uid);
        roomGroupCache.delete(roomId);
    }

    @Override
    public void markNoticeRead(Long uid, Long roomId) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, GroupErrorEnum.USER_NOT_IN_GROUP);
        roomGroupDao.markNoticeRead(roomGroup, uid);
        roomGroupCache.delete(roomId);
    }

    @Override
    public void updateNickname(Long uid, Long roomId, String nickname) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, GroupErrorEnum.USER_NOT_IN_GROUP);
        groupMemberDao.updateNickname(roomGroup.getId(), uid, nickname);
    }

    @Override
    public List<ChatRoomResp> getMyGroupList(Long uid) {
        List<GroupMember> myMemberships = groupMemberDao.getMyGroups(uid);
        if (CollectionUtil.isEmpty(myMemberships)) {
            return Collections.emptyList();
        }
        Map<Long, Integer> roleByGroupId = myMemberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, GroupMember::getRole, (a, b) -> a));
        List<RoomGroup> groups = roomGroupDao.listByIds(roleByGroupId.keySet());
        if (CollectionUtil.isEmpty(groups)) {
            return Collections.emptyList();
        }
        List<Long> roomIds = groups.stream().map(RoomGroup::getRoomId).collect(Collectors.toList());
        List<ChatRoomResp> result = buildContactResp(uid, roomIds);
        Map<Long, Long> roomIdToGroupId = groups.stream()
                .collect(Collectors.toMap(RoomGroup::getRoomId, RoomGroup::getId, (a, b) -> a));
        result.forEach(resp -> {
            Long groupId = roomIdToGroupId.get(resp.getRoomId());
            Integer role = groupId == null ? null : roleByGroupId.get(groupId);
            resp.setIsLord(GroupRoleEnum.LEADER.getType().equals(role));
        });
        return result;
    }

    @Override
    public CursorPageBaseResp<ChatMemberResp> getMemberPage(MemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        List<Long> memberUidList;
        if (isHotGroup(room)) {// 全员群展示所有用户
            memberUidList = null;
        } else {// 只展示房间内的群成员
            RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
            if (roomGroup == null) return CursorPageBaseResp.empty();
            memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
        }
        return chatService.getMemberPage(memberUidList, request);
    }

    @Override
    @Cacheable(cacheNames = "member", key = "'memberList.'+#request.roomId+'.'+#uid")
    public List<ChatMemberListResp> getMemberList(Long uid, ChatMessageMemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        if (isHotGroup(room)) {// 全员群展示所有用户100名
            List<User> memberList = userDao.getMemberList();
            return MemberAdapter.buildMemberList(memberList);
        }
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        if (roomGroup != null) {
            List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            Map<Long, User> batch = userInfoCache.getBatch(memberUidList);
            List<ChatMemberListResp> result = new ArrayList<>(MemberAdapter.buildMemberList(batch));
            // AI助手不是真实群成员（不占用group_member表），但支持被@触发，这里补充进候选人列表
            appendAiBots(result);
            return result;
        }
        // 单聊：候选人是对方（好友），另外无论对方是不是AI助手，都支持@Aichat让AI介入对话
        RoomFriend roomFriend = roomFriendCache.get(request.getRoomId());
        if (roomFriend == null) return Collections.emptyList();
        Long otherUid = Objects.equals(roomFriend.getUid1(), uid) ? roomFriend.getUid2() : roomFriend.getUid1();
        User other = userInfoCache.get(otherUid);
        List<ChatMemberListResp> result = new ArrayList<>();
        if (other != null) {
            result.addAll(MemberAdapter.buildMemberList(Collections.singletonList(other)));
        }
        // 对方本身就是AI助手时（单聊即AI机器人房间）不重复追加
        if (other == null || !ChatAIHandlerFactory.getAllAIUserIds().contains(otherUid)) {
            appendAiBots(result);
        }
        return result;
    }

    /** 把已启用的AI助手（如DeepSeek机器人）补充进@候选人列表，它们不是真实的group_member */
    private void appendAiBots(List<ChatMemberListResp> result) {
        List<Long> aiUidList = ChatAIHandlerFactory.getAllAIUserIds();
        if (aiUidList.isEmpty()) {
            return;
        }
        Map<Long, User> aiUserMap = userInfoCache.getBatch(aiUidList);
        result.addAll(MemberAdapter.buildMemberList(aiUserMap));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delMember(Long uid, MemberDelReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, GroupErrorEnum.USER_NOT_IN_GROUP);
        // 1. 判断被移除的人是否是群主或者管理员  （群主不可以被移除，管理员只能被群主移除）
        Long removedUid = request.getUid();
        // 1.1 群主 非法操作
        AssertUtil.isFalse(groupMemberDao.isLord(roomGroup.getId(), removedUid), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        // 1.2 管理员 判断是否是群主操作
        if (groupMemberDao.isManager(roomGroup.getId(), removedUid)) {
            Boolean isLord = groupMemberDao.isLord(roomGroup.getId(), uid);
            AssertUtil.isTrue(isLord, GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        }
        // 1.3 普通成员 判断是否有权限操作
        AssertUtil.isTrue(hasPower(self), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        GroupMember member = groupMemberDao.getMember(roomGroup.getId(), removedUid);
        AssertUtil.isNotEmpty(member, "用户已经移除");
        groupMemberDao.removeById(member.getId());
        // 发送移除事件告知群成员
        List<Long> memberUidList = groupMemberCache.getMemberUidList(roomGroup.getRoomId());
        WSBaseResp<WSMemberChange> ws = MemberAdapter.buildMemberRemoveWS(roomGroup.getRoomId(), member.getUid());
        pushService.sendPushMsg(ws, memberUidList);
        groupMemberCache.evictMemberUidList(room.getId());
    }


    @Override
    @RedissonLock(key = "#request.roomId")
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long uid, MemberAddReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        AssertUtil.isFalse(isHotGroup(room), "全员群无需邀请好友");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, "您不是群成员");
        List<Long> memberBatch = groupMemberDao.getMemberBatch(roomGroup.getId(), request.getUidList());
        Set<Long> existUid = new HashSet<>(memberBatch);
        List<Long> waitAddUidList = request.getUidList().stream().filter(a -> !existUid.contains(a)).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(waitAddUidList)) {
            return;
        }
        List<GroupMember> groupMembers = MemberAdapter.buildMemberAdd(roomGroup.getId(), waitAddUidList);
        groupMemberDao.saveBatch(groupMembers);
        // 同步为新成员建立会话，确保群聊立即出现在其消息列表中
        contactDao.refreshOrCreateActiveTime(roomGroup.getRoomId(), waitAddUidList, null, new java.util.Date());
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, groupMembers, uid));
    }

    @Override
    @Transactional
    public Long addGroup(Long uid, GroupAddReq request) {
        RoomGroup roomGroup = roomService.createGroupRoom(uid, request.getName());
        // 批量保存群成员
        List<GroupMember> groupMembers = RoomAdapter.buildGroupMemberBatch(request.getUidList(), roomGroup.getId());
        groupMemberDao.saveBatch(groupMembers);
        // 同步为所有成员（创建者+被邀请人）建立会话，确保群聊立即出现在消息列表中
        List<Long> allUidList = new java.util.ArrayList<>(request.getUidList());
        allUidList.add(uid);
        contactDao.refreshOrCreateActiveTime(roomGroup.getRoomId(), allUidList, null, new java.util.Date());
        // 发送邀请加群消息==》触发每个人的会话
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, groupMembers, uid));
        return roomGroup.getRoomId();
    }

    private boolean hasPower(GroupMember self) {
        return Objects.equals(self.getRole(), GroupRoleEnum.LEADER.getType())
                || Objects.equals(self.getRole(), GroupRoleEnum.MANAGER.getType())
                || iRoleService.hasPower(self.getUid(), RoleEnum.ADMIN);

    }

    private GroupRoleAPPEnum getGroupRole(Long uid, RoomGroup roomGroup, Room room) {
        GroupMember member = Objects.isNull(uid) ? null : groupMemberDao.getMember(roomGroup.getId(), uid);
        if (Objects.nonNull(member)) {
            return GroupRoleAPPEnum.of(member.getRole());
        } else if (isHotGroup(room)) {
            return GroupRoleAPPEnum.MEMBER;
        } else {
            return GroupRoleAPPEnum.REMOVE;
        }
    }

    private boolean isHotGroup(Room room) {
        return HotFlagEnum.YES.getType().equals(room.getHotFlag());
    }

    private List<Contact> buildContact(List<Pair<Long, Double>> list, Long uid) {
        if (CollectionUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        List<Long> roomIds = list.stream().map(Pair::getKey).collect(Collectors.toList());
        Map<Long, Room> batch = Optional.ofNullable(roomCache.getBatch(roomIds)).orElse(Collections.emptyMap());
        Map<Long, Contact> contactMap = new HashMap<>();
        if (Objects.nonNull(uid)) {
            List<Contact> byRoomIds = contactDao.getByRoomIds(roomIds, uid);
            contactMap = byRoomIds.stream().collect(Collectors.toMap(Contact::getRoomId, Function.identity()));
        }
        Map<Long, Contact> finalContactMap = contactMap;
        return list.stream().map(pair -> {
            Long roomId = pair.getKey();
            Contact contact = finalContactMap.get(roomId);
            if (Objects.isNull(contact)) {
                contact = new Contact();
                contact.setRoomId(pair.getKey());
                Room room = batch.get(roomId);
                if (room != null) {
                    contact.setLastMsgId(room.getLastMsgId());
                }
            }
            contact.setActiveTime(new Date(pair.getValue().longValue()));
            return contact;
        }).filter(contact -> Objects.nonNull(contact.getRoomId())).collect(Collectors.toList());
    }

    private Double getCursorOrNull(String cursor) {
        try {
            return Optional.ofNullable(cursor).map(Double::parseDouble).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseRoomId(String roomId) {
        try {
            return roomId == null ? null : Long.parseLong(roomId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NotNull
    private List<ChatRoomResp> buildContactResp(Long uid, List<Long> roomIds) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return Collections.emptyList();
        }
        // 表情和头像
        Map<Long, RoomBaseInfo> roomBaseInfoMap = getRoomBaseInfoMap(roomIds, uid);
        if (roomBaseInfoMap.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Contact> contactMap = getContactMap(uid, new ArrayList<>(roomBaseInfoMap.keySet()));
        // 最后一条消息
        List<Long> msgIds = roomBaseInfoMap.values().stream()
                .map(RoomBaseInfo::getLastMsgId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Message> messages = CollectionUtil.isEmpty(msgIds) ? new ArrayList<>() : messageDao.listByIds(msgIds);
        messages = Optional.ofNullable(messages).orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, Message> msgMap = messages.stream().collect(Collectors.toMap(Message::getId, Function.identity(), (a, b) -> a));
        List<Long> lastMsgUidList = messages.stream()
                .map(Message::getFromUid)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> lastMsgUidMap = CollectionUtil.isEmpty(lastMsgUidList)
                ? Collections.emptyMap()
                : Optional.ofNullable(userInfoCache.getBatch(lastMsgUidList)).orElse(Collections.emptyMap());
        // 消息未读数
        Map<Long, Integer> unReadCountMap = getUnReadCountMap(uid, new ArrayList<>(roomBaseInfoMap.keySet()));
        return roomBaseInfoMap.values().stream().map(room -> {
                    ChatRoomResp resp = new ChatRoomResp();
                    RoomBaseInfo roomBaseInfo = roomBaseInfoMap.get(room.getRoomId());
                    resp.setAvatar(roomBaseInfo.getAvatar());
                    resp.setRoomId(room.getRoomId());
                    resp.setActiveTime(room.getActiveTime());
                    resp.setHot_Flag(roomBaseInfo.getHotFlag());
                    resp.setType(roomBaseInfo.getType());
                    resp.setName(roomBaseInfo.getName());
                    Contact contact = contactMap.get(room.getRoomId());
                    resp.setPinned(contact == null || contact.getPinned() == null ? 0 : contact.getPinned());
                    resp.setMuted(contact == null || contact.getMuted() == null ? 0 : contact.getMuted());
                    Message message = msgMap.get(room.getLastMsgId());
                    if (Objects.nonNull(message)) {
                        AbstractMsgHandler strategyNoNull = MsgHandlerFactory.getStrategyNoNull(message.getType());
                        User sender = lastMsgUidMap.get(message.getFromUid());
                        String senderName = sender == null ? "用户" : sender.getName();
                        resp.setText(senderName + ":" + strategyNoNull.showContactMsg(message));
                    }
                    resp.setUnreadCount(unReadCountMap.getOrDefault(room.getRoomId(), 0));
                    return resp;
                }).sorted(Comparator.comparing(ChatRoomResp::getActiveTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取未读数
     */
    private Map<Long, Integer> getUnReadCountMap(Long uid, List<Long> roomIds) {
        if (Objects.isNull(uid) || CollectionUtil.isEmpty(roomIds)) {
            return new HashMap<>();
        }
        List<Contact> contacts = contactDao.getByRoomIds(roomIds, uid);
        return Optional.ofNullable(contacts).orElse(Collections.emptyList()).parallelStream()
                .map(contact -> Pair.of(contact.getRoomId(), messageDao.getUnReadCount(contact.getRoomId(), contact.getReadTime())))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<Long, Contact> getContactMap(Long uid, List<Long> roomIds) {
        if (Objects.isNull(uid) || CollectionUtil.isEmpty(roomIds)) {
            return Collections.emptyMap();
        }
        List<Contact> contacts = contactDao.getByRoomIds(roomIds, uid);
        return Optional.ofNullable(contacts).orElse(Collections.emptyList())
                .stream()
                .filter(contact -> Objects.nonNull(contact.getRoomId()))
                .collect(Collectors.toMap(Contact::getRoomId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, User> getFriendRoomMap(List<Long> roomIds, Long uid) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return new HashMap<>();
        }
        Map<Long, RoomFriend> roomFriendMap = Optional.ofNullable(roomFriendCache.getBatch(roomIds)).orElse(Collections.emptyMap());
        List<RoomFriend> roomFriends = roomFriendMap.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
        Set<Long> friendUidSet = Objects.isNull(uid) ? Collections.emptySet() : ChatAdapter.getFriendUidSet(roomFriends, uid);
        Map<Long, User> userBatch = CollectionUtil.isEmpty(friendUidSet)
                ? Collections.emptyMap()
                : Optional.ofNullable(userInfoCache.getBatch(new ArrayList<>(friendUidSet))).orElse(Collections.emptyMap());
        return roomFriends
                .stream()
                .collect(Collectors.toMap(RoomFriend::getRoomId, roomFriend -> {
                    Long friendUid = ChatAdapter.getFriendUid(roomFriend, uid);
                    return userBatch.get(friendUid);
                }));
    }

    private Map<Long, RoomBaseInfo> getRoomBaseInfoMap(List<Long> roomIds, Long uid) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return Collections.emptyMap();
        }
        Map<Long, Room> roomMap = Optional.ofNullable(roomCache.getBatch(roomIds)).orElse(Collections.emptyMap());
        List<Room> rooms = roomMap.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(rooms)) {
            return Collections.emptyMap();
        }
        // 房间根据好友和群组类型分组
        Map<Integer, List<Long>> groupRoomIdMap = rooms.stream()
                .filter(room -> Objects.nonNull(room.getType()))
                .collect(Collectors.groupingBy(Room::getType,
                Collectors.mapping(Room::getId, Collectors.toList())));
        // 获取群组信息
        List<Long> groupRoomId = groupRoomIdMap.getOrDefault(RoomTypeEnum.GROUP.getType(), Collections.emptyList());
        Map<Long, RoomGroup> roomInfoBatch = CollectionUtil.isEmpty(groupRoomId)
                ? Collections.emptyMap()
                : Optional.ofNullable(roomGroupCache.getBatch(groupRoomId)).orElse(Collections.emptyMap());
        // 获取好友信息
        List<Long> friendRoomId = groupRoomIdMap.getOrDefault(RoomTypeEnum.FRIEND.getType(), Collections.emptyList());
        Map<Long, User> friendRoomMap = getFriendRoomMap(friendRoomId, uid);

        return rooms.stream().map(room -> {
            RoomBaseInfo roomBaseInfo = new RoomBaseInfo();
            roomBaseInfo.setRoomId(room.getId());
            roomBaseInfo.setType(room.getType());
            roomBaseInfo.setHotFlag(room.getHotFlag());
            roomBaseInfo.setLastMsgId(room.getLastMsgId());
            roomBaseInfo.setActiveTime(room.getActiveTime());
            if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.GROUP) {
                RoomGroup roomGroup = roomInfoBatch.get(room.getId());
                if (roomGroup != null) {
                    roomBaseInfo.setName(roomGroup.getName());
                    roomBaseInfo.setAvatar(roomGroup.getAvatar());
                }
            } else if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.FRIEND) {
                User user = friendRoomMap.get(room.getId());
                if (user != null) {
                    roomBaseInfo.setName(user.getName());
                    roomBaseInfo.setAvatar(user.getAvatar());
                }
            }
            return roomBaseInfo;
        }).filter(roomBaseInfo -> Objects.nonNull(roomBaseInfo.getRoomId()))
                .collect(Collectors.toMap(RoomBaseInfo::getRoomId, Function.identity(), (a, b) -> a));
    }

    private void fillRoomActive(Long uid, Map<Long, Room> roomMap) {
    }

    @Override
    public void pinContact(Long uid, Long roomId, Boolean pinned) {
        Contact contact = getOrCreateContact(uid, roomId);
        if (contact != null) {
            contact.setPinned(Boolean.TRUE.equals(pinned) ? 1 : 0);
            contactDao.updateById(contact);
        }
    }

    @Override
    public void muteContact(Long uid, Long roomId, Boolean muted) {
        Contact contact = getOrCreateContact(uid, roomId);
        if (contact != null) {
            contact.setMuted(Boolean.TRUE.equals(muted) ? 1 : 0);
            contactDao.updateById(contact);
        }
    }

    @Override
    public void deleteContact(Long uid, Long roomId) {
        contactDao.remove(uid, roomId);
    }

    private Contact getOrCreateContact(Long uid, Long roomId) {
        if (uid == null || roomId == null) {
            return null;
        }
        Contact contact = contactDao.get(uid, roomId);
        if (contact != null) {
            return contact;
        }
        Room room = roomCache.get(roomId);
        if (room == null) {
            return null;
        }
        contact = new Contact();
        contact.setUid(uid);
        contact.setRoomId(roomId);
        contact.setLastMsgId(room.getLastMsgId());
        contact.setActiveTime(room.getActiveTime());
        contact.setPinned(0);
        contact.setMuted(0);
        contactDao.save(contact);
        return contact;
    }
}
