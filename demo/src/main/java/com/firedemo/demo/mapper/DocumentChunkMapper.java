package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.rag.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档块 Mapper
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {
}
