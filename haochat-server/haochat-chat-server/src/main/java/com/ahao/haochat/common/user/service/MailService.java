package com.ahao.haochat.common.user.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送：发送验证码。未配置 SMTP（无 JavaMailSender 或 from 为空）时降级为日志输出，便于联调。
 */
@Slf4j
@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:}")
    private String from;

    public void sendCode(String to, String code) {
        String text = "【HaoChat】你的验证码是 " + code + " ，10 分钟内有效，请勿泄露。";
        if (mailSender == null || StringUtils.isBlank(from)) {
            log.warn("[MAIL-FALLBACK] 未配置 SMTP，验证码发往 {} -> {}", to, code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("HaoChat 验证码");
            message.setText(text);
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", to);
        } catch (Exception e) {
            log.warn("验证码邮件发送失败 to={}: {}", to, e.getMessage());
            throw new com.ahao.haochat.common.common.exception.BusinessException("邮件发送失败，请稍后再试");
        }
    }
}
