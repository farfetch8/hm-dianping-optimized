package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.utils.BatchUserLoginTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量登录控制器
 * 用于触发批量用户登录操作
 */
@Slf4j
@RestController
@RequestMapping("/batch")
public class BatchLoginController {

    @Autowired
    private BatchUserLoginTool batchUserLoginTool;

    /**
     * 批量登录所有用户
     */
    @PostMapping("/login-all-users")
    public Result loginAllUsers() {
        // 异步执行批量登录，避免请求超时
        new Thread(() -> {
            batchUserLoginTool.loginAllUsers();
        }).start();
        return Result.ok("批量登录操作已开始，请查看日志了解进度");
    }

    /**
     * 清理所有用户的登录状态
     */
    @PostMapping("/clear-all-tokens")
    public Result clearAllTokens() {
        new Thread(() -> {
            batchUserLoginTool.clearAllUserTokens();
        }).start();
        return Result.ok("批量清理token操作已开始，请查看日志了解进度");
    }

    /**
     * 导出所有用户的token到文件
     * 默认导出到项目根目录下的token.txt文件
     */
    @PostMapping("/export-tokens")
    public Result exportTokens() {
        // 异步执行导出操作
        new Thread(() -> {
            try {
                // 默认导出到项目根目录下的token.txt
                String filePath = "token.txt";
                int count = batchUserLoginTool.exportTokensToFile(filePath);
                log.info("成功导出 {} 个token到文件: {}", count, filePath);
            } catch (Exception e) {
                log.error("导出token到文件失败: {}", e.getMessage(), e);
            }
        }).start();
        return Result.ok("token导出操作已开始，请查看日志了解进度");
    }
}