package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.VO.UserLoginVO;


public interface UserService {
    void register(UserRegisterDTO dto);
    UserLoginVO login(UserLoginDTO dto);
}
