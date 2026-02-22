package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.management.Query;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService iUserService;

    @Override
    public Result isFollow(Long followUserId) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();

        //查询是否关注用户
        Boolean isFollow = count(new QueryWrapper<Follow>()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)) > 0;
        return Result.ok(isFollow);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;

        //判断是否关注
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);

            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id，放入redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //取消关注
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));

            // 取消关注成功，把关注用户的id从Redis的set集合中移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommon(Long followUserId) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        // 2.求交集
        String key2 = "follow:" + followUserId;
        // 3. 求交集 相当于reids语句：SINTER follows:1011 follows:2
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3. 转换为Long类型
        List<Long> commonFollowUserIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4. 查询用户信息
        List<UserDTO> commonFollowUserDTOs =  iUserService.listByIds(commonFollowUserIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(commonFollowUserDTOs);
    }
}
