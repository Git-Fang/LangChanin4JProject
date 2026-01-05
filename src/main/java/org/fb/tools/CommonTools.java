package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommonTools {
    private static final Logger log = LoggerFactory.getLogger(CommonTools.class);

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;


    @Tool(name = "embedding_search", value="查询qdrant向量数据信息:根据传入数据{{question}}从qdrant向量数据库中查询并返回")
    public String embeddingSearch(@P(value="question", required = true) String question) {
        log.info("开始向量化。传入数据：{}", question);

        EmbeddingSearchResult<TextSegment> searchResult = getMatchWords(question);

        //3.获取匹配项的相似度得分与答案
        EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);
        log.info("答案相似度score：{}; 结果为：{}",embeddingMatch.score(),embeddingMatch.embedded().text());

        return embeddingMatch.embedded().text();
    }



    public EmbeddingSearchResult<TextSegment> getMatchWords(String question) {
        //1.1提问，并将问题转成向量数据
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        //1.2创建搜索请求对象
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1) //匹配最相似的一条记录
//                .minScore(0.8)
                .build();

        //2.根据搜索请求 searchRequest 在向量存储中进行相似度搜索
        return embeddingStore.search(searchRequest);
    }


}
