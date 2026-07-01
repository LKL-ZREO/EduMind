package com.firedemo.demo.Service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 存储上传的文件
     * @param file 上传的文件
     * @return 文件存储路径
     */
    String storeFile(MultipartFile file);

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
}