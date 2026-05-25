package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.SharedKb;
import java.util.List;
import java.util.Map;

public interface SharedKbService {

    /** 创建知识库 */
    SharedKb create(Long userId, String name, String description);

    /** 我创建的 */
    List<SharedKb> getMy(Long userId);

    /** 我加入的（不含自己创建的） */
    List<SharedKb> getJoined(Long userId);

    /** 编辑 */
    void update(Long userId, Long kbId, String name, String description);

    /** 解散（仅 owner） */
    void delete(Long userId, Long kbId);

    /** 生成邀请链接 */
    String generateInvite(Long userId, Long kbId, Integer maxUses, Integer expireHours);

    /** 通过邀请加入 */
    void joinByToken(Long userId, String token);

    /** 查询邀请信息 */
    Map<String, Object> getInviteInfo(String token);

    /** 成员列表 */
    List<Map<String, Object>> getMembers(Long kbId);

    /** 踢出成员 */
    void removeMember(Long userId, Long kbId, Long targetUserId);

    /** 改角色 */
    void changeRole(Long userId, Long kbId, Long targetUserId, String role);
}
