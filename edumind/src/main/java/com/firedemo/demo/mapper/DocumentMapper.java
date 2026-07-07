package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.firedemo.demo.Entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档Mapper
 *
 * @author 海克斯
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 根据docId查询文档
     *
     * @param docId 文档ID
     * @return 文档实体
     */
    @Select("SELECT * FROM document WHERE doc_id = #{docId}")
    Document selectByDocId(@Param("docId") String docId);

    /**
     * 查询用户的所有文档
     *
     * @param userId 用户ID
     * @return 文档列表
     */
    @Select("SELECT * FROM document WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Document> selectByUserId(@Param("userId") Long userId);
}
