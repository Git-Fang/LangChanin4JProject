package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.Result;
import org.fb.bean.RagQueryResult;
import org.fb.service.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RAG查询控制器
 * 提供向量搜索和智能问答接口
 */
@RestController
@RequestMapping("/api/rag")
@Tag(name = "RAG查询", description = "向量搜索和智能问答接口")
public class RagQueryController {
    private static final Logger log = LoggerFactory.getLogger(RagQueryController.class);

    @Autowired
    private RagQueryService ragQueryService;

    /**
     * RAG智能问答
     * 执行向量相似度搜索并使用LLM生成答案
     *
     * @param query 用户查询文本
     * @param maxResults 最大返回结果数（默认5）
     * @param minScore 最低相似度分数阈值（默认0.5）
     * @param generateAnswer 是否生成答案（默认true）
     * @return RAG查询结果
     */
    @GetMapping("/query")
    @Operation(summary = "RAG智能问答", 
               description = "基于向量相似度搜索的智能问答，返回相关文档切片和LLM生成的答案")
    public Result<Map<String, Object>> ragQuery(
            @Parameter(description = "查询问题", required = true)
            @RequestParam("query") String query,
            
            @Parameter(description = "最大返回结果数")
            @RequestParam(value = "maxResults", defaultValue = "5") int maxResults,
            
            @Parameter(description = "最低相似度分数阈值(0-1)")
            @RequestParam(value = "minScore", defaultValue = "0.5") double minScore,
            
            @Parameter(description = "是否使用LLM生成答案")
            @RequestParam(value = "generateAnswer", defaultValue = "true") boolean generateAnswer) {
        
        log.info("收到RAG查询请求: query={}, maxResults={}, minScore={}, generateAnswer={}", 
                query, maxResults, minScore, generateAnswer);
        
        try {
            // 校验参数
            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询问题不能为空");
            }
            
            if (maxResults < 1 || maxResults > 20) {
                maxResults = 5;
            }
            
            if (minScore < 0 || minScore > 1) {
                minScore = 0.5;
            }
            
            // 执行查询
            RagQueryResult result = ragQueryService.query(query.trim(), maxResults, minScore, generateAnswer);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("answer", result.getAnswer());
            response.put("relevantChunks", result.getRelevantChunks());
            response.put("matchedCount", result.getMatchedCount());
            response.put("queryTimeMs", result.getQueryTimeMs());
            response.put("generatedWithLlm", result.isGeneratedWithLlm());
            
            String message = "查询成功";
            if (result.getMatchedCount() > 0) {
                message += "，找到 " + result.getMatchedCount() + " 条相关结果";
            } else {
                message += "，未找到相关结果";
            }
            
            if (result.isGeneratedWithLlm() && result.getAnswer() != null) {
                message += "，已生成答案";
            }
            
            log.info("RAG查询完成: matchedCount={}, queryTimeMs={}", 
                    result.getMatchedCount(), result.getQueryTimeMs());
            
            return Result.success(message, response);
            
        } catch (Exception e) {
            log.error("RAG查询失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 仅执行向量搜索
     *
     * @param query 搜索查询
     * @param maxResults 最大返回结果数
     * @param minScore 最低相似度分数阈值
     * @return 搜索结果
     */
    @GetMapping("/search")
    @Operation(summary = "向量相似度搜索", 
               description = "仅执行向量相似度搜索，返回相关文档切片，不生成答案")
    public Result<Map<String, Object>> vectorSearch(
            @Parameter(description = "搜索查询", required = true)
            @RequestParam("query") String query,
            
            @Parameter(description = "最大返回结果数")
            @RequestParam(value = "maxResults", defaultValue = "5") int maxResults,
            
            @Parameter(description = "最低相似度分数阈值(0-1)")
            @RequestParam(value = "minScore", defaultValue = "0.5") double minScore) {
        
        log.info("收到向量搜索请求: query={}, maxResults={}, minScore={}", 
                query, maxResults, minScore);
        
        try {
            // 校验参数
            if (query == null || query.trim().isEmpty()) {
                return Result.error("搜索查询不能为空");
            }
            
            // 执行搜索
            RagQueryResult result = ragQueryService.searchOnly(query.trim(), maxResults, minScore);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("relevantChunks", result.getRelevantChunks());
            response.put("matchedCount", result.getMatchedCount());
            response.put("queryTimeMs", result.getQueryTimeMs());
            
            String message = result.getMatchedCount() > 0 
                    ? "搜索成功，找到 " + result.getMatchedCount() + " 条相关结果"
                    : "搜索成功，未找到相关结果";
            
            log.info("向量搜索完成: matchedCount={}, queryTimeMs={}", 
                    result.getMatchedCount(), result.getQueryTimeMs());
            
            return Result.success(message, response);
            
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }
}
