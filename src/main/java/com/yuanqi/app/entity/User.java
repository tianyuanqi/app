package com.yuanqi.app.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_user")
public class User {

    //系统生成的底层数字主键（雪花算法）
//    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    //对外展示的用户id
//    private String userid;
    private String username;
    private String password;
    private Date birth;
    private int gender;

    private String email;
    private String role;

}
