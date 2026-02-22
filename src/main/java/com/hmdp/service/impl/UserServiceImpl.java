package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 校验失败，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 2. 校验成功，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. 发送验证码（这里简单模拟，直接打印到控制台）
        log.debug("发送验证码：{}", code);

        // 5. 返回验证码
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 校验失败，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 2. 校验验证码
        String code = loginForm.getCode();
        // 2.1 从Redis获取验证码
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (redisCode == null || !redisCode.equals(code)) {
            // 2.2 校验失败，返回错误信息
            return Result.fail("验证码错误");
        }

        // 3. 校验成功，根据手机号查询用户
        // query() 方法是 MyBatis-Plus 提供的查询方法，用于根据条件查询单条记录 相当于 select * from tb_user where phone = phone
        // eq() 方法是查询条件方法，用于添加等于条件
        // one() 方法是查询方法，用于返回单条记录
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 3.1 用户不存在，创建新用户
            user = createUserWithPhone(phone);
        }

        // 4. 将用户信息保存到Redis
        // 4.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);

        // 4.2 将User对象转为HashMap存储，方便后续查询
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 4.3 保存到Redis expire的作用是设置key的过期时间，单位是分钟
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5. 校验成功，登录成功
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月截止到今天的签到记录返回的是一个十进制的数字  BITFIELD key GET u[dayOfMonth] 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        // 6.解析出签到记录
        if (CollectionUtils.isEmpty(result)) {
            // 6.1 如果为空，说明没有签到记录，返回0
            return Result.ok(0);
        }
        long count = result.get(0) == null ? 0 : result.get(0);

        // 7. 把签到结果和1进行与操作，每与一次，就把签到结果向右移动一位，依次内推，直到遇到第一次未签到为止.
        // 7.1 初始化签到次数
        int signCount = 0;
        // 7.2 遍历签到记录
        while ((count & 1) == 1) {
            // 7.3 与操作，判断是否签到
            signCount++;
            // 7.4 右移一位，继续判断下一天
            count >>>= 1;
        }

        // 8. 返回签到次数
        return Result.ok(signCount);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        // 1. 获取Token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return Result.ok();
        }
        // 2. 删除Redis中的用户
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);
        // 3. 返回
        return Result.ok();
    }

}
