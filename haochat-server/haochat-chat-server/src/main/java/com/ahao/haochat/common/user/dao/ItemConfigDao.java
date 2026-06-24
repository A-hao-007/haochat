package com.ahao.haochat.common.user.dao;

import com.ahao.haochat.common.user.domain.entity.ItemConfig;
import com.ahao.haochat.common.user.mapper.ItemConfigMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 功能物品配置表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-03-19
 */
@Service
public class ItemConfigDao extends ServiceImpl<ItemConfigMapper, ItemConfig> {

    public List<ItemConfig> getByType(Integer type) {
        return lambdaQuery().eq(ItemConfig::getType, type).list();
    }
}
