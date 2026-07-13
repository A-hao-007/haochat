package com.ahao.haochat.common.user.service;

import cn.hutool.core.util.RandomUtil;
import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.common.utils.AssertUtil;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.dao.UserSessionDao;
import com.ahao.haochat.common.user.domain.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
    @Autowired
    private AuthSecurityService authSecurityService;
    @Autowired
    private UserSessionDao userSessionDao;

    public void sendRegisterCode(String email) {
        checkEmailFormat(email);
        AssertUtil.isTrue(userDao.getByEmail(email) == null, "该邮箱已被注册");
        sendCode(codeKey("register", email), email);
    }

    public void verifyRegisterCode(String email, String code) {
        verifyCode(codeKey("register", email), code);
    }

    public void clearRegisterCode(String email) {
        RedisUtils.del(codeKey("register", email));
    }

    public void sendBindCode(Long uid, String email) {
        checkEmailFormat(email);
        User exist = userDao.getByEmail(email);
        AssertUtil.isTrue(exist == null || Objects.equals(exist.getId(), uid), "该邮箱已被其他账号绑定");
        sendCode(codeKey("bind:" + uid, email), email);
    }

    public void bindEmail(Long uid, String email, String code) {
        checkEmailFormat(email);
        verifyCode(codeKey("bind:" + uid, email), code);
        User exist = userDao.getByEmail(email);
        AssertUtil.isTrue(exist == null || Objects.equals(exist.getId(), uid), "该邮箱已被其他账号绑定");
        userDao.modifyEmail(uid, email);
        RedisUtils.del(codeKey("bind:" + uid, email));
    }

    public User loginByEmail(String email, String password) {
        authSecurityService.checkLoginAllowed(email);
        User user = userDao.getByEmail(email);
        if (user == null || !ENCODER.matches(password, user.getPassword())) {
            authSecurityService.onLoginFailure(user == null ? null : user.getId(), email, "EMAIL_PASSWORD", "bad_credentials");
            throw new BusinessException("邮箱或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            authSecurityService.onLoginFailure(user.getId(), email, "EMAIL_PASSWORD", "disabled");
            throw new BusinessException("账号已被禁用");
        }
        authSecurityService.onLoginSuccess(user.getId(), email, "EMAIL_PASSWORD");
        return user;
    }

    public void sendLoginCode(String email) {
        checkEmailFormat(email);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        AssertUtil.isFalse(user.getStatus() != null && user.getStatus() == 1, "账号已被禁用");
        sendCode(codeKey("login", email), email);
    }

    public User loginByEmailCode(String email, String code) {
        checkEmailFormat(email);
        verifyCode(codeKey("login", email), code);
        User user = userDao.getByEmail(email);
        AssertUtil.isNotEmpty(user, "该邮箱未绑定任何账号");
        AssertUtil.isFalse(user.getStatus() != null && user.getStatus() == 1, "账号已被禁用");
        RedisUtils.del(codeKey("login", email));
        authSecurityService.onLoginSuccess(user.getId(), email, "EMAIL_CODE");
        return user;
    }

    /**
     * Forgot-password code sending must not reveal whether the email exists.
     */
    public void sendForgotCode(String email) {
        checkEmailFormat(email);
        User user = userDao.getByEmail(email);
        if (user == null || (user.getStatus() != null && user.getStatus() == 1)) {
            log.info("ignore forgot password code for non-existing/disabled email={}", email);
            return;
        }
        sendCode(codeKey("forgot", email), email);
    }

    public void resetByCode(String email, String code, String newPassword) {
        AssertUtil.isTrue(newPassword != null && STRONG_PWD.matcher(newPassword).matches(),
                "新密码至少 8 位，且需同时包含大小写字母和数字");
        verifyCode(codeKey("forgot", email), code);
        User user = userDao.getByEmail(email);
        if (user == null || (user.getStatus() != null && user.getStatus() == 1)) {
            throw new BusinessException("验证码错误或已过期");
        }
        userDao.modifyPassword(user.getId(), ENCODER.encode(newPassword));
        userSessionDao.revokeByUid(user.getId());
        RedisUtils.del(codeKey("forgot", email));
        log.info("forgot password reset success: email={}, uid={}", email, user.getId());
    }

    private void sendCode(String key, String email) {
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.set(key, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        mailService.sendCode(email, code);
    }

    private void verifyCode(String key, String code) {
        AssertUtil.isFalse(StringUtils.isBlank(code), "请输入验证码");
        String real = RedisUtils.getStr(key);
        AssertUtil.isNotEmpty(real, "验证码错误或已过期");
        AssertUtil.equal(real, code.trim(), "验证码错误或已过期");
    }

    private void checkEmailFormat(String email) {
        AssertUtil.isTrue(email != null && EMAIL.matcher(email).matches(), "邮箱格式有误");
    }

    private String codeKey(String scene, String email) {
        return "email:code:" + scene + ":" + email;
    }
}
