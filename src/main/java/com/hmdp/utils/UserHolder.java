package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    // 1. 定义ThreadLocal，用于存储当前线程的用户信息。
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // 2. 提供保存用户信息到ThreadLocal的方法
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    // 3. 提供从ThreadLocal获取用户信息的方法
    public static UserDTO getUser(){
        return tl.get();
    }

    // 4. 提供移除用户信息的方法，避免内存泄漏
    public static void removeUser(){
        tl.remove();
    }
}
