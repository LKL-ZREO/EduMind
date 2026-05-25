package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.User;

/**
 * 用户 Service
 */
public interface UserService {

    void register(com.firedemo.demo.DTO.UserRegisterDTO dto);

    com.firedemo.demo.VO.UserLoginVO login(com.firedemo.demo.DTO.UserLoginDTO dto);

    User getById(Long id);
}
