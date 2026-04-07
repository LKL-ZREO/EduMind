package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Result;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;


    @PostMapping("/register")
    public  Result<Void> register(@RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success(null);
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO dto) {
        UserLoginVO vo = userService.login(dto);
        return Result.success(vo);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(null);
    }

}