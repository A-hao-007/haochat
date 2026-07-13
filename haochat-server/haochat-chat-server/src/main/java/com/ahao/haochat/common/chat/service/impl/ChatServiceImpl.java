package com.ahao.haochat.common.chat.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Pair;
import com.ahao.haochat.common.chat.dao.*;
import com.ahao.haochat.common.chat.domain.dto.MsgReadInfoDTO;
import com.ahao.haochat.common.chat.domain.entity.*;
import com.ahao.haochat.common.chat.domain.entity.msg.MessageExtra;
import com.ahao.haochat.common.chat.domain.enums.MessageMarkActTypeEnum;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.*;
import com.ahao.haochat.common.chat.domain.vo.request.member.MemberReq;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberListResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMemberStatisticResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageReadResp;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.ContactService;
import com.ahao.haochat.common.chat.service.adapter.MemberAdapter;
import com.ahao.haochat.common.chat.service.adapter.MessageAdapter;
import com.ahao.haochat.common.chat.service.adapter.RoomAdapter;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import com.ahao.haochat.common.chat.service.cache.RoomGroupCache;
import com.ahao.haochat.common.chat.service.helper.ChatMemberHelper;
import com.ahao.haochat.common.chat.service.strategy.mark.AbstractMsgMarkStrategy;
import com.ahao.haochat.common.chat.service.strategy.mark.MsgMarkFactory;
import com.ahao.haochat.common.chat.service.strategy.msg.AbstractMsgHandler;
import com.ahao.haochat.common.chat.service.strategy.msg.MsgHandlerFactory;
import com.ahao.haochat.common.chat.service.strategy.msg.RecallMsgHandler;
import com.ahao.haochat.common.common.annotation.RedissonLock;
import com.ahao.haochat.common.common.algorithm.sensitiveWord.SensitiveWordBs;
import com.ahao.haochat.common.common.domain.enums.NormalOrNoEnum;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.event.MessageEditEvent;
import com.ahao.haochat.common.common.event.MessageSendEvent;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.discover.PrioritizedUrlDiscover;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.ChatActiveStatusEnum;
import com.ahao.haochat.common.user.domain.enums.RoleEnum;
import com.ahao.haochat.common.user.domain.vo.response.ws.ChatMemberResp;
import com.ahao.haochat.common.user.service.IRoleService;
import com.ahao.haochat.common.user.service.cache.UserCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Description: 消息处理类
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-26
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {
    public static final long ROOM_GROUP_ID = 1L;
    private static final PrioritizedUrlDiscover URL_TITLE_DISCOVER = new PrioritizedUrlDiscover();
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private UserCache userCache;
    @Autowired
    private MemberAdapter memberAdapter;
    @Autowired
    private RoomDao roomDao;
    @Autowired
    private MessageMarkDao messageMarkDao;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private RecallMsgHandler recallMsgHandler;
    @Autowired
    private ContactService contactService;
    @Autowired
    private ContactDao contactDao;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private GroupMemberDao groupMemberDao;
    @Autowired
    private RoomGroupCache roomGroupCache;
    @Autowired
    private RoomGroupDao roomGroupDao;
    @Autowired
    private SensitiveWordBs sensitiveWordBs;

    /**
     * 发送消息
     */
    @Override
    @Transactional
    public Long sendMsg(ChatMessageReq request, Long uid) {
        check(request, uid);
        AbstractMsgHandler<?> msgHandler = MsgHandlerFactory.getStrategyNoNull(request.getMsgType());
        Long msgId = msgHandler.checkAndSaveMsg(request, uid);
        //发布消息发送事件
        applicationEventPublisher.publishEvent(new MessageSendEvent(this, msgId));
        return msgId;
    }

    private void check(ChatMessageReq request, Long uid) {
        Room room = roomCache.get(request.getRoomId());
        if (room.isHotRoom()) {//全员群跳过校验
            return;
        }
        if (User.UID_SYSTEM.equals(uid)) {//系统消息（如入群提示）不受成员校验限制
            return;
        }
        if (com.ahao.haochat.common.chatai.handler.ChatAIHandlerFactory.getAllAIUserIds().contains(uid)) {
            //AI助手回复消息：AI助手不是真实的group_member记录（仅在@候选人列表中虚拟展示），不受成员校验限制
            return;
        }
        if (room.isRoomFriend()) {
            RoomFriend roomFriend = roomFriendDao.getByRoomId(request.getRoomId());
            AssertUtil.equal(NormalOrNoEnum.NORMAL.getStatus(), roomFriend.getStatus(), "您已经被对方拉黑");
            AssertUtil.isTrue(uid.equals(roomFriend.getUid1()) || uid.equals(roomFriend.getUid2()), "您已经被对方拉黑");
        }
        if (room.isRoomGroup()) {
            RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
            GroupMember member = groupMemberDao.getMember(roomGroup.getId(), uid);
            AssertUtil.isNotEmpty(member, "您已经被移除该群");
        }

    }

    @Override
    public ChatMessageResp getMsgResp(Message message, Long receiveUid) {
        return CollUtil.getFirst(getMsgRespBatch(Collections.singletonList(message), receiveUid));
    }

    @Override
    public ChatMessageResp getMsgResp(Long msgId, Long receiveUid) {
        Message msg = messageDao.getById(msgId);
        return getMsgResp(msg, receiveUid);
    }

    @Override
    public CursorPageBaseResp<ChatMemberResp> getMemberPage(List<Long> memberUidList, MemberReq request) {
        Pair<ChatActiveStatusEnum, String> pair = ChatMemberHelper.getCursorPair(request.getCursor());
        ChatActiveStatusEnum activeStatusEnum = pair.getKey();
        String timeCursor = pair.getValue();
        List<ChatMemberResp> resultList = new ArrayList<>();//最终列表
        Boolean isLast = Boolean.FALSE;
        if (activeStatusEnum == ChatActiveStatusEnum.ONLINE) {//在线列表
            CursorPageBaseResp<User> cursorPage = userDao.getCursorPage(memberUidList, new CursorPageBaseReq(request.getPageSize(), timeCursor), ChatActiveStatusEnum.ONLINE);
            resultList.addAll(MemberAdapter.buildMember(cursorPage.getList()));//添加在线列表
            if (cursorPage.getIsLast()) {//如果是最后一页,从离线列表再补点数据
                activeStatusEnum = ChatActiveStatusEnum.OFFLINE;
                Integer leftSize = request.getPageSize() - cursorPage.getList().size();
                cursorPage = userDao.getCursorPage(memberUidList, new CursorPageBaseReq(leftSize, null), ChatActiveStatusEnum.OFFLINE);
                resultList.addAll(MemberAdapter.buildMember(cursorPage.getList()));//添加离线线列表
            }
            timeCursor = cursorPage.getCursor();
            isLast = cursorPage.getIsLast();
        } else if (activeStatusEnum == ChatActiveStatusEnum.OFFLINE) {//离线列表
            CursorPageBaseResp<User> cursorPage = userDao.getCursorPage(memberUidList, new CursorPageBaseReq(request.getPageSize(), timeCursor), ChatActiveStatusEnum.OFFLINE);
            resultList.addAll(MemberAdapter.buildMember(cursorPage.getList()));//添加离线线列表
            timeCursor = cursorPage.getCursor();
            isLast = cursorPage.getIsLast();
        }
        // 获取群成员角色ID
        List<Long> uidList = resultList.stream().map(ChatMemberResp::getUid).collect(Collectors.toList());
        RoomGroup roomGroup = roomGroupDao.getByRoomId(request.getRoomId());
        Map<Long, Integer> uidMapRole = groupMemberDao.getMemberMapRole(roomGroup.getId(), uidList);
        Map<Long, String> uidMapNickname = groupMemberDao.getMemberMapNickname(roomGroup.getId(), uidList);
        resultList.forEach(member -> {
            member.setRoleId(uidMapRole.get(member.getUid()));
            member.setNickname(uidMapNickname.get(member.getUid()));
        });
        //组装结果
        return new CursorPageBaseResp<>(ChatMemberHelper.generateCursor(activeStatusEnum, timeCursor), isLast, resultList);
    }

    @Override
    public CursorPageBaseResp<ChatMessageResp> getMsgPage(ChatMessagePageReq request, Long receiveUid) {
        //越权校验：非热门房间必须是该房间成员才能读消息，否则构造任意 roomId 即可窥探他人私聊/群聊
        checkReadPermission(request.getRoomId(), receiveUid);
        //用最后一条消息id，来限制被踢出的人能看见的最大一条消息
        Long lastMsgId = getLastMsgId(request.getRoomId(), receiveUid);
        CursorPageBaseResp<Message> cursorPage = messageDao.getCursorPage(request.getRoomId(), request, lastMsgId);
        if (cursorPage.isEmpty()) {
            return CursorPageBaseResp.empty();
        }
        return CursorPageBaseResp.init(cursorPage, getMsgRespBatch(cursorPage.getList(), receiveUid));
    }

    /**
     * 读消息权限校验：热门房间对所有人开放；单聊仅双方可读；群聊仅群成员可读。
     * AI 单聊本质是真实好友房间（用户是 room_friend 的 uid1/uid2 之一），故走单聊分支即可通过。
     */
    private void checkReadPermission(Long roomId, Long receiveUid) {
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(room, "房间号有误");
        if (room.isHotRoom()) {
            return;
        }
        AssertUtil.isNotEmpty(receiveUid, "请先登录");
        if (room.isRoomFriend()) {
            RoomFriend roomFriend = roomFriendDao.getByRoomId(roomId);
            AssertUtil.isNotEmpty(roomFriend, "房间号有误");
            AssertUtil.isTrue(receiveUid.equals(roomFriend.getUid1()) || receiveUid.equals(roomFriend.getUid2()),
                    "你没有权限查看该会话");
        } else if (room.isRoomGroup()) {
            RoomGroup roomGroup = roomGroupCache.get(roomId);
            AssertUtil.isNotEmpty(roomGroup, "房间号有误");
            GroupMember member = groupMemberDao.getMember(roomGroup.getId(), receiveUid);
            AssertUtil.isNotEmpty(member, "你不在该群聊中");
        }
    }

    private Long getLastMsgId(Long roomId, Long receiveUid) {
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(room, "房间号有误");
        if (room.isHotRoom()) {
            return null;
        }
        AssertUtil.isNotEmpty(receiveUid, "请先登录");
        Contact contact = contactDao.get(receiveUid, roomId);
        // 无会话记录（如首次打开 AI 会话）时返回 null，避免空指针，从最新消息开始展示
        return contact == null ? null : contact.getLastMsgId();
    }

    @Override
    public ChatMemberStatisticResp getMemberStatistic() {
        System.out.println(Thread.currentThread().getName());
        Long onlineNum = userCache.getOnlineNum();
//        Long offlineNum = userCache.getOfflineNum();不展示总人数
        ChatMemberStatisticResp resp = new ChatMemberStatisticResp();
        resp.setOnlineNum(onlineNum);
//        resp.setTotalNum(onlineNum + offlineNum);
        return resp;
    }

    @Override
    @RedissonLock(key = "#uid")
    public void setMsgMark(Long uid, ChatMessageMarkReq request) {
        AbstractMsgMarkStrategy strategy = MsgMarkFactory.getStrategyNoNull(request.getMarkType());
        switch (MessageMarkActTypeEnum.of(request.getActType())) {
            case MARK:
                strategy.mark(uid, request.getMsgId());
                break;
            case UN_MARK:
                strategy.unMark(uid, request.getMsgId());
                break;
        }
    }

    @Override
    public void recallMsg(Long uid, ChatMessageBaseReq request) {
        Message message = messageDao.getById(request.getMsgId());
        //校验能不能执行撤回
        checkRecall(uid, message);
        //执行消息撤回
        recallMsgHandler.recall(uid, message);
    }

    // [AUDIT-ADD] B-消息编辑：仅发送者可编辑自己的文本消息，并更新 updateTime
    @Override
    @Transactional
    public void editMsg(Long uid, ChatMessageEditReq request) {
        Message message = messageDao.getById(request.getMsgId());
        AssertUtil.isNotEmpty(message, "消息有误");
        AssertUtil.equal(request.getRoomId(), message.getRoomId(), "房间号有误");
        checkReadPermission(message.getRoomId(), uid);
        AssertUtil.equal(message.getType(), MessageTypeEnum.TEXT.getType(), "仅文本消息支持编辑");
        AssertUtil.isTrue(Objects.equals(uid, message.getFromUid()), "抱歉,您没有权限");
        AssertUtil.isFalse(request.getContent() == null || request.getContent().trim().isEmpty(), "编辑内容不能为空");
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        extra.setUrlContentMap(URL_TITLE_DISCOVER.getUrlContentMap(request.getContent()));
        Message update = new Message();
        update.setId(message.getId());
        update.setContent(sensitiveWordBs.filter(request.getContent()));
        update.setExtra(extra);
        update.setUpdateTime(new Date());
        messageDao.updateById(update);
        applicationEventPublisher.publishEvent(new MessageEditEvent(this, message.getId()));
    }

    @Override
    @Cacheable(cacheNames = "member", key = "'memberList.'+#req.roomId")
    public List<ChatMemberListResp> getMemberList(ChatMessageMemberReq req) {
        if (Objects.equals(1L, req.getRoomId())) {//大群聊可看见所有人
            return userDao.getMemberList()
                    .stream()
                    .map(a -> {
                        ChatMemberListResp resp = new ChatMemberListResp();
                        BeanUtils.copyProperties(a, resp);
                        resp.setUid(a.getId());
                        return resp;
                    }).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public Collection<MsgReadInfoDTO> getMsgReadInfo(Long uid, ChatMessageReadInfoReq request) {
        List<Message> messages = messageDao.listByIds(request.getMsgIds());
        messages.forEach(message -> {
            AssertUtil.equal(uid, message.getFromUid(), "只能查询自己发送的消息");
        });
        return contactService.getMsgReadInfo(messages).values();
    }

    @Override
    public CursorPageBaseResp<ChatMessageReadResp> getReadPage(@Nullable Long uid, ChatMessageReadReq request) {
        Message message = messageDao.getById(request.getMsgId());
        AssertUtil.isNotEmpty(message, "消息id有误");
        AssertUtil.equal(uid, message.getFromUid(), "只能查看自己的消息");
        CursorPageBaseResp<Contact> page;
        if (request.getSearchType() == 1) {//已读
            page = contactDao.getReadPage(message, request);
        } else {
            page = contactDao.getUnReadPage(message, request);
        }
        if (CollectionUtil.isEmpty(page.getList())) {
            return CursorPageBaseResp.empty();
        }
        return CursorPageBaseResp.init(page, RoomAdapter.buildReadResp(page.getList()));
    }

    @Override
    @RedissonLock(key = "#uid")
    public void msgRead(Long uid, ChatMessageMemberReq request) {
        Contact contact = contactDao.get(uid, request.getRoomId());
        if (Objects.nonNull(contact)) {
            Contact update = new Contact();
            update.setId(contact.getId());
            update.setReadTime(new Date());
            contactDao.updateById(update);
        } else {
            Contact insert = new Contact();
            insert.setUid(uid);
            insert.setRoomId(request.getRoomId());
            insert.setReadTime(new Date());
            contactDao.save(insert);
        }
    }

    private void checkRecall(Long uid, Message message) {
        AssertUtil.isNotEmpty(message, "消息有误");
        AssertUtil.notEqual(message.getType(), MessageTypeEnum.RECALL.getType(), "消息无法撤回");
        boolean hasPower = iRoleService.hasPower(uid, RoleEnum.CHAT_MANAGER);
        if (hasPower) {
            return;
        }
        boolean self = Objects.equals(uid, message.getFromUid());
        AssertUtil.isTrue(self, "抱歉,您没有权限");
        long between = DateUtil.between(message.getCreateTime(), new Date(), DateUnit.MINUTE);
        AssertUtil.isTrue(between < 2, "覆水难收，超过2分钟的消息不能撤回哦~~");
    }

    public List<ChatMessageResp> getMsgRespBatch(List<Message> messages, Long receiveUid) {
        if (CollectionUtil.isEmpty(messages)) {
            return new ArrayList<>();
        }
        //查询消息标志
        List<MessageMark> msgMark = messageMarkDao.getValidMarkByMsgIdBatch(messages.stream().map(Message::getId).collect(Collectors.toList()));
        return MessageAdapter.buildMsgResp(messages, msgMark, receiveUid);
    }

}
