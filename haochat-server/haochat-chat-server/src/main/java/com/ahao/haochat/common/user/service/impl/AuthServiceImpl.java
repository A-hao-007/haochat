package com.ahao.haochat.common.user.service.impl;

import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.vo.request.auth.LoginReq;
import com.ahao.haochat.common.user.domain.vo.request.auth.RegisterReq;
import com.ahao.haochat.common.user.service.AuthSecurityService;
import com.ahao.haochat.common.user.service.EmailAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class AuthServiceImpl {

    @Autowired
    private UserDao userDao;
    @Autowired
    private EmailAuthService emailAuthService;
    @Autowired
    private AuthSecurityService authSecurityService;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public User register(RegisterReq req) {
        User existByUsername = userDao.getByUsername(req.getUsername());
        if (existByUsername != null) {
            throw new BusinessException("用户名已被注册");
        }
        User existByName = userDao.getByName(req.getName());
        if (existByName != null) {
            throw new BusinessException("昵称已被使用");
        }
        User existByEmail = userDao.getByEmail(req.getEmail());
        if (existByEmail != null) {
            throw new BusinessException("该邮箱已被注册");
        }
        emailAuthService.verifyRegisterCode(req.getEmail(), req.getEmailCode());

        User user = User.builder()
                .username(req.getUsername())
                .name(req.getName())
                .email(req.getEmail())
                .openId(UUID.randomUUID().toString().replace("-", ""))
                .password(passwordEncoder.encode(req.getPassword()))
                .activeStatus(2)
                .status(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        userDao.save(user);
        emailAuthService.clearRegisterCode(req.getEmail());
        log.info("user registered: username={}, uid={}, email={}", req.getUsername(), user.getId(), req.getEmail());
        return user;
    }

    public User login(LoginReq req) {
        authSecurityService.checkLoginAllowed(req.getUsername());
        User user = userDao.getByUsername(req.getUsername());
        if (user == null) {
            authSecurityService.onLoginFailure(null, req.getUsername(), "PASSWORD", "bad_credentials");
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            authSecurityService.onLoginFailure(user.getId(), req.getUsername(), "PASSWORD", "disabled");
            throw new BusinessException("账号已被禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            authSecurityService.onLoginFailure(user.getId(), req.getUsername(), "PASSWORD", "bad_credentials");
            throw new BusinessException("用户名或密码错误");
        }
        authSecurityService.onLoginSuccess(user.getId(), req.getUsername(), "PASSWORD");
        log.info("user login success: username={}, uid={}", req.getUsername(), user.getId());
        return user;
    }
}
