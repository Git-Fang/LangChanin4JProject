package org.fb;


import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@MapperScan("mapper")
@EnableDiscoveryClient
public class RAGTranslationApplication {

    public static void main(String[] args) {
        // 配置Netty DNS解析器，解决Docker环境中的DNS SERVFAIL问题
        System.setProperty("io.netty.resolver.dns.defaultDnsServers", "8.8.8.8,114.114.114.114");
        System.setProperty("io.netty.resolver.dns.searchDomains", "localdomain");
        System.setProperty("io.netty.resolver.dns.ndots", "2");
        System.setProperty("io.netty.resolver.dns.timeout", "5000");
        
        SpringApplication.run(RAGTranslationApplication.class, args);
    }
}