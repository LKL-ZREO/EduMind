package com.firedemo.edumind.auth;

import com.firedemo.edumind.platform.ratelimit.RateLimit;
import com.firedemo.edumind.shared.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;

    /** SPA 获取 CSRF token；访问该端点会同步写入 XSRF-TOKEN Cookie。 */
    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        return Result.success(Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName()));
    }

    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
               count = 3, interval = 60, timeUnit = RateLimit.TimeUnit.SECONDS)
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success(null);
    }

    /**
     * 登录并将教师身份写入 Redis-backed HttpSession。
     */
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
               count = 5, interval = 60, timeUnit = RateLimit.TimeUnit.SECONDS)
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        UserLoginVO vo = userService.login(dto);

        HttpSession session = request.getSession(false);
        if (session == null) {
            request.getSession(true);
        } else {
            request.changeSessionId();
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        vo.getUsername(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        authentication.setDetails(vo.getId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return Result.success(vo);
    }

    @GetMapping("/me")
    public Result<UserLoginVO> me(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Long userId)) {
            return Result.error(401, "未登录");
        }
        var user = userService.getById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            return Result.error(401, "账号已被禁用或不存在");
        }
        return Result.success(UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build());
    }

    /** 退出登录后 Redis Session 立即失效。 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Result.success(null);
    }
}
