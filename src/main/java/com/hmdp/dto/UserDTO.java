package com.hmdp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
// User是实体类，UserDTO是用于传输的类 对User实体类进行封装，只包含需要传输的字段
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
