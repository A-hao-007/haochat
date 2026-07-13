package com.ahao.haochat.common.chatai.dao;

import com.ahao.haochat.common.chatai.domain.entity.AiPromptTemplate;
import com.ahao.haochat.common.chatai.mapper.AiPromptTemplateMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiPromptTemplateDao extends ServiceImpl<AiPromptTemplateMapper, AiPromptTemplate> {
    public AiPromptTemplate active(String code) {
        return lambdaQuery()
                .eq(AiPromptTemplate::getCode, code)
                .eq(AiPromptTemplate::getStatus, 1)
                .orderByDesc(AiPromptTemplate::getVersion)
                .last("limit 1")
                .one();
    }

    public List<AiPromptTemplate> listByCode(String code) {
        return lambdaQuery()
                .eq(code != null, AiPromptTemplate::getCode, code)
                .orderByDesc(AiPromptTemplate::getCode)
                .orderByDesc(AiPromptTemplate::getVersion)
                .list();
    }
}
