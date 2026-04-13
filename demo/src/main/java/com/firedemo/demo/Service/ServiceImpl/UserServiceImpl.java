package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.mapper.UserMapper;
import com.firedemo.demo.mapper.ChatHistoryMapper;
import com.firedemo.demo.utils.JwtUtil;
import com.firedemo.demo.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final JwtUtil jwtUtil;
    private final PasswordUtil passwordUtil;

    @Override
    public void register(UserRegisterDTO dto) {
        // 1. 校验用户名是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        Long count = Long.valueOf(userMapper.selectCount(wrapper));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. DTO 转 Entity
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        // 3. 数据库字段赋值
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setStatus(Integer.valueOf(dto.getStatus()));

        // 4. 插入数据库
        userMapper.insert(user);
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        // 1. 查用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 校验密码
        if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 检查状态
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 4. 生成 JWT（带上 status，用于选择 agent：1=main, 2=jarvis）
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getStatus());

        // 5. 获取或生成 sessionId（查最近的历史记录）
        String sessionId = getOrCreateSessionId(user.getId());

        // 6. 返回登录信息
        return UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .token(token)
                .sessionId(sessionId)
                .build();
    }

    /**
     * 获取用户最近的 sessionId，没有则创建新的
     */
    private String getOrCreateSessionId(Long userId) {
        List<String> sessionIds = chatHistoryMapper.selectSessionIdsByUserId(userId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            return sessionIds.get(0); // 返回最新的 sessionId
        }
        // 没有历史记录，生成新的
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }

}
