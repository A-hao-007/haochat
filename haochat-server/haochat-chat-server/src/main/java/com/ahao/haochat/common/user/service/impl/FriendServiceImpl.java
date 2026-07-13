package com.ahao.haochat.common.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.ContactService;
import com.ahao.haochat.common.chat.service.RoomService;
import com.ahao.haochat.common.chat.service.adapter.MessageAdapter;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.request.PageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.domain.vo.response.PageBaseResp;
import com.ahao.haochat.common.common.event.UserApplyEvent;
import com.ahao.haochat.common.common.event.FriendApplyAcceptedEvent;
import com.ahao.haochat.common.common.service.LockService;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.user.dao.UserApplyDao;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.dao.UserFriendDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.entity.UserApply;
import com.ahao.haochat.common.user.domain.entity.UserFriend;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApplyReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendApproveReq;
import com.ahao.haochat.common.user.domain.vo.request.friend.FriendCheckReq;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendApplyResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendCheckResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendResp;
import com.ahao.haochat.common.user.domain.vo.response.friend.FriendUnreadResp;
import com.ahao.haochat.common.user.service.FriendService;
import com.ahao.haochat.common.user.service.adapter.FriendAdapter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ahao.haochat.common.user.domain.enums.ApplyStatusEnum.AGREE;
import static com.ahao.haochat.common.user.domain.enums.ApplyStatusEnum.REJECT;
import static com.ahao.haochat.common.user.domain.enums.ApplyStatusEnum.WAIT_APPROVAL;

/**
 * @author : limeng
 * @description : 好友
 * @date : 2023/07/19
 */
@Slf4j
@Service
public class FriendServiceImpl implements FriendService {

    @Autowired
    private UserFriendDao userFriendDao;
    @Autowired
    private UserApplyDao userApplyDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private RoomService roomService;
    @Autowired
    private ContactService contactService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private LockService lockService;

    /**
     * 检查
     * 检查是否是自己好友
     *
     * @param uid     uid
     * @param request 请求
     * @return {@link FriendCheckResp}
     */
    @Override
    public FriendCheckResp check(Long uid, FriendCheckReq request) {
        List<UserFriend> friendList = userFriendDao.getByFriends(uid, request.getUidList());

        Set<Long> friendUidSet = friendList.stream().map(UserFriend::getFriendUid).collect(Collectors.toSet());
        List<FriendCheckResp.FriendCheck> friendCheckList = request.getUidList().stream().map(friendUid -> {
            FriendCheckResp.FriendCheck friendCheck = new FriendCheckResp.FriendCheck();
            friendCheck.setUid(friendUid);
            friendCheck.setIsFriend(friendUidSet.contains(friendUid));
            return friendCheck;
        }).collect(Collectors.toList());
        return new FriendCheckResp(friendCheckList);
    }

    /**
     * 申请好友
     *
     * @param request 请求
     */
    @Override
    public void apply(Long uid, FriendApplyReq request) {
        AssertUtil.isFalse(Objects.equals(uid, request.getTargetUid()), "不能添加自己为好友");
        lockService.executeWithLock(friendPairKey(uid, request.getTargetUid()), () -> {
            //是否有好友关系
            UserFriend friend = userFriendDao.getByFriend(uid, request.getTargetUid());
            AssertUtil.isEmpty(friend, "你们已经是好友了");
            //是否有待审批的申请记录(自己的)
            UserApply selfApproving = userApplyDao.getFriendApproving(uid, request.getTargetUid());
            if (Objects.nonNull(selfApproving)) {
                log.info("已有好友申请记录,uid:{}, targetId:{}", uid, request.getTargetUid());
                return null;
            }
            //是否有待审批的申请记录(别人请求自己的)
            UserApply friendApproving = userApplyDao.getFriendApproving(request.getTargetUid(), uid);
            if (Objects.nonNull(friendApproving)) {
                ((FriendService) AopContext.currentProxy()).applyApprove(uid, new FriendApproveReq(friendApproving.getId()));
                return null;
            }
            //申请入库
            UserApply insert = FriendAdapter.buildFriendApply(uid, request);
            userApplyDao.save(insert);
            //申请事件
            applicationEventPublisher.publishEvent(new UserApplyEvent(this, insert));
            return null;
        });
    }

    /**
     * 分页查询好友申请
     *
     * @param request 请求
     * @return {@link PageBaseResp}<{@link FriendApplyResp}>
     */
    @Override
    public PageBaseResp<FriendApplyResp> pageApplyFriend(Long uid, PageBaseReq request) {
        IPage<UserApply> userApplyIPage = userApplyDao.friendApplyPage(uid, request.plusPage());
        if (CollectionUtil.isEmpty(userApplyIPage.getRecords())) {
            return PageBaseResp.empty();
        }
        //将这些申请列表设为已读
        readApples(uid, userApplyIPage);
        //返回消息
        return PageBaseResp.init(userApplyIPage, FriendAdapter.buildFriendApplyList(userApplyIPage.getRecords()));
    }

    @Override
    public PageBaseResp<FriendApplyResp> pageSentApplyFriend(Long uid, PageBaseReq request) {
        IPage<UserApply> userApplyIPage = userApplyDao.sentFriendApplyPage(uid, request.plusPage());
        if (CollectionUtil.isEmpty(userApplyIPage.getRecords())) {
            return PageBaseResp.empty();
        }
        return PageBaseResp.init(userApplyIPage, FriendAdapter.buildSentFriendApplyList(userApplyIPage.getRecords()));
    }

    private void readApples(Long uid, IPage<UserApply> userApplyIPage) {
        List<Long> applyIds = userApplyIPage.getRecords()
                .stream().map(UserApply::getId)
                .collect(Collectors.toList());
        userApplyDao.readApples(uid, applyIds);
    }

    /**
     * 申请未读数
     *
     * @return {@link FriendUnreadResp}
     */
    @Override
    public FriendUnreadResp unread(Long uid) {
        Integer unReadCount = userApplyDao.getUnReadCount(uid);
        return new FriendUnreadResp(unReadCount);
    }

    @Override
    public void applyApprove(Long uid, FriendApproveReq request) {
        // 先取申请记录，仅为解析"好友对"用于加锁；真正的业务校验在事务主体里重新做一遍。
        UserApply userApply = userApplyDao.getById(request.getApplyId());
        AssertUtil.isNotEmpty(userApply, "不存在申请记录");
        AssertUtil.equal(userApply.getTargetId(), uid, "不存在申请记录");
        // 锁"好友对"而不是单个 uid：A同意B、B同意A是两条不同的申请，若锁各自的 uid 则不互斥，
        // 会并发建立重复好友关系。按有序 uid 对加锁，让同一对好友的审批串行化。
        // 锁必须包在事务外（LockService 编程式锁 + 经代理调用事务主体），否则锁在事务提交前就释放了。
        lockService.executeWithLock(friendPairKey(uid, userApply.getUid()), () -> {
            ((FriendService) AopContext.currentProxy()).doApplyApprove(uid, request);
            return null;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doApplyApprove(Long uid, FriendApproveReq request) {
        UserApply userApply = userApplyDao.getById(request.getApplyId());
        AssertUtil.isNotEmpty(userApply, "不存在申请记录");
        AssertUtil.equal(userApply.getTargetId(), uid, "不存在申请记录");
        if (Objects.equals(userApply.getStatus(), AGREE.getCode())) {
            return;
        }
        AssertUtil.isFalse(Objects.equals(userApply.getStatus(), REJECT.getCode()), "好友申请已拒绝");
        AssertUtil.equal(userApply.getStatus(), WAIT_APPROVAL.getCode(), "申请状态异常");
        //同意申请
        boolean updated = userApplyDao.agreeWaiting(request.getApplyId(), uid);
        if (!updated) {
            return;
        }
        //创建双方好友关系
        createFriend(uid, userApply.getUid());
        //创建一个聊天房间
        RoomFriend roomFriend = roomService.createFriendRoom(Arrays.asList(uid, userApply.getUid()));
        applicationEventPublisher.publishEvent(new FriendApplyAcceptedEvent(this, request.getApplyId(), uid,
                userApply.getUid(), roomFriend.getRoomId()));
        //发送一条同意消息。。我们已经是好友了，开始聊天吧
        chatService.sendMsg(MessageAdapter.buildAgreeMsg(roomFriend.getRoomId()), uid);
    }

    @Override
    public void applyReject(Long uid, FriendApproveReq request) {
        UserApply userApply = userApplyDao.getById(request.getApplyId());
        AssertUtil.isNotEmpty(userApply, "不存在申请记录");
        AssertUtil.equal(userApply.getTargetId(), uid, "不存在申请记录");
        lockService.executeWithLock(friendPairKey(uid, userApply.getUid()), () -> {
            UserApply latest = userApplyDao.getById(request.getApplyId());
            AssertUtil.isNotEmpty(latest, "不存在申请记录");
            AssertUtil.equal(latest.getTargetId(), uid, "不存在申请记录");
            if (Objects.equals(latest.getStatus(), REJECT.getCode())) {
                return null;
            }
            AssertUtil.isFalse(Objects.equals(latest.getStatus(), AGREE.getCode()), "好友申请已同意");
            userApplyDao.rejectWaiting(request.getApplyId(), uid);
            return null;
        });
    }

    /** 好友对锁 key：两 uid 排序后拼接，保证 (A,B) 与 (B,A) 得到同一把锁 */
    private String friendPairKey(Long uid1, Long uid2) {
        long min = Math.min(uid1, uid2);
        long max = Math.max(uid1, uid2);
        return "friend_apply_pair_" + min + "_" + max;
    }

    /**
     * 删除好友
     *
     * @param uid       uid
     * @param friendUid 朋友uid
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long uid, Long friendUid) {
        List<UserFriend> userFriends = userFriendDao.getUserFriend(uid, friendUid);
        if (CollectionUtil.isEmpty(userFriends)) {
            log.info("没有好友关系：{},{}", uid, friendUid);
            return;
        }
        List<Long> friendRecordIds = userFriends.stream().map(UserFriend::getId).collect(Collectors.toList());
        userFriendDao.removeByIds(friendRecordIds);
        //禁用房间
        roomService.disableFriendRoom(Arrays.asList(uid, friendUid));
    }

    @Override
    public CursorPageBaseResp<FriendResp> friendList(Long uid, CursorPageBaseReq request) {
        CursorPageBaseResp<UserFriend> friendPage = userFriendDao.getFriendPage(uid, request);
        if (CollectionUtils.isEmpty(friendPage.getList())) {
            return CursorPageBaseResp.empty();
        }
        List<Long> friendUids = friendPage.getList()
                .stream().map(UserFriend::getFriendUid)
                .collect(Collectors.toList());
        List<User> userList = userDao.getFriendList(friendUids);
        return CursorPageBaseResp.init(friendPage, FriendAdapter.buildFriend(friendPage.getList(), userList));
    }

    private void createFriend(Long uid, Long targetUid) {
        UserFriend existForward = userFriendDao.getByFriend(uid, targetUid);
        UserFriend existReverse = userFriendDao.getByFriend(targetUid, uid);
        if (existForward != null && existReverse != null) {
            return;
        }
        List<UserFriend> inserts = Lists.newArrayList();
        if (existForward == null) {
            UserFriend userFriend1 = new UserFriend();
            userFriend1.setUid(uid);
            userFriend1.setFriendUid(targetUid);
            inserts.add(userFriend1);
        }
        if (existReverse == null) {
            UserFriend userFriend2 = new UserFriend();
            userFriend2.setUid(targetUid);
            userFriend2.setFriendUid(uid);
            inserts.add(userFriend2);
        }
        userFriendDao.saveBatch(inserts);
    }

    @Override
    public void setRemark(Long uid, Long friendUid, String remark) {
        UserFriend uf = userFriendDao.getByFriend(uid, friendUid);
        if (uf != null) {
            uf.setRemark(remark);
            userFriendDao.updateById(uf);
        }
    }
}
