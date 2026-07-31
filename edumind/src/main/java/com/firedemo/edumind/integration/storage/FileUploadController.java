package com.firedemo.edumind.integration.storage;

import com.firedemo.edumind.platform.storage.FileStorage;
import com.firedemo.edumind.platform.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorage fileStorageService;

    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
               count = 10, interval = 60, timeUnit = RateLimit.TimeUnit.SECONDS)
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file) {

        String filePath = fileStorageService.storeFile(file);

        Map<String, String> result = new HashMap<>();
        result.put("filePath", filePath);
        result.put("fileName", file.getOriginalFilename());

        return ResponseEntity.ok(result);
    }
}
