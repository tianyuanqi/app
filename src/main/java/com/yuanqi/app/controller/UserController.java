package com.yuanqi.app.controller;


import com.yuanqi.app.common.Result;
import com.yuanqi.app.entity.User;
import com.yuanqi.app.service.UserService;
import com.yuanqi.app.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/users")
public class UserController {

    @Autowired
    private UserService userService;


    @PostMapping("/login")
    public Result login(@RequestBody User loginuser){
        LoginVO vo = userService.Userlogin(loginuser.getUsername(),loginuser.getPassword());
        return Result.success(vo);
    }

}
