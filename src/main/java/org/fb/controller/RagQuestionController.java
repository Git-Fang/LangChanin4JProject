package org.fb.controller;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * RAG问答控制器
 * 提供基于知识库的问答功能，支持检索内容展示和匹配度显示
 */
@RestController
@RequestMapping("/ragTranslation/qa")
@Tag(name = "RAG问答", description = "基于知识库的问答服务")
public class RagQuestionController {
    private static final Logger log = LoggerFactory.getLogger(RagQuestionController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("qdrantEmbeddingStore")
    private EmbeddingStore<TextSegment> embeddingStore;

    @Operation(summary = "RAG问答", description = "根据用户问题检索知识库并生成回答，同时返回检索内容和匹配度")
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        String question = request.get("question");
        if (question == null || question.trim().isEmpty()) {
            response.put("code", 400);
            response.put("message", "问题不能为空");
            return response;
        }

        try {
            log.info("========== RAG问答请求 ==========");
            log.info("用户问题: {}", question);

            // 1. 检索知识库
            List<Map<String, Object>> retrievalResults = searchKnowledgeBase(question);
            
            // 2. 构建Prompt
            String prompt = buildPrompt(question, retrievalResults);
            
            // 3. 调用LLM生成回答
            String aiAnswer = chatService.chat(System.currentTimeMillis(), prompt);
            
            // 4. 构建返回结果
            response.put("code", 200);
            response.put("message", "success");
            
            Map<String, Object> data = new HashMap<>();
            data.put("question", question);
            data.put("answer", aiAnswer);
            data.put("retrievalResults", retrievalResults);
            data.put("retrievalCount", retrievalResults.size());
            
            response.put("data", data);
            
            log.info("========== RAG问答完成 ==========");
            log.info("检索到 {} 条相关内容", retrievalResults.size());
            
        } catch (Exception e) {
            log.error("RAG问答失败: {}", e.getMessage(), e);
            response.put("code", 500);
            response.put("message", "问答失败: " + e.getMessage());
        }
        
        return response;
    }

    @Operation(summary = "仅检索", description = "仅检索知识库，不生成回答")
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        String question = request.get("question");
        if (question == null || question.trim().isEmpty()) {
            response.put("code", 400);
            response.put("message", "问题不能为空");
            return response;
        }

        try {
            List<Map<String, Object>> retrievalResults = searchKnowledgeBase(question);
            
            response.put("code", 200);
            response.put("message", "success");
            
            Map<String, Object> data = new HashMap<>();
            data.put("question", question);
            data.put("retrievalResults", retrievalResults);
            data.put("retrievalCount", retrievalResults.size());
            
            response.put("data", data);
            
        } catch (Exception e) {
            log.error("检索失败: {}", e.getMessage(), e);
            response.put("code", 500);
            response.put("message", "检索失败: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * 检索知识库
     */
    private List<Map<String, Object>> searchKnowledgeBase(String question) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            // 向量化查询
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            
            // 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(10)  // 最多返回10条
                    .minScore(0.5)  // 最低相似度阈值
                    .build();
            
            // 执行搜索
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            
            // 处理结果
            for (int i = 0; i < searchResult.matches().size(); i++) {
                EmbeddingMatch<TextSegment> match = searchResult.matches().get(i);
                
                Map<String, Object> item = new HashMap<>();
                item.put("index", i + 1);
                item.put("content", match.embedded().text());
                item.put("score", Math.round(match.score() * 100.0) / 100.0);  // 保留两位小数
                item.put("scorePercent", Math.round(match.score() * 100.0));  // 百分比形式
                
                // 获取metadata - 简化处理，不依赖具体API
                Map<String, String> metadata = new HashMap<>();
                metadata.put("hasMetadata", "true");
                item.put("metadata", metadata);
                
                results.add(item);
            }
            
            log.info("检索完成，共找到 {} 条相关内容", results.size());
            
        } catch (Exception e) {
            log.error("检索知识库失败: {}", e.getMessage(), e);
        }
        
        return results;
    }

    /**
     * 构建Prompt
     */
    private String buildPrompt(String question, List<Map<String, Object>> retrievalResults) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个专业的问答助手。请根据以下检索到的相关内容回答用户的问题。\n\n");
        
        // 添加检索内容
        if (!retrievalResults.isEmpty()) {
            prompt.append("【检索到的相关内容】\n");
            prompt.append("--------------------------------------------------\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                Map<String, Object> item = retrievalResults.get(i);
                prompt.append("【相关内容").append(i + 1).append("】(相似度: ")
                      .append(item.get("scorePercent")).append("%)\n");
                prompt.append(item.get("content")).append("\n\n");
            }
            prompt.append("--------------------------------------------------\n\n");
        } else {
            prompt.append("【注意】知识库中没有找到与问题直接相关的内容，请基于你的知识回答。\n\n");
        }
        
        // 添加问题
        prompt.append("【用户问题】\n");
        prompt.append(question).append("\n\n");
        
        // 添加回答要求
        prompt.append("【回答要求】\n");
        prompt.append("1. 请根据检索到的相关内容进行回答\n");
        prompt.append("2. 如果检索内容不相关或不足以回答问题，请如实说明\n");
        prompt.append("3. 回答要简洁、准确\n");
        prompt.append("4. 如果涉及到具体数据或事实，请优先参考检索内容\n\n");
        
        prompt.append("【回答】");
        
        return prompt.toString();
    }
}
