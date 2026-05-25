package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.SharedKbMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SharedKbMemberMapper extends BaseMapper<SharedKbMember> {

    @Select("SELECT m.*, u.username FROM shared_kb_member m " +
            "LEFT JOIN sys_user u ON m.user_id = u.id " +
            "WHERE m.kb_id = #{kbId} ORDER BY m.joined_at")
    List<java.util.Map<String, Object>> selectMembersWithName(@Param("kbId") Long kbId);
}
