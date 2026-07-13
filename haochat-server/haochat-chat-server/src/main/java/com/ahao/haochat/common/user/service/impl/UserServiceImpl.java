package com.ahao.haochat.common.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahao.haochat.common.common.event.UserBlackEvent;
import com.ahao.haochat.common.common.event.UserRegisterEvent;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.algorithm.sensitiveWord.SensitiveWordBs;
import com.ahao.haochat.common.user.dao.BlackDao;
import com.ahao.haochat.common.user.dao.ItemConfigDao;
import com.ahao.haochat.common.user.dao.UserBackpackDao;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.dao.UserSessionDao;
import com.ahao.haochat.common.user.domain.dto.ItemInfoDTO;
import com.ahao.haochat.common.user.domain.dto.SummeryInfoDTO;
import com.ahao.haochat.common.user.domain.entity.Black;
import com.ahao.haochat.common.user.domain.entity.ItemConfig;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.entity.UserBackpack;
import com.ahao.haochat.common.user.domain.enums.BlackTypeEnum;
import com.ahao.haochat.common.user.domain.enums.ItemEnum;
import com.ahao.haochat.common.user.domain.enums.ItemTypeEnum;
import com.ahao.haochat.common.user.domain.vo.request.user.*;
import com.ahao.haochat.common.user.domain.vo.response.user.BadgeResp;
import com.ahao.haochat.common.user.domain.vo.response.user.UserInfoResp;
import com.ahao.haochat.common.user.service.UserService;
import com.ahao.haochat.common.user.service.adapter.UserAdapter;
import com.ahao.haochat.common.user.service.cache.ItemCache;
import com.ahao.haochat.common.user.service.cache.UserCache;
import com.ahao.haochat.common.user.service.cache.UserSummaryCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Description: 用户基础操作类
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-19
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserCache userCache;
    @Autowired
    private UserBackpackDao userBackpackDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ItemConfigDao itemConfigDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private ItemCache itemCache;
    @Autowired
    private BlackDao blackDao;
    @Autowired
    private UserSummaryCache userSummaryCache;
    @Autowired
    private SensitiveWordBs sensitiveWordBs;
    @Autowired
    private UserSessionDao userSessionDao;

    @Override
    public UserInfoResp getUserInfo(Long uid) {
        User userInfo = userCache.getUserInfo(uid);
        Integer countByValidItemId = userBackpackDao.getCountByValidItemId(uid, ItemEnum.MODIFY_NAME_CARD.getId());
        return UserAdapter.buildUserInfoResp(userInfo, countByValidItemId);
    }

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Override
    public void modifyPassword(Long uid, ModifyPasswordReq req) {
        User user = userDao.getById(uid);
        AssertUtil.isNotEmpty(user, "用户不存在");
        AssertUtil.isTrue(PASSWORD_ENCODER.matches(req.getOldPassword(), user.getPassword()), "原密码错误");
        AssertUtil.isFalse(PASSWORD_ENCODER.matches(req.getNewPassword(), user.getPassword()), "新密码不能与原密码相同");
        userDao.modifyPassword(uid, PASSWORD_ENCODER.encode(req.getNewPassword()));
        userSessionDao.revokeByUid(uid);
        log.info("用户修改密码成功: uid={}", uid);
    }

    @Override
    @Transactional
    public void modifyName(Long uid, ModifyNameReq req) {
        //判断名字是不是重复
        String newName = req.getName();
        AssertUtil.isFalse(sensitiveWordBs.hasSensitiveWord(newName), "名字中包含敏感词，请重新输入"); // 判断名字中有没有敏感词
        User oldUser = userDao.getByName(newName);
        AssertUtil.isEmpty(oldUser, "名字已经被抢占了，请换一个哦~~");
        //判断改名卡够不够
        UserBackpack firstValidItem = userBackpackDao.getFirstValidItem(uid, ItemEnum.MODIFY_NAME_CARD.getId());
        AssertUtil.isNotEmpty(firstValidItem, "改名次数不够了，每月1日会自动补发一张改名卡，请耐心等待~");
        //使用改名卡
        boolean useSuccess = userBackpackDao.invalidItem(firstValidItem.getId());
        if (useSuccess) {//用乐观锁，就不用分布式锁了
            //改名
            userDao.modifyName(uid, req.getName());
            //删除缓存
            userCache.userInfoChange(uid);
        }
    }

    @Override
    public List<BadgeResp> badges(Long uid) {
        //查询所有徽章
        List<ItemConfig> itemConfigs = itemCache.getByType(ItemTypeEnum.BADGE.getType());
        //查询用户拥有的徽章
        List<UserBackpack> backpacks = userBackpackDao.getByItemIds(uid, itemConfigs.stream().map(ItemConfig::getId).collect(Collectors.toList()));
        //查询用户当前佩戴的标签
        User user = userDao.getById(uid);
        return UserAdapter.buildBadgeResp(itemConfigs, backpacks, user);
    }

    @Override
    public void wearingBadge(Long uid, WearingBadgeReq req) {
        // badgeId=0 表示卸下徽章
        if (req.getBadgeId() != null && req.getBadgeId() == 0) {
            userDao.wearingBadge(uid, 0L);
            userCache.userInfoChange(uid);
            return;
        }
        //确保有这个徽章
        UserBackpack firstValidItem = userBackpackDao.getFirstValidItem(uid, req.getBadgeId());
        AssertUtil.isNotEmpty(firstValidItem, "您没有这个徽章哦，快去达成条件获取吧");
        //确保物品类型是徽章
        ItemConfig itemConfig = itemConfigDao.getById(firstValidItem.getItemId());
        AssertUtil.equal(itemConfig.getType(), ItemTypeEnum.BADGE.getType(), "该徽章不可佩戴");
        //佩戴徽章
        userDao.wearingBadge(uid, req.getBadgeId());
        //删除用户缓存
        userCache.userInfoChange(uid);
    }

    @Override
    public void register(User user) {
        userDao.save(user);
        applicationEventPublisher.publishEvent(new UserRegisterEvent(this, user));
    }

    @Override
    public void black(BlackReq req) {
        Long uid = req.getUid();
        Black user = new Black();
        user.setTarget(uid.toString());
        user.setType(BlackTypeEnum.UID.getType());
        blackDao.save(user);
        User byId = userDao.getById(uid);
        blackIp(byId.getIpInfo().getCreateIp());
        blackIp(byId.getIpInfo().getUpdateIp());
        applicationEventPublisher.publishEvent(new UserBlackEvent(this, byId));
    }

    @Override
    public List<SummeryInfoDTO> getSummeryUserInfo(SummeryInfoReq req) {
        //需要前端同步的uid
        List<Long> uidList = getNeedSyncUidList(req.getReqList());
        //加载用户信息
        Map<Long, SummeryInfoDTO> batch = userSummaryCache.getBatch(uidList);
        return req.getReqList()
                .stream()
                .map(a -> batch.containsKey(a.getUid()) ? batch.get(a.getUid()) : SummeryInfoDTO.skip(a.getUid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemInfoDTO> getItemInfo(ItemInfoReq req) {//简单做，更新时间可判断被修改
        return req.getReqList().stream().map(a -> {
            ItemConfig itemConfig = itemCache.getById(a.getItemId());
            if (Objects.nonNull(a.getLastModifyTime()) && a.getLastModifyTime() >= itemConfig.getUpdateTime().getTime()) {
                return ItemInfoDTO.skip(a.getItemId());
            }
            ItemInfoDTO dto = new ItemInfoDTO();
            dto.setItemId(itemConfig.getId());
            dto.setImg(itemConfig.getImg());
            dto.setDescribe(itemConfig.getDescribe());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<Long> getNeedSyncUidList(List<SummeryInfoReq.infoReq> reqList) {
        List<Long> needSyncUidList = new ArrayList<>();
        List<Long> userModifyTime = userCache.getUserModifyTime(reqList.stream().map(SummeryInfoReq.infoReq::getUid).collect(Collectors.toList()));
        for (int i = 0; i < reqList.size(); i++) {
            SummeryInfoReq.infoReq infoReq = reqList.get(i);
            Long modifyTime = userModifyTime.get(i);
            if (Objects.isNull(infoReq.getLastModifyTime()) || (Objects.nonNull(modifyTime) && modifyTime > infoReq.getLastModifyTime())) {
                needSyncUidList.add(infoReq.getUid());
            }
        }
        return needSyncUidList;
    }

    public void blackIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return;
        }
        try {
            Black user = new Black();
            user.setTarget(ip);
            user.setType(BlackTypeEnum.IP.getType());
            blackDao.save(user);
        } catch (Exception e) {
            log.error("duplicate black ip:{}", ip);
        }
    }

    @Override
    public List<SummeryInfoDTO> searchUser(String keyword, Long excludeUid) {
        List<User> users = userDao.searchByKeyword(keyword, excludeUid);
        return users.stream().map(user -> SummeryInfoDTO.builder()
                .uid(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .needRefresh(Boolean.FALSE)
                .build()).collect(Collectors.toList());
    }

    @Override
    public void updateAvatar(Long uid, String avatar) {
        User update = new User();
        update.setId(uid);
        update.setAvatar(avatar);
        userDao.updateById(update);
    }
}
