package com.firedemo.demo.Service;


import com.firedemo.demo.Entity.DirectoryNode;
import com.firedemo.demo.Entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档服务接口
 *
 * @author 海克斯
 */
public interface DocumentService {

    /**
     * 上传文档
     *
     * @param userId 用户ID
     * @param file   文件
     * @param kbId   所属共享知识库ID（可选）
     * @return 文档ID
     */
    String uploadDocument(Long userId, MultipartFile file, Long kbId);

    /**
     * 根据ID获取文档
     *
     * @param docId 文档ID
     * @return 文档实体
     */
    Document getByDocId(String docId);

    /**
     * 获取用户的文档列表
     *
     * @param userId 用户ID
     * @return 文档列表
     */
    List<Document> getUserDocuments(Long userId);

    /**
     * 删除文档
     *
     * @param docId  文档ID
     * @param userId 用户ID（权限校验）
     * @return 是否成功
     */
    boolean deleteDocument(String docId, Long userId);

    /**
     * 更新文档状态
     *
     * @param docId     文档ID
     * @param status    状态
     * @param chunkCount 块数
     * @return 是否成功
     */
    boolean updateStatus(String docId, Integer status, Integer chunkCount);

    /**
     * 处理文档：读取内容、切割、生成向量并存储
     *
     * @param docId 文档ID
     */
    void processDocument(String docId);

    /**
     * 根据查询检索相关文档内容（全库共享）
     *
     * @param query  查询文本
     * @param topK   返回条数
     * @return 相关文档内容列表
     */
    List<String> searchRelevantContent(String query, int topK);

    // ==================== 目录树管理 ====================

    /**
     * 获取用户目录树（平铺列表，由调用方组装成树形）
     */
    List<DirectoryNode> getDirectoryTree(Long userId);

    /**
     * 获取共享知识库目录树
     */
    List<DirectoryNode> getDirectoryTreeByKbId(Long kbId);

    /**
     * 创建文件夹
     *
     * @param userId   用户ID
     * @param parentId 父节点ID，null 表示根级
     * @param label    文件夹名称
     * @return 新节点ID
     */
    Long createFolder(Long userId, Long parentId, String label, Long kbId);

    /**
     * 重命名节点
     */
    void renameNode(Long userId, Long nodeId, String label);

    /**
     * 删除节点及其所有子节点
     */
    void deleteDirectoryNode(Long userId, Long nodeId);

    /**
     * 移动节点（拖拽排序/变更父节点）
     */
    void moveNode(Long userId, Long nodeId, Long targetParentId, Integer sortOrder);

    /**
     * 切换目录节点共享状态（递归应用到所有子节点）
     *
     * @return 切换后的共享状态
     */
    boolean toggleShare(Long userId, Long nodeId);

    /**
     * 获取其他用户共享的目录树（含分享者用户名）
     */
    List<java.util.Map<String, Object>> getSharedTree(Long userId);
}
