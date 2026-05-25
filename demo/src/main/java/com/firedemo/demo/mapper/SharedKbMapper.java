package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.SharedKb;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SharedKbMapper extends BaseMapper<SharedKb> {

    @Select("SELECT k.* FROM shared_kb k JOIN shared_kb_member m ON k.id = m.kb_id WHERE m.user_id = #{userId}")
    List<SharedKb> selectJoined(@Param("userId") Long userId);

    @Select("SELECT * FROM shared_kb WHERE invite_token = #{token}")
    SharedKb selectByInviteToken(@Param("token") String token);
}
