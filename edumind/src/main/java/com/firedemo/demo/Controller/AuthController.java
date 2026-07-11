package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Service.TokenService;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final JwtUtil jwtUtil;

    private static final String TOKEN_COOKIE = "edumind_token";
    /** Cookie 有效期（秒），与 JWT 过期时间一致 */
    private static final int COOKIE_MAX_AGE = (int) JwtUtil.EXPIRATION_SECONDS;

    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
               count = 3, interval = 60, timeUnit = RateLimit.TimeUnit.SECONDS)
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success(null);
    }

    /**
     * 登录 — 双层限流 + 返回 JWT + 刷新令牌 + HttpOnly Cookie
     */
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
               count = 5, interval = 60, timeUnit = RateLimit.TimeUnit.SECONDS)
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto, HttpServletResponse response) {
        UserLoginVO vo = userService.login(dto);

        // 设置 HttpOnly Cookie — 前端无需手动管理 token
        Cookie cookie = new Cookie(TOKEN_COOKIE, vo.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false);  // 本地开发用 HTTP，生产 Nginx 会设 Secure
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        return Result.success(vo);
    }

    /**
     * 刷新令牌 — 用 Refresh Token 换新的 Access Token。
     * Refresh Token 单次使用，换成功后旧 token 立即失效。
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body,
                                                HttpServletResponse response) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.error(400, "缺少 refreshToken");
        }

        Long userId = tokenService.consumeRefreshToken(refreshToken);
        if (userId == null) {
            return Result.error(401, "刷新令牌无效或已过期，请重新登录");
        }

        // 查用户信息
        var user = userService.getById(userId);
        if (user == null || user.getStatus() == 0) {
            return Result.error(401, "账号已被禁用");
        }

        // 签发新的 Access Token + Refresh Token
        String newToken = jwtUtil.generateToken(userId, user.getUsername(), user.getStatus());
        String newRefreshToken = tokenService.createRefreshToken(userId);

        // 更新 Cookie
        Cookie cookie = new Cookie(TOKEN_COOKIE, newToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        return Result.success(Map.of(
                "token", newToken,
                "refreshToken", newRefreshToken,
                "expiresIn", JwtUtil.EXPIRATION_SECONDS
        ));
    }

    /**
     * 退出登录 — 吊销 Access Token + 清除 Cookie
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 提取 token
        String token = jwtUtil.extractTokenFromRequest(request);

        // Cookie 回退
        if (token == null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (TOKEN_COOKIE.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        // 加入黑名单
        if (token != null) {
            long remaining = jwtUtil.getRemainingSeconds(token);
            tokenService.blacklist(token, remaining);
        }

        // 清除 Cookie
        Cookie cookie = new Cookie(TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return Result.success(null);
    }
}
