package com.firedemo.demo.Service;


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
     * @return 文档ID
     */
    String uploadDocument(Long userId, MultipartFile file);

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
}
