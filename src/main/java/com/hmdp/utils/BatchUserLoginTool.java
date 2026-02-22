package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 批量用户登录工具类
 * 用于为tb_user表中的所有用户生成token并保存到Redis
 */
@Slf4j
@Component
public class BatchUserLoginTool {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 为所有用户生成登录token并保存到Redis
     */
    public void loginAllUsers() {
        // 1. 查询所有用户
        List<User> allUsers = userService.list();
        log.info("开始为 {} 个用户生成登录token", allUsers.size());

        int successCount = 0;
        // 2. 为每个用户生成token并保存到Redis
        for (User user : allUsers) {
            try {
                // 生成token
                String token = UUID.randomUUID().toString(true);

                // 将User对象转为UserDTO并存储到Redis
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

                // 保存到Redis
                String redisKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(redisKey, userMap);
                stringRedisTemplate.expire(redisKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

                // 记录token与用户ID的映射关系，便于后续查询
                stringRedisTemplate.opsForValue().set("user:token:" + user.getId(), token, LOGIN_USER_TTL, TimeUnit.MINUTES);

                successCount++;

                // 每处理100个用户打印一次日志
                if (successCount % 100 == 0) {
                    log.info("已处理 {} 个用户", successCount);
                }

            } catch (Exception e) {
                log.error("处理用户 {} 时出错: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("批量登录完成，成功处理 {} 个用户", successCount);
    }

    /**
     * 清理所有用户的登录token
     */
    public void clearAllUserTokens() {
        try {
            // 获取所有用户ID
            List<User> allUsers = userService.list();
            log.info("开始清理 {} 个用户的登录token", allUsers.size());

            int clearCount = 0;
            for (User user : allUsers) {
                try {
                    // 获取用户的token
                    String tokenKey = "user:token:" + user.getId();
                    String token = stringRedisTemplate.opsForValue().get(tokenKey);

                    if (token != null) {
                        // 删除用户登录信息
                        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
                        stringRedisTemplate.delete(tokenKey);
                        clearCount++;
                    }

                } catch (Exception e) {
                    log.error("清理用户 {} 的token时出错: {}", user.getId(), e.getMessage(), e);
                }
            }

            log.info("清理完成，成功清理 {} 个用户的token", clearCount);
        } catch (Exception e) {
            log.error("清理用户token时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 将Redis中的用户token导出到文件
     * @param filePath 文件路径
     * @return 导出的token数量
     */
    public int exportTokensToFile(String filePath) {
        log.info("开始导出token到文件: {}", filePath);

        int exportCount = 0;
        BufferedWriter writer = null;

        try {
            // 创建文件写入器
            writer = new BufferedWriter(new FileWriter(filePath));

            // 查询所有用户
            List<User> allUsers = userService.list();
            log.info("开始从Redis获取 {} 个用户的token", allUsers.size());

            // 遍历用户，获取token并写入文件
            for (User user : allUsers) {
                try {
                    // 获取用户的token
                    String tokenKey = "user:token:" + user.getId();
                    String token = stringRedisTemplate.opsForValue().get(tokenKey);

                    if (token != null) {
                        // 写入token到文件，格式：用户ID,token
                        writer.write(token);
                        writer.newLine();
                        exportCount++;

                        // 每写入100个token刷新一次缓冲区
                        if (exportCount % 100 == 0) {
                            writer.flush();
                            log.info("已导出 {} 个token到文件", exportCount);
                        }
                    }

                } catch (Exception e) {
                    log.error("导出用户 {} 的token时出错: {}", user.getId(), e.getMessage(), e);
                }
            }

            // 确保所有数据都写入文件
            writer.flush();
            log.info("token导出完成，成功导出 {} 个token到文件: {}", exportCount, filePath);

        } catch (IOException e) {
            log.error("创建或写入文件时出错: {}", e.getMessage(), e);
            throw new RuntimeException("导出token到文件失败", e);
        } finally {
            // 关闭写入器
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.error("关闭文件写入器时出错: {}", e.getMessage(), e);
                }
            }
        }

        return exportCount;
    }
}
