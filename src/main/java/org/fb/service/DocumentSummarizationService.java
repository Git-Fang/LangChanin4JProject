package org.fb.service;

import org.fb.bean.SummaryChunk;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档概要摘要服务接口
 */
public interface DocumentSummarizationService {

    /**
     * 对上传的文档进行概要摘要处理
     *
     * @param file 上传的文档文件（支持PDF、MD、Word等）
     * @return 切分摘要结果列表
     */
    List<SummaryChunk> summarizeDocument(MultipartFile file);

    /**
     * 对已解析的文档文本进行概要摘要处理
     *
     * @param documentText 文档文本内容
     * @param articleTitle 文章标题
     * @return 切分摘要结果列表
     */
    List<SummaryChunk> summarizeText(String documentText, String articleTitle);

    /**
     * 对已解析的文档文本进行概要摘要处理
     *
     * @param documentText 文档文本内容
     * @param articleTitle 文章标题
     * @param saveToQdrant 是否保存到Qdrant向量数据库
     * @return 切分摘要结果列表
     */
    List<SummaryChunk> summarizeText(String documentText, String articleTitle, boolean saveToQdrant);

    /**
     * 语义切分文本
     *
     * @param text 待切分文本
     * @return 切分后的文本片段列表
     */
    List<String> semanticChunking(String text);

    /**
     * 生成文本摘要
     *
     * @param chunk 文本片段
     * @return 150字以内的摘要
     */
    String generateSummary(String chunk);
}
