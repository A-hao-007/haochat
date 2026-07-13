package com.ahao.haochat.common.chatai.service;

import com.ahao.haochat.common.chatai.dao.AiPromptTemplateDao;
import com.ahao.haochat.common.chatai.domain.entity.AiPromptTemplate;
import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Versioned prompt lookup. Database templates override deployment defaults. */
@Service
@RequiredArgsConstructor
public class AiPromptService {
    public static final String SYSTEM = "SYSTEM";
    public static final String QUERY_REWRITE = "QUERY_REWRITE";
    public static final String MEMORY_EXTRACT = "MEMORY_EXTRACT";

    private final AiPromptTemplateDao promptTemplateDao;

    public String resolve(String code, String fallback) {
        try {
            AiPromptTemplate template = promptTemplateDao.active(code);
            if (template != null && StringUtils.isNotBlank(template.getContent())) {
                return template.getContent();
            }
        } catch (Exception ignored) {
            // The AI base path remains available before the migration is applied.
        }
        return fallback;
    }

    public String render(String code, String fallback, Map<String, String> variables) {
        String content = resolve(code, fallback);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return content;
    }

    public List<AiPromptTemplate> list(String code) {
        return promptTemplateDao.listByCode(code);
    }

    public AiPromptTemplate save(AiPromptTemplate template, Long operatorUid) {
        if (template.getVersion() == null || template.getVersion() < 1) {
            template.setVersion(1);
        }
        if (template.getStatus() == null) {
            template.setStatus(0);
        }
        if (template.getId() == null) {
            template.setCreatorUid(operatorUid);
            template.setCreateTime(new Date());
            promptTemplateDao.save(template);
        } else {
            promptTemplateDao.updateById(template);
        }
        return template;
    }

    public void activate(Long id) {
        AiPromptTemplate target = promptTemplateDao.getById(id);
        if (target == null) {
            throw new IllegalArgumentException("Prompt template does not exist");
        }
        promptTemplateDao.lambdaUpdate()
                .eq(AiPromptTemplate::getCode, target.getCode())
                .set(AiPromptTemplate::getStatus, 0)
                .update();
        promptTemplateDao.lambdaUpdate()
                .eq(AiPromptTemplate::getId, id)
                .set(AiPromptTemplate::getStatus, 1)
                .update();
        RedisUtils.del(RedisKey.getKey(RedisKey.AI_SYSTEM_PROMPT_STRING));
    }
}
