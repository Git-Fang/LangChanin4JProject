package org.fb.config.toolsConfig;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommonTools {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;


    @Tool(name = "查询向量数据库信息", value="根据用户输入信息从pinecone向量数据库中查询后，并返回给用户")
    public String embeddingSearch(@P(value="用户问题", required = true) String question) {
        //1.1提问，并将问题转成向量数据
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        //1.2创建搜索请求对象
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1) //匹配最相似的一条记录
//                .minScore(0.8)
                .build();

        //2.根据搜索请求 searchRequest 在向量存储中进行相似度搜索
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        //searchResult.matches()：获取搜索结果中的匹配项列表。
        //.get(0)：从匹配项列表中获取第一个匹配项
        EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);

        //3.1获取匹配项的相似度得分
        System.out.println("答案相似度："+embeddingMatch.score()); // 0.8144288515898701
        //3.2返回文本结果
        System.out.println("答案："+embeddingMatch.embedded().text());

        return embeddingMatch.embedded().text();
    }



}
