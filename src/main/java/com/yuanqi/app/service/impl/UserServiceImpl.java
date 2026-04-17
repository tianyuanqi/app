package com.yuanqi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.common.JwtUtils;
import com.yuanqi.app.entity.User;
import com.yuanqi.app.mapper.UserMapper;
import com.yuanqi.app.service.UserService;
import com.yuanqi.app.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    // 初始化加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    public LoginVO Userlogin(String username, String password) {
        // 1. 根据用户名查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);

        // 2. 账号校验
        if (user == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }

//        System.out.println("【前端传入的明文密码】：" + password);
//        System.out.println("【数据库查出的密文密码】：" + user.getPassword());
//        System.out.println("【本机原生生成的正确密文】：" + passwordEncoder.encode("123456"));
        // 3. 密码哈希比对 (工业级核心：不直接比对字符串，而是比对哈希值)
        // passwordEncoder.matches(前端传的明文, 数据库里的加密密文)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 4. 签发 JWT 令牌
        String token = JwtUtils.createToken(user.getId(), user.getUsername());

        // 5. 组装返回给前端的 VO
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());

        return vo;
    }
}


//    @Override
//    public String Userlogin(String username, String password) {
//// 1. 创建一个条件构造器 (相当于在组装 SQL 的 WHERE 条件)
//        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
//
//        // 2. 拼接条件：WHERE username = ? AND password = ?
//        queryWrapper.eq(User::getUsername, username)
//                .eq(User::getPassword, password);
//
//        // 3. 调用 selectOne 执行查询
//        // selectOne 期望最多返回一条数据。如果数据库里有两条同名同密码的，这里会报错。
//        User user = userMapper.selectOne(queryWrapper);
//
//        // 4. 判断结果
//        if (user != null) {
//            return "登录成功，欢迎: " + user.getUsername();
//        } else {
//            // 如果查不到，说明账号或密码错误
//            throw new IllegalArgumentException("用户名或密码错误");
//        }
//    }
//}
