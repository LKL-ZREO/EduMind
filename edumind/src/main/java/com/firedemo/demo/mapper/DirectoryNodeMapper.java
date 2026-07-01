package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.DirectoryNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 目录节点 Mapper
 *
 * @author 海克斯
 */
@Mapper
public interface DirectoryNodeMapper extends BaseMapper<DirectoryNode> {

    @Select("SELECT * FROM directory_node WHERE user_id = #{userId} AND kb_id IS NULL ORDER BY sort_order, id")
    List<DirectoryNode> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM directory_node WHERE kb_id = #{kbId} ORDER BY sort_order, id")
    List<DirectoryNode> selectByKbId(@Param("kbId") Long kbId);

    @Select("SELECT * FROM directory_node WHERE parent_id = #{parentId} ORDER BY sort_order, id")
    List<DirectoryNode> selectByParentId(@Param("parentId") Long parentId);

    @Select("""
        WITH RECURSIVE sub_tree AS (
            SELECT * FROM directory_node WHERE id = #{nodeId}
            UNION ALL
            SELECT n.* FROM directory_node n
            INNER JOIN sub_tree st ON n.parent_id = st.id
        )
        SELECT * FROM sub_tree WHERE id != #{nodeId}
        ORDER BY sort_order, id
        """)
    List<DirectoryNode> selectDescendants(@Param("nodeId") Long nodeId);

    /** 查询其他用户共享的节点（含分享者用户名） */
    @Select("SELECT n.*, u.username AS shared_by_name FROM directory_node n " +
            "LEFT JOIN sys_user u ON n.user_id = u.id " +
            "WHERE n.is_shared = TRUE AND n.user_id != #{userId} " +
            "ORDER BY n.user_id, n.sort_order, n.id")
    List<Map<String, Object>> selectSharedByOthersWithName(@Param("userId") Long userId);

    /** 递归更新节点及其所有子节点的共享状态 */
    @Update("""
        WITH RECURSIVE sub_tree AS (
            SELECT id FROM directory_node WHERE id = #{nodeId}
            UNION ALL
            SELECT n.id FROM directory_node n
            INNER JOIN sub_tree st ON n.parent_id = st.id
        )
        UPDATE directory_node SET is_shared = #{shared}
        WHERE id IN (SELECT id FROM sub_tree)
        """)
    void updateSharedRecursive(@Param("nodeId") Long nodeId, @Param("shared") Boolean shared);

}
