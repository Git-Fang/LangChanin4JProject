package org.fb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DocSummarizer Application
 * 文档摘要总结服务 - 基于LangChain4j的智能文档摘要生成
 */
@SpringBootApplication
public class DocSummarizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocSummarizerApplication.class, args);
    }
}
