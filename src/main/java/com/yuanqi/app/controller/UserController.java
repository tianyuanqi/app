package com.yuanqi.app.controller;


import com.yuanqi.app.common.Result;
import com.yuanqi.app.dto.LoginDto;
import com.yuanqi.app.service.UserService;
import com.yuanqi.app.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1. 用户管理",description = "负责用户的登录，注册，查看收藏等操作")
@RestController
@RequestMapping("api/users")
public class UserController {

    @Autowired
    private UserService userService;


    @Operation(summary = "用户登录",description = "校验账号密码，成功后签发并发放 JWT Token 通行证")
    @PostMapping("/login")
    public Result login(@RequestBody LoginDto loginuser){
        LoginVO vo = userService.Userlogin(loginuser.getUsername(),loginuser.getPassword());
        return Result.success(vo);
    }

}
