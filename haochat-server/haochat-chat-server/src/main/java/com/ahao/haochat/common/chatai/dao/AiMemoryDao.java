package com.ahao.haochat.common.chatai.dao;

import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.mapper.AiMemoryMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AiMemoryDao extends ServiceImpl<AiMemoryMapper, AiMemory> {
}
