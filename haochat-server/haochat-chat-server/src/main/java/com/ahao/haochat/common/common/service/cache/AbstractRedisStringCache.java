package com.ahao.haochat.common.common.service.cache;

import cn.hutool.core.collection.CollectionUtil;
import com.ahao.haochat.common.common.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Redis string batch cache template.
 */
@Slf4j
public abstract class AbstractRedisStringCache<IN, OUT> implements BatchCache<IN, OUT> {

    private final Class<OUT> outClass;

    protected AbstractRedisStringCache() {
        ParameterizedType genericSuperclass = (ParameterizedType) this.getClass().getGenericSuperclass();
        this.outClass = (Class<OUT>) genericSuperclass.getActualTypeArguments()[1];
    }

    protected abstract String getKey(IN req);

    protected abstract Long getExpireSeconds();

    protected abstract Map<IN, OUT> load(List<IN> req);

    @Override
    public OUT get(IN req) {
        return getBatch(Collections.singletonList(req)).get(req);
    }

    @Override
    public Map<IN, OUT> getBatch(List<IN> req) {
        if (CollectionUtil.isEmpty(req)) {
            return new HashMap<>();
        }
        req = req.stream().distinct().collect(Collectors.toList());
        List<String> keys = req.stream().map(this::getKey).collect(Collectors.toList());

        List<OUT> valueList;
        try {
            valueList = RedisUtils.mget(keys, outClass);
        } catch (Exception e) {
            log.warn("redis batch get failed, fallback to load, cache={}", getClass().getSimpleName(), e);
            return load(req);
        }
        if (valueList.size() != req.size()) {
            log.warn("redis batch get size mismatch, fallback to load, cache={}, reqSize={}, valueSize={}",
                    getClass().getSimpleName(), req.size(), valueList.size());
            return load(req);
        }

        List<IN> loadReqs = new ArrayList<>();
        for (int i = 0; i < valueList.size(); i++) {
            if (Objects.isNull(valueList.get(i))) {
                loadReqs.add(req.get(i));
            }
        }

        Map<IN, OUT> load = new HashMap<>();
        if (CollectionUtil.isNotEmpty(loadReqs)) {
            load = load(loadReqs);
            Map<String, OUT> loadMap = load.entrySet().stream()
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .map(entry -> Pair.of(getKey(entry.getKey()), entry.getValue()))
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (oldValue, newValue) -> newValue));
            if (CollectionUtil.isNotEmpty(loadMap)) {
                try {
                    RedisUtils.mset(loadMap, getExpireSeconds());
                } catch (Exception e) {
                    log.warn("redis batch set failed, ignore cache write, cache={}", getClass().getSimpleName(), e);
                }
            }
        }

        Map<IN, OUT> resultMap = new HashMap<>();
        for (int i = 0; i < req.size(); i++) {
            IN in = req.get(i);
            OUT out = Optional.ofNullable(valueList.get(i)).orElse(load.get(in));
            resultMap.put(in, out);
        }
        return resultMap;
    }

    public void put(IN req, OUT value) {
        try {
            RedisUtils.set(getKey(req), value, getExpireSeconds());
        } catch (Exception e) {
            log.warn("redis set failed, ignore cache write, cache={}", getClass().getSimpleName(), e);
        }
    }

    @Override
    public void delete(IN req) {
        deleteBatch(Collections.singletonList(req));
    }

    @Override
    public void deleteBatch(List<IN> req) {
        if (CollectionUtil.isEmpty(req)) {
            return;
        }
        List<String> keys = req.stream().map(this::getKey).collect(Collectors.toList());
        try {
            RedisUtils.del(keys);
        } catch (Exception e) {
            log.warn("redis delete failed, ignore cache delete, cache={}", getClass().getSimpleName(), e);
        }
    }
}
