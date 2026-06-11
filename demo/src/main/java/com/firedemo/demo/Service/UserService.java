package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.VO.UserLoginVO;

/**
 * 用户 Service
 */
public interface UserService {

    void register(UserRegisterDTO dto);

    UserLoginVO login(UserLoginDTO dto);

    User getById(Long id);
}
