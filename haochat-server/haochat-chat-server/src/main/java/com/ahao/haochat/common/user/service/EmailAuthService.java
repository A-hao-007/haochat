package com.ahao.haochat.common.user.service;

import cn.hutool.core.util.RandomUtil;
import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 邮箱相关：绑定邮箱、邮箱登录、邮箱找回密码。验证码存 Redis（10 分钟）。
 */
@Slf4j
@Service
public class EmailAuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern STRONG_PWD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final int CODE_TTL_MINUTES = 10;

    @Autowired
    private UserDao userDao;
    @Autowired
    private MailService mailService;

    /** 注册：发送邮箱验证码（此时账号尚未创建，按邮箱维度限流/存储） */
    public void sendRegisterCode(String email) {
        checkEmailFormat(email);
        AssertUtil.isTrue(userDao.getByEmail(email) == null, "该邮箱已被注册");
        sendCode(registerKey(email), email);
    }

    /** 注册：校验邮箱验证码（成功后由调用方负责创建账号，此处不删除验证码，避免创建失败后无法重试） */
    public void verifyRegisterCode(String email, String code) {
        verifyCode(registerKey(email), code);
    }

    /** 注册成功后清理验证码 */
    public void clearRegisterCode(String email) {
        RedisUtils.del(registerKey(email));
    }

    /** 绑定邮箱：发送验证码 */
    public void sendBindCode(Long uid, String email) {
        checkEmailFormat(email);
        User exist = userDao.getByEmail(email);
        AssertUtil.isTrue(exist == null || Objects.equals(exist.getId(), uid), "该邮箱已被其他账号绑定");
        sendCode(bindKey(uid, email), email);
    }

    /** 绑定邮箱：校验验证码后写入 */
    public void bindEmail(Long uid, String email, String code) {
        checkEmailFormat(email);
        verifyCode(bindKey(uid, email), code);
        User exist = userDao.getByEmail(email);
        AssertUtil.isTrue(exist == null || Objects.equals(exist.getId(), uid), "该邮箱已被其他账号绑定");
        userDao.modifyEmail(uid, email);
        RedisUtils.del(bindKey(uid, email));
    }

    /** 邮箱登录：邮箱 + 密码 */
    public User loginByEmail(String email, String password) {
        User user = userDao.getByEmail(email);
        if (user == null || !ENCODER.matches(password, user.getPassword())) {
            throw new BusinessException("邮箱或密码错误");
        }
        AssertUtil.isFalse(user.getStatus() != null && user.getStatus() == 1, "账号已被禁用");
        return user;
    }

    /** 验证码登录：发送验证码到已注册邮箱 */
    public void sendLoginCode(String email) {
        checkEmailFormat(email);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        AssertUtil.isFalse(user.getStatus() != null && user.getStatus() == 1, "账号已被禁用");
        sendCode(loginKey(email), email);
    }

    /** 验证码登录：校验验证码后返回用户（验证通过即消费掉验证码，防止重放） */
    public User loginByEmailCode(String email, String code) {
        checkEmailFormat(email);
        verifyCode(loginKey(email), code);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        AssertUtil.isFalse(user.getStatus() != null && user.getStatus() == 1, "账号已被禁用");
        RedisUtils.del(loginKey(email));
        return user;
    }

    /** 找回密码：发送验证码到已绑定邮箱 */
    public void sendForgotCode(String email) {
        checkEmailFormat(email);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        sendCode(forgotKey(email), email);
    }

    /** 找回密码：校验验证码 + 强度后重置 */
    public void resetByCode(String email, String code, String newPassword) {
        AssertUtil.isTrue(newPassword != null && STRONG_PWD.matcher(newPassword).matches(),
                "新密码至少 8 位，且需同时包含大小写字母和数字");
        verifyCode(forgotKey(email), code);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        userDao.modifyPassword(user.getId(), ENCODER.encode(newPassword));
        RedisUtils.del(forgotKey(email));
        log.info("邮箱找回密码成功: email={}, uid={}", email, user.getId());
    }

    private void sendCode(String key, String email) {
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.set(key, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        mailService.sendCode(email, code);
    }

    private void verifyCode(String key, String code) {
        AssertUtil.isFalse(StringUtils.isBlank(code), "请输入验证码");
        String real = RedisUtils.getStr(key);
        AssertUtil.isNotEmpty(real, "验证码已过期，请重新获取");
        AssertUtil.equal(real, code.trim(), "验证码错误");
    }

    private void checkEmailFormat(String email) {
        AssertUtil.isTrue(email != null && EMAIL.matcher(email).matches(), "邮箱格式有误");
    }

    private String bindKey(Long uid, String email) {
        return "email:bind:" + uid + ":" + email;
    }

    private String forgotKey(String email) {
        return "email:forgot:" + email;
    }

    private String loginKey(String email) {
        return "email:login:" + email;
    }

    private String registerKey(String email) {
        return "email:register:" + email;
    }
}
