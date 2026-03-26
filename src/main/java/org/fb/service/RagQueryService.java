package org.fb.service;

import org.fb.bean.RagQueryResult;

/**
 * RAG查询服务接口
 */
public interface RagQueryService {

    /**
     * 执行RAG查询
     *
     * @param query 用户查询文本
     * @param maxResults 最大返回结果数
     * @param minScore 最低相似度分数阈值
     * @param generateAnswer 是否使用LLM生成答案
     * @return RAG查询结果
     */
    RagQueryResult query(String query, int maxResults, double minScore, boolean generateAnswer);

    /**
     * 执行RAG查询（默认参数）
     * 最大返回5条结果，最低分数0.5，使用LLM生成答案
     *
     * @param query 用户查询文本
     * @return RAG查询结果
     */
    RagQueryResult query(String query);

    /**
     * 仅执行向量相似度搜索
     *
     * @param query 用户查询文本
     * @param maxResults 最大返回结果数
     * @param minScore 最低相似度分数阈值
     * @return RAG查询结果（不包含LLM生成的答案）
     */
    RagQueryResult searchOnly(String query, int maxResults, double minScore);
}
