package com.ahao.haochat.common.user.service;

import com.ahao.haochat.common.user.domain.dto.ItemInfoDTO;
import com.ahao.haochat.common.user.domain.dto.SummeryInfoDTO;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.vo.request.user.*;
import com.ahao.haochat.common.user.domain.vo.response.user.BadgeResp;
import com.ahao.haochat.common.user.domain.vo.response.user.UserInfoResp;

import java.util.List;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
public interface UserService {

    /**
     * 获取前端展示信息
     *
     * @param uid
     * @return
     */
    UserInfoResp getUserInfo(Long uid);

    /**
     * 修改用户名
     *
     * @param uid
     * @param req
     */
    void modifyName(Long uid, ModifyNameReq req);

    /**
     * 用户徽章列表
     *
     * @param uid
     */
    List<BadgeResp> badges(Long uid);

    /**
     * 佩戴徽章
     *
     * @param uid
     * @param req
     */
    void wearingBadge(Long uid, WearingBadgeReq req);

    /**
     * 用户注册，需要获得id
     *
     * @param user
     */
    void register(User user);

    void black(BlackReq req);

    /**
     * 获取用户汇总信息
     *
     * @param req
     * @return
     */
    List<SummeryInfoDTO> getSummeryUserInfo(SummeryInfoReq req);

    List<ItemInfoDTO> getItemInfo(ItemInfoReq req);

    /**
     * 搜索用户
     */
    List<SummeryInfoDTO> searchUser(String keyword, Long excludeUid);

    /**
     * 更新头像
     */
    void updateAvatar(Long uid, String avatar);
}
