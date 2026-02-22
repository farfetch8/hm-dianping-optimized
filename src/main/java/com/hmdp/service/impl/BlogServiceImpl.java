package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static com.hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        // 查询是否点赞过
        isBlogLiked(id, blog);

        // 查询用户
        queryBlogUser(blog);

        // 返回
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();

        // 查询是否点赞过
        records.forEach(blog -> isBlogLiked(blog.getId(), blog));

        // 查询用户
        records.forEach(this::queryBlogUser);

        // 返回
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 使用Redis的sortedset集合来记录点赞用户
        // 1.1 构建Redis的key
        String key = BLOG_LIKED_KEY + id;
        // 1.2 获取当前登录用户
        String userId = UserHolder.getUser().getId().toString();
        // 1.3 获取当前时间戳作为score
        long currentTime = System.currentTimeMillis();

        // 2. 检查用户是否点赞过
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score != null) {
            // 3.1 如果点赞过，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.2 把用户从Redis的sortedset集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
        } else {
            //4.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //4.2 保存用户到Redis的sortedset集合，score为当前时间戳
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId, currentTime);
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);

        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 2. 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败!");
        }

        // 3. 查询笔记作者的所有粉丝，从tb_follow中查询  select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        // 4. 把探店博文的id保存到每个粉丝的关注列表中
        // 4.1 遍历所有粉丝
        for (Follow follow : follows) {
            // 4.2 获取粉丝id
            Long followUserId = follow.getUserId();
            // 4.3 构建Redis的key
            String key = FEED_KEY + followUserId;
            // 4.4 把探店博文的id保存到粉丝的关注列表中
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 5. 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2. 查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        // 2.1 构建Redis的key
        String key = FEED_KEY + userId;
        // 2.2 查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        // 3. 解析数据的最小时间戳、上一次查询相同地查询个数作为偏移量(offset)、blog的id列表
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3.1 解析数据的最小时间戳
        long minTime = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore)
                .mapToLong(Double::longValue)
                .min().orElse(0L);
        // 3.2 解析在上一次查询中与最小时间戳相同的元素个数作为偏移量(offset)
        long newOffset = typedTuples.stream()
                .filter(tuple -> tuple.getScore().longValue() == minTime)
                .count();
        // 3.3 解析blog的id列表
        List<Long> ids = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 4. 根据id查询blog
        // 4.1 构建blog的id列表
        String idStr = StrUtil.join(",", ids);
        // 4.2 根据id查询blog
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 4.3 封装blog的作者信息
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog.getId(), blog);
        }

        // 5. 封装数据成ScrollResult对象，返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset((int) newOffset);

        return Result.ok(scrollResult);
    }

    private void isBlogLiked(Long id, Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        String userId = user.getId().toString();
        // 使用SortedSet的score()方法检查用户是否点赞，存在返回点赞时间戳，不存在返回null
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId);
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
