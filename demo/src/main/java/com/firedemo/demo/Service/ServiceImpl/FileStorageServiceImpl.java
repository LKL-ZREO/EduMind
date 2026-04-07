package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path targetPath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("文件保存成功: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("文件删除成功: {}", filePath);
        } catch (IOException e) {
            log.error("文件删除失败: {}", filePath, e);
        }
    }

    @Override
    public String readFileContent(String filePath) {
        try {
            Path path = Paths.get(filePath);

            // 检查文件类型
            String contentType = Files.probeContentType(path);

            if (contentType != null && contentType.startsWith("text")) {
                // 文本文件直接读取
                return Files.readString(path);
            } else {
                // 非文本文件返回路径，由调用方处理
                return "[文件类型: " + contentType + ", 路径: " + filePath + "]";
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            throw new RuntimeException("读取文件失败", e);
        }
    }
}