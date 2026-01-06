package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "个人数据管理")
@RestController
@RequestMapping("/xiaozhi/personal")
public class PersonalDataController {
    private static final Logger log = LoggerFactory.getLogger(PersonalDataController.class);

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "上传个人简历文件")
    public Map<String, Object> uploadResume(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            MultipartFile[] files = new MultipartFile[]{file};
            Integer count = documentService.saveAndEmbedding(files);
            
            result.put("success", true);
            result.put("message", "文件上传成功，已向量化存储到qdrant数据库");
            result.put("count", count);
            log.info("个人简历文件上传成功: {}", file.getOriginalFilename());
        } catch (Exception e) {
            log.error("上传个人简历文件失败", e);
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/upload/batch")
    @Operation(summary = "批量上传个人简历文件")
    public Map<String, Object> uploadResumeBatch(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer count = documentService.saveAndEmbedding(files);
            
            result.put("success", true);
            result.put("message", "批量上传成功，已向量化存储到qdrant数据库");
            result.put("count", count);
            log.info("批量上传个人简历文件成功，共{}个文件", count);
        } catch (Exception e) {
            log.error("批量上传个人简历文件失败", e);
            result.put("success", false);
            result.put("message", "批量上传失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/text")
    @Operation(summary = "添加个人简历文本信息")
    public Map<String, Object> addResumeText(@RequestBody String text) {
        Map<String, Object> result = new HashMap<>();
        try {
            documentService.addText(text);
            
            result.put("success", true);
            result.put("message", "文本信息添加成功，已向量化存储到qdrant数据库");
            log.info("个人简历文本信息添加成功");
        } catch (Exception e) {
            log.error("添加个人简历文本信息失败", e);
            result.put("success", false);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }
}
