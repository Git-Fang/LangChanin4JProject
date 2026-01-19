package org.fb.service;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DocumentService {

    public Integer saveFilesToLocal(MultipartFile[] files);

    public Integer saveAndEmbedding(MultipartFile[] files);

    public void addText(String document);

    public List<TextSegment> parseAndEmbedding(String filePath);

    public Map<String, Object> processBatchFiles(MultipartFile[] files, String operation);

    public List<Map<String, Object>> getFileOperationData(String sortField, String sortOrder, String operationType);
}
