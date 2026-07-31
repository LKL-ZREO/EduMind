package com.firedemo.edumind.platform.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 */
public interface FileStorage {

    /**
     * 存储上传的文件
     * @param file 上传的文件
     * @return 文件存储路径
     */
    String storeFile(MultipartFile file);

    /** Store binary content at a caller-controlled key and return that key. */
    String storeBytes(byte[] content, String storageKey, String contentType);

    /**
     * 删除文件
     * @param filePath 文件路径
     */
    void deleteFile(String filePath);

    /**
     * 获取文件内容（文本文件）
     * @param filePath 文件路径
     * @return 文件内容
     */
    String readFileContent(String filePath);

    /** Read raw file bytes without document parsing. */
    byte[] readFileBytes(String filePath);
}
