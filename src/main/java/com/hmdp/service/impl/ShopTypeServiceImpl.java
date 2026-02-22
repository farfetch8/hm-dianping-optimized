package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper; // JSON序列化工具

    @Override
    public List<ShopType> queryList() {
        //1.从redis缓存中查询
        String cacheKey = CACHE_SHOP_KEY + "type";
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        List<ShopType> typeList = null;

        //2.如果缓存存在，解析JSON并返回
        if (json != null) {
            typeList = JSONUtil.toList(json, ShopType.class);
            return typeList;
        }

        //3.如果缓存为空，从数据库查询
        typeList = query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return Collections.emptyList();
        }

        //4.将查询结果写入redis缓存
        String typeJson = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(cacheKey, typeJson);
        // 设置过期时间，例如5分钟
        stringRedisTemplate.expire(cacheKey, 5, TimeUnit.MINUTES);

        return typeList;
    }

}
