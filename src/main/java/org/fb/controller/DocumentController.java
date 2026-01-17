package org.fb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.segment.TextSegment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import mapper.FileOperationMapper;
import org.fb.bean.FileOperation;
import org.fb.service.impl.DocumentImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ragTranslation/doc")
@Tag(name = "RAG文档嵌入")
public class DocumentController {
    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentImpl documentImpl;

    @Autowired
    private FileOperationMapper fileOperationMapper;

    @PostMapping("/uploadAndEmbeddingMultipleDocuments")
    @Operation(summary = "1-上传多个文档并向量存储")
    public String uploadAndEmbeddingMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        Integer sucessCount = documentImpl.saveAndEmbedding(files);
        log.info("成功上传并嵌入{}个文件", sucessCount);

        return "操作成功个数： " + sucessCount + "/" + files.length;
    }

    @PostMapping(value = "/rag01/add")
    @Operation(summary = "2-读取本地文档解析至向量数据库")
    public String add(@RequestParam("filePath") String filePath) throws IOException {
        List<TextSegment> segments = null;
        try {
            segments = documentImpl.parseAndEmbedding(filePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "Inserted " + segments.size() + " chunks into Qdrant";
    }


    @PostMapping("/embeddingFile")
    @Operation(summary = "3-直接添加文本数据")
    public String embeddingFile(@RequestParam("content") String content) {

        try {
            documentImpl.addText(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "Content embedded and stored successfully";
    }


    @PostMapping("/uploadMultipleDocuments")
    @Operation(summary = "上传多个文档到本地")
    public Integer uploadMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        return documentImpl.saveFilesToLocal(files);
    }

    @GetMapping("/history")
    @Operation(summary = "获取上传文档历史记录")
    public List<Map<String, Object>> getDocumentHistory() {
        List<FileOperation> records = fileOperationMapper.selectList(
            new LambdaQueryWrapper<FileOperation>()
                .orderByDesc(FileOperation::getOperationTime)
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return records.stream().map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("fileName", record.getFileName());
            map.put("fileType", record.getFileType());
            map.put("operationType", record.getOperationType());
            map.put("operationTime", record.getOperationTime() != null ?
                record.getOperationTime().format(formatter) : null);
            map.put("status", record.getStatus());
            map.put("finishedTime", record.getFinishedTime() != null ?
                record.getFinishedTime().format(formatter) : null);
            return map;
        }).collect(Collectors.toList());
    }
}
