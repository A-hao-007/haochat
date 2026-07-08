package com.ahao.haochat.common.user.scheduler;

import com.ahao.haochat.common.common.domain.enums.IdempotentEnum;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.enums.ItemEnum;
import com.ahao.haochat.common.user.service.IUserBackpackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * 每月给正常状态用户发一张改名卡（注册时只送一张，用掉之后一直没有补发机制）。
 * 用"年月"作为幂等号，重复执行/重启不会重复发放，也不会覆盖用户通过其他渠道获得的改名卡。
 */
@Slf4j
@Component
public class RenameCardScheduler {

    @Autowired
    private UserDao userDao;
    @Autowired
    private IUserBackpackService userBackpackService;

    @Scheduled(cron = "0 0 3 1 * ?")
    public void grantMonthlyRenameCard() {
        String yearMonth = YearMonth.now().toString();
        List<User> users = userDao.lambdaQuery().eq(User::getStatus, 0).list();
        log.info("开始发放每月改名卡: yearMonth={}, userCount={}", yearMonth, users.size());
        for (User user : users) {
            String businessId = user.getId() + "_" + yearMonth;
            userBackpackService.acquireItem(user.getId(), ItemEnum.MODIFY_NAME_CARD.getId(),
                    IdempotentEnum.YEAR_MONTH, businessId);
        }
        log.info("每月改名卡发放完成: yearMonth={}", yearMonth);
    }
}
