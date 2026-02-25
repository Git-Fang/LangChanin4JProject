package org.fb.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.fb.service.impl.NL2SQLService;
import org.fb.tools.MongoDBTools;
import org.fb.tools.CommonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索服务
 * 按照 mysql -> mongodb -> qdrant 的顺序检索知识库数据
 * 用于回答"结合知识库中的相关信息总结..."等需要查询知识库的问题
 */
@Service
public class KnowledgeBaseRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRetrievalService.class);

    @Autowired
    private NL2SQLService nl2SQLService;

    @Autowired
    private MongoDBTools mongoDBTools;

    @Autowired
    private CommonTools commonTools;

    /**
     * 从知识库检索信息
     * 检索顺序：MySQL -> MongoDB -> Qdrant
     * @param query 用户查询关键词
     * @return 检索结果
     */
    @Tool(name = "knowledge_base_search", value = "知识库检索:根据用户输入的查询关键词{{query}}从知识库（MySQL->MongoDB->Qdrant顺序）检索相关信息并返回")
    public String searchKnowledgeBase(@P(value = "query", required = true) String query) {
        log.info("========== 知识库检索开始 ==========");
        log.info("查询关键词: {}", query);
        
        StringBuilder result = new StringBuilder();
        result.append("【知识库检索结果】\n\n");
        
        // 第一步：MySQL检索
        log.info("开始MySQL知识库检索...");
        String mysqlResult = searchFromMySQL(query);
        if (mysqlResult != null && !mysqlResult.isEmpty() && !mysqlResult.contains("查询结果为空") && !mysqlResult.contains("查询失败")) {
            result.append("【MySQL知识库检索结果】\n");
            result.append(mysqlResult).append("\n\n");
            log.info("MySQL检索到数据: {}", mysqlResult.substring(0, Math.min(100, mysqlResult.length())));
        } else {
            log.info("MySQL未检索到相关数据");
            result.append("【MySQL知识库】暂无相关数据\n\n");
        }
        
        // 第二步：MongoDB检索
        log.info("开始MongoDB知识库检索...");
        String mongoResult = searchFromMongoDB(query);
        if (mongoResult != null && !mongoResult.isEmpty() && !mongoResult.contains("未找到")) {
            result.append("【MongoDB知识库检索结果】\n");
            result.append(mongoResult).append("\n\n");
            log.info("MongoDB检索到数据: {}", mongoResult.substring(0, Math.min(100, mongoResult.length())));
        } else {
            log.info("MongoDB未检索到相关数据");
            result.append("【MongoDB知识库】暂无相关数据\n\n");
        }
        
        // 第三步：Qdrant向量数据库检索
        log.info("开始Qdrant向量数据库检索...");
        String qdrantResult = searchFromQdrant(query);
        if (qdrantResult != null && !qdrantResult.isEmpty() && !qdrantResult.contains("查无相关数据")) {
            result.append("【Qdrant向量知识库检索结果】\n");
            result.append(qdrantResult).append("\n\n");
            log.info("Qdrant检索到数据: {}", qdrantResult.substring(0, Math.min(100, qdrantResult.length())));
        } else {
            log.info("Qdrant未检索到相关数据");
            result.append("【Qdrant向量知识库】暂无相关数据\n\n");
        }
        
        log.info("========== 知识库检索完成 ==========");
        return result.toString();
    }

    /**
     * 从MySQL数据库检索
     */
    private String searchFromMySQL(String query) {
        try {
            // 尝试将自然语言转换为SQL查询
            log.info("MySQL检索 - 尝试执行自然语言查询: {}", query);
            List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(query);
            
            if (sqlResult == null || sqlResult.isEmpty()) {
                return "MySQL数据库中未找到相关数据";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("共找到").append(sqlResult.size()).append("条记录：\n");
            
            int count = 1;
            for (Map<String, Object> row : sqlResult) {
                result.append("记录").append(count++).append(": ");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    result.append(entry.getKey()).append("=").append(entry.getValue()).append("; ");
                }
                result.append("\n");
            }
            
            return result.toString();
        } catch (Exception e) {
            log.warn("MySQL检索失败: {}", e.getMessage());
            return "MySQL检索失败: " + e.getMessage();
        }
    }

    /**
     * 从MongoDB检索个人信息
     */
    private String searchFromMongoDB(String query) {
        try {
            // 使用MongoDBTools进行检索
            log.info("MongoDB检索 - 关键词: {}", query);
            String result = mongoDBTools.searchMongoPersonalData(query);
            
            if (result != null && !result.contains("未找到")) {
                return result;
            }
            
            // 尝试获取所有个人信息
            log.info("MongoDB检索 - 尝试获取所有个人信息...");
            String allResult = mongoDBTools.getAllMongoPersonalData();
            if (allResult != null && !allResult.contains("暂无")) {
                // 检查是否包含查询关键词
                if (allResult.toLowerCase().contains(query.toLowerCase())) {
                    return allResult;
                }
            }
            
            return "MongoDB中未找到与'" + query + "'相关的信息";
        } catch (Exception e) {
            log.warn("MongoDB检索失败: {}", e.getMessage());
            return "MongoDB检索失败: " + e.getMessage();
        }
    }

    /**
     * 从Qdrant向量数据库检索
     */
    private String searchFromQdrant(String query) {
        try {
            log.info("Qdrant检索 - 查询: {}", query);
            String result = commonTools.embeddingSearch(query);
            
            if (result != null && !result.contains("查无相关数据")) {
                return result;
            }
            
            return "Qdrant向量数据库中未找到与'" + query + "'相关的信息";
        } catch (Exception e) {
            log.warn("Qdrant检索失败: {}", e.getMessage());
            return "Qdrant检索失败: " + e.getMessage();
        }
    }

    /**
     * 便捷方法：专门用于检索个人信息（如"方彪的过往项目经历"）
     */
    @Tool(name = "search_personal_project_experience", value = "检索个人项目经验信息:根据人名{{name}}查询其在知识库中的项目经历、工作经验等信息")
    public String searchPersonalProjectExperience(@P(value = "name", required = true) String name) {
        log.info("========== 检索个人项目经验 ==========");
        log.info("查询人名: {}", name);
        
        StringBuilder result = new StringBuilder();
        result.append("【个人项目经验查询结果 - ").append(name).append("】\n\n");
        
        // 构建查询关键词
        String[] keywords = {name, name + "项目", name + "工作", name + "经历", name + "经验"};
        
        // 从MongoDB优先检索
        for (String keyword : keywords) {
            String mongoResult = searchFromMongoDB(keyword);
            if (mongoResult != null && !mongoResult.contains("未找到") && !mongoResult.contains("暂无")) {
                result.append("【MongoDB个人信息】\n");
                result.append(mongoResult).append("\n\n");
                log.info("MongoDB找到个人信息");
                break;
            }
        }
        
        // 从Qdrant检索
        String qdrantResult = searchFromQdrant(name + " 项目 经验");
        if (qdrantResult != null && !qdrantResult.contains("查无相关数据")) {
            result.append("【Qdrant向量库个人信息】\n");
            result.append(qdrantResult).append("\n\n");
            log.info("Qdrant找到个人信息");
        }
        
        // 如果都没有结果，尝试从MySQL检索
        if (!result.toString().contains("找到") && !result.toString().contains("记录")) {
            try {
                String mysqlResult = searchFromMySQL(name + " 项目 经验");
                if (mysqlResult != null && !mysqlResult.contains("未找到") && !mysqlResult.contains("失败")) {
                    result.append("【MySQL相关信息】\n");
                    result.append(mysqlResult).append("\n\n");
                    log.info("MySQL找到相关信息");
                }
            } catch (Exception e) {
                log.warn("MySQL检索失败: {}", e.getMessage());
            }
        }
        
        // 检查是否有任何结果
        if (!result.toString().contains("找到") && !result.toString().contains("记录")) {
            result.append("未在知识库中找到与[").append(name).append("]相关的项目经验信息。");
            result.append("\n建议：\n");
            result.append("1. 确认该人员的相关信息已录入知识库\n");
            result.append("2. 检查MongoDB的personal_data集合是否有数据\n");
            result.append("3. 检查Qdrant向量数据库是否已导入相关文档\n");
        }
        
        log.info("========== 个人项目经验查询完成 ==========");
        return result.toString();
    }
}
