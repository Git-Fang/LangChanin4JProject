package org.fb.service;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    public Integer saveFilesToLocal(MultipartFile[] files);

    public void addText(String document);

    public List<TextSegment> parseAndEmbedding(String filePath);
}
