package org.fb;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@MapperScan("mapper")
public class RAGTranslationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RAGTranslationApplication.class, args);
    }
}