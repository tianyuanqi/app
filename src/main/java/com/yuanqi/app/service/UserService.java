package com.yuanqi.app.service;

import com.yuanqi.app.vo.LoginVO;

public interface UserService {

    LoginVO Userlogin(String username, String password);
}
