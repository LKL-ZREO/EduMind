package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.SharedKb;
import com.firedemo.demo.Entity.SharedKbMember;
import com.firedemo.demo.Service.SharedKbService;
import com.firedemo.demo.mapper.SharedKbMapper;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedKbServiceImpl implements SharedKbService {

    private final SharedKbMapper sharedKbMapper;
    private final SharedKbMemberMapper memberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SharedKb create(Long userId, String name, String description) {
        // 生成邀请 token
        String token = UUID.randomUUID().toString().replace("-", "");

        SharedKb kb = new SharedKb();
        kb.setName(name);
        kb.setDescription(description != null ? description : "");
        kb.setOwnerId(userId);
        kb.setInviteToken(token);
        kb.setInviteExpiresAt(LocalDateTime.now().plusDays(30));
        kb.setInviteMaxUses(0);
        kb.setInviteUseCount(0);
        sharedKbMapper.insert(kb);

        // 创建者自动加入 role=owner
        SharedKbMember owner = new SharedKbMember();
        owner.setKbId(kb.getId());
        owner.setUserId(userId);
        owner.setRole("owner");
        memberMapper.insert(owner);

        log.info("Shared KB created: id={}, name={}, owner={}", kb.getId(), name, userId);
        return kb;
    }

    @Override
    public List<SharedKb> getMy(Long userId) {
        return sharedKbMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKb>()
                        .eq(SharedKb::getOwnerId, userId));
    }

    @Override
    public List<SharedKb> getJoined(Long userId) {
        return sharedKbMapper.selectJoined(userId).stream()
                .filter(kb -> !kb.getOwnerId().equals(userId))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long kbId, String name, String description) {
        SharedKb kb = sharedKbMapper.selectById(kbId);
        checkOwner(kb, userId);
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        sharedKbMapper.updateById(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long kbId) {
        SharedKb kb = sharedKbMapper.selectById(kbId);
        checkOwner(kb, userId);
        // 级联删除成员、文档、目录节点由业务层调用方处理
        // 这里只删知识库本身 + 成员关系
        memberMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                .eq(SharedKbMember::getKbId, kbId));
        sharedKbMapper.deleteById(kbId);
        log.info("Shared KB deleted: id={}", kbId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateInvite(Long userId, Long kbId, Integer maxUses, Integer expireHours) {
        hasAccess(kbId, userId);
        SharedKb kb = sharedKbMapper.selectById(kbId);
        String token = UUID.randomUUID().toString().replace("-", "");
        kb.setInviteToken(token);
        kb.setInviteExpiresAt(expireHours != null ? LocalDateTime.now().plusHours(expireHours) : LocalDateTime.now().plusDays(30));
        kb.setInviteMaxUses(maxUses != null ? maxUses : 0);
        kb.setInviteUseCount(0);
        sharedKbMapper.updateById(kb);
        return token;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinByToken(Long userId, String token) {
        SharedKb kb = sharedKbMapper.selectByInviteToken(token);
        if (kb == null) throw new IllegalArgumentException("邀请链接无效");
        if (kb.getInviteExpiresAt() != null && kb.getInviteExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("邀请链接已过期");
        if (kb.getInviteMaxUses() > 0 && kb.getInviteUseCount() >= kb.getInviteMaxUses())
            throw new IllegalArgumentException("邀请链接已达最大使用次数");

        // 检查是否已是成员
        long count = memberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                        .eq(SharedKbMember::getKbId, kb.getId())
                        .eq(SharedKbMember::getUserId, userId));
        if (count > 0) throw new IllegalArgumentException("你已是该知识库成员");

        SharedKbMember member = new SharedKbMember();
        member.setKbId(kb.getId());
        member.setUserId(userId);
        member.setRole("member");
        memberMapper.insert(member);

        kb.setInviteUseCount(kb.getInviteUseCount() + 1);
        sharedKbMapper.updateById(kb);
        log.info("User {} joined shared KB {} via invite", userId, kb.getId());
    }

    @Override
    public Map<String, Object> getInviteInfo(String token) {
        SharedKb kb = sharedKbMapper.selectByInviteToken(token);
        if (kb == null) return Map.of("valid", false);
        Map<String, Object> info = new HashMap<>();
        info.put("valid", true);
        info.put("kbId", kb.getId());
        info.put("name", kb.getName());
        info.put("description", kb.getDescription());
        info.put("expired", kb.getInviteExpiresAt() != null && kb.getInviteExpiresAt().isBefore(LocalDateTime.now()));
        return info;
    }

    @Override
    public List<Map<String, Object>> getMembers(Long kbId) {
        return memberMapper.selectMembersWithName(kbId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long userId, Long kbId, Long targetUserId) {
        SharedKb kb = sharedKbMapper.selectById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在");
        boolean isOwner = kb.getOwnerId().equals(userId);
        if (!isOwner) {
            // 检查是否 admin
            SharedKbMember me = memberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                            .eq(SharedKbMember::getKbId, kbId)
                            .eq(SharedKbMember::getUserId, userId));
            if (me == null || !"admin".equals(me.getRole()))
                throw new IllegalArgumentException("无权操作");
        }
        if (kb.getOwnerId().equals(targetUserId))
            throw new IllegalArgumentException("不能踢出创建者");
        memberMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                .eq(SharedKbMember::getKbId, kbId)
                .eq(SharedKbMember::getUserId, targetUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeRole(Long userId, Long kbId, Long targetUserId, String role) {
        SharedKb kb = sharedKbMapper.selectById(kbId);
        checkOwner(kb, userId);
        SharedKbMember target = memberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                        .eq(SharedKbMember::getKbId, kbId)
                        .eq(SharedKbMember::getUserId, targetUserId));
        if (target == null) throw new IllegalArgumentException("成员不存在");
        target.setRole(role);
        memberMapper.updateById(target);
    }

    private void checkOwner(SharedKb kb, Long userId) {
        if (kb == null || !kb.getOwnerId().equals(userId))
            throw new IllegalArgumentException("无权操作");
    }

    private void hasAccess(Long kbId, Long userId) {
        long count = memberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SharedKbMember>()
                        .eq(SharedKbMember::getKbId, kbId)
                        .eq(SharedKbMember::getUserId, userId));
        if (count == 0) throw new IllegalArgumentException("你不是该知识库成员");
    }
}
