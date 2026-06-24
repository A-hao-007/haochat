package com.ahao.haochat.common.user.controller;

import com.ahao.haochat.common.common.domain.vo.response.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 简单本地文件上传
 */
@RestController
@RequestMapping("/capi/file")
public class FileController {

    private static final String UPLOAD_DIR = "/var/www/uploads/";
    private static final String BASE_URL = "/uploads/";

    static {
        new File(UPLOAD_DIR).mkdirs();
    }

    @PostMapping("/upload")
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String name = UUID.randomUUID().toString().substring(0, 8) + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + name);
            Files.write(path, file.getBytes());
            Map<String, String> result = new HashMap<>();
            result.put("url", BASE_URL + name);
            return ApiResult.success(result);
        } catch (Exception e) {
            return ApiResult.fail(-1, "上传失败: " + e.getMessage());
        }
    }
}
