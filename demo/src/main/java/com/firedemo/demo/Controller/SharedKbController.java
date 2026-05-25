package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.SharedKb;
import com.firedemo.demo.Service.SharedKbService;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/shared-kb")
@RequiredArgsConstructor
public class SharedKbController {

    private final SharedKbService sharedKbService;
    private final JwtUtil jwtUtil;
    private static final String TOKEN_PREFIX = "Bearer ";

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        SharedKb kb = sharedKbService.create(userId, body.get("name"), body.get("description"));
        return ResponseEntity.ok(Map.of("id", kb.getId(), "message", "创建成功"));
    }

    @GetMapping("/my")
    public ResponseEntity<List<SharedKb>> getMy(HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(sharedKbService.getMy(userId));
    }

    @GetMapping("/joined")
    public ResponseEntity<List<SharedKb>> getJoined(HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(sharedKbService.getJoined(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        sharedKbService.update(userId, id, body.get("name"), body.get("description"));
        return ResponseEntity.ok(Map.of("message", "更新成功"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        sharedKbService.delete(userId, id);
        return ResponseEntity.ok(Map.of("message", "已解散"));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<Map<String, Object>> generateInvite(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        Integer maxUses = body != null ? (Integer) body.get("maxUses") : null;
        Integer expireHours = body != null ? (Integer) body.get("expireHours") : null;
        String token = sharedKbService.generateInvite(userId, id, maxUses, expireHours);
        String link = "/invite?token=" + token;
        return ResponseEntity.ok(Map.of("token", token, "link", link));
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, String>> join(@RequestParam String token, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        sharedKbService.joinByToken(userId, token);
        return ResponseEntity.ok(Map.of("message", "加入成功"));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<Map<String, Object>> inviteInfo(@PathVariable String token) {
        return ResponseEntity.ok(sharedKbService.getInviteInfo(token));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> members(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(sharedKbService.getMembers(id));
    }

    @DeleteMapping("/{id}/members/{targetId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long id, @PathVariable Long targetId, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        sharedKbService.removeMember(userId, id, targetId);
        return ResponseEntity.ok(Map.of("message", "已移除"));
    }

    @PutMapping("/{id}/members/{targetId}/role")
    public ResponseEntity<Map<String, String>> changeRole(
            @PathVariable Long id, @PathVariable Long targetId,
            @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getUserId(req);
        if (userId == null) return ResponseEntity.status(401).build();
        sharedKbService.changeRole(userId, id, targetId, body.get("role"));
        return ResponseEntity.ok(Map.of("message", "角色已更新"));
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith(TOKEN_PREFIX)) return null;
        try { return jwtUtil.getUserIdFromToken(auth.substring(TOKEN_PREFIX.length())); }
        catch (Exception e) { return null; }
    }
}
