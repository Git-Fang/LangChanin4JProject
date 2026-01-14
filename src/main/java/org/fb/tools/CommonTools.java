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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CommonTools {
    private static final Logger log = LoggerFactory.getLogger(CommonTools.class);

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;


    @Tool(name = "embedding_search", value="查询qdrant向量数据信息:根据传入数据{{question}}从qdrant向量数据库中查询并返回")
    public String embeddingSearch(@P(value="question", required = true) String question) {
        log.info("开始向量化查询。传入数据：{}", question);

        EmbeddingSearchResult<TextSegment> searchResult = getMatchWords(question);

        if (searchResult.matches().isEmpty()) {
            log.info("未查询到相关数据");
            return "数据库查无相关数据";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < searchResult.matches().size(); i++) {
            EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(i);
            log.info("答案{}相似度score：{}; 结果为：{}", i + 1, embeddingMatch.score(), embeddingMatch.embedded().text());
            result.append("【相关数据").append(i + 1).append("】\n");
            result.append(embeddingMatch.embedded().text()).append("\n\n");
        }

        return result.toString().trim();
    }



    public EmbeddingSearchResult<TextSegment> getMatchWords(String question) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.4)
                .build();

        return embeddingStore.search(searchRequest);
    }


}
