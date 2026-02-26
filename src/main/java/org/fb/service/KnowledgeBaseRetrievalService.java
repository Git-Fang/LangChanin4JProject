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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 从Qdrant向量数据库检索（支持多路召回）
     */
    private String searchFromQdrant(String query) {
        try {
            // 【修复1】优化Query构建：从原问题中提取关键检索词
            String optimizedQuery = extractSearchKeywords(query);
            log.info("Qdrant检索 - 原始Query: {}", query);
            log.info("Qdrant检索 - 优化后Query: {}", optimizedQuery);
            
            // 【新增】多路召回策略：尝试多个检索词
            List<String> searchQueries = generateMultipleSearchQueries(query, optimizedQuery);
            log.info("Qdrant检索 - 多路召回查询词: {}", searchQueries);
            
            // 用于去重和收集结果
            Set<String> dedup = new HashSet<>();
            List<String> allResults = new ArrayList<>();
            double maxScore = 0.0;
            
            // 多路召回执行
            for (String sq : searchQueries) {
                log.info("Qdrant检索 - 执行查询: {}", sq);
                String result = commonTools.embeddingSearch(sq);
                
                if (result != null && !result.contains("查无相关数据") && !result.contains("未查询到")) {
                    // 解析相似度信息
                    if (result.contains("相似度score：")) {
                        Pattern scorePattern = Pattern.compile("相似度score：([0-9.]+)");
                        Matcher scoreMatcher = scorePattern.matcher(result);
                        while (scoreMatcher.find()) {
                            try {
                                double score = Double.parseDouble(scoreMatcher.group(1));
                                if (score > maxScore) {
                                    maxScore = score;
                                }
                            } catch (NumberFormatException e) {
                                // 忽略解析错误
                            }
                        }
                    }
                    
                    // 简单去重：将每行作为去重 key
                    String[] lines = result.split("\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !dedup.contains(trimmed)) {
                            dedup.add(trimmed);
                            allResults.add(trimmed);
                        }
                    }
                    log.info("Qdrant检索 - 查询 '{}' 找到结果，当前去重后: {} 条", sq, allResults.size());
                }
            }
            
            // 如果多路召回有结果
            if (!allResults.isEmpty()) {
                log.info("Qdrant多路召回完成，共 {} 条结果，最高相似度: {}", allResults.size(), maxScore);
                
                // 构建返回结果
                StringBuilder resultBuilder = new StringBuilder();
                resultBuilder.append("共找到").append(allResults.size()).append("条相关结果（多路召回）:\n\n");
                
                int count = 1;
                for (String line : allResults) {
                    if (line.contains("【相关数据") || line.startsWith("【")) {
                        resultBuilder.append(line).append("\n\n");
                    } else {
                        resultBuilder.append("【相关数据").append(count++).append("】\n");
                        resultBuilder.append(line).append("\n\n");
                    }
                }
                
                // 添加最高相似度信息
                if (maxScore > 0) {
                    resultBuilder.append("【最高相似度】").append(String.format("%.2f", maxScore)).append("\n");
                }
                
                return resultBuilder.toString();
            }
            
            // 如果多路召回都未找到结果，尝试原始Query作为fallback
            log.info("多路召回未命中，尝试原始Query检索...");
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
     * 【新增】生成多个检索查询词，实现多路召回
     * 针对不同类型的查询，生成多个可能匹配的检索词
     */
    private List<String> generateMultipleSearchQueries(String originalQuery, String optimizedQuery) {
        List<String> queries = new ArrayList<>();
        
        // 1. 首先添加优化后的查询词
        if (optimizedQuery != null && !optimizedQuery.isEmpty()) {
            queries.add(optimizedQuery);
        }
        
        // 2. 提取人名
        String[] namePatterns = {"方彪", "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十"};
        String extractedName = null;
        for (String name : namePatterns) {
            if (originalQuery.contains(name)) {
                extractedName = name;
                break;
            }
        }
        
        // 3. 如果有人名，生成多种组合查询
        if (extractedName != null) {
            queries.add(extractedName);  // 仅人名
            queries.add(extractedName + " 项目");  // 人名+项目
            queries.add(extractedName + " 工作");  // 人名+工作
            queries.add(extractedName + " 简历");  // 人名+简历
            queries.add(extractedName + " 项目经验");  // 人名+项目经验
            queries.add(extractedName + " 工作经历");  // 人名+工作经历
        }
        
        // 4. 添加核心关键词变体
        if (originalQuery.contains("项目") || originalQuery.contains("经验")) {
            queries.add("项目经验");
            queries.add("工作项目");
            queries.add("过往项目");
        }
        if (originalQuery.contains("工作") || originalQuery.contains("任职")) {
            queries.add("工作经历");
            queries.add("任职经历");
        }
        if (originalQuery.contains("简历") || originalQuery.contains("个人")) {
            queries.add("个人简历");
            queries.add("简历信息");
        }
        
        // 5. 去重
        Set<String> uniqueQueries = new LinkedHashSet<>(queries);
        
        return new ArrayList<>(uniqueQueries);
    }
    
    /**
     * 【修复1】从用户问题中提取关键检索词
     * 目的：解决query太长导致向量检索效果差的问题
     * 优化：更智能地提取人名、核心实体，构建多检索词策略
     */
    private String extractSearchKeywords(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        
        log.info("开始提取检索关键词，原Query: {}", query);
        
        StringBuilder keywords = new StringBuilder();
        
        // 1. 提取人名（扩展匹配模式）
        String[] namePatterns = {"方彪", "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十", 
                                  "刘一", "刘二", "刘三", "陈一", "陈二", "杨一", "杨二"};
        
        String extractedName = null;
        for (String name : namePatterns) {
            if (query.contains(name)) {
                extractedName = name;
                keywords.append(name);
                break;
            }
        }
        
        // 2. 提取核心实体（项目、技术、公司等）
        List<String> entities = new ArrayList<>();
        
        // 项目相关
        if (query.contains("项目") || query.contains("经验") || query.contains("经历")) {
            entities.add("项目经验");
            entities.add("项目经历");
        }
        // 工作相关
        if (query.contains("工作") || query.contains("任职") || query.contains("职位")) {
            entities.add("工作经历");
            entities.add("任职");
        }
        // 简历相关
        if (query.contains("简历") || query.contains("个人") || query.contains("介绍")) {
            entities.add("简历");
            entities.add("个人");
        }
        // 技能相关
        if (query.contains("技能") || query.contains("技术") || query.contains("能力")) {
            entities.add("技能");
            entities.add("技术");
        }
        
        // 3. 构建优化后的查询词
        if (extractedName != null) {
            // 优先使用"人名 + 实体"组合
            if (!entities.isEmpty()) {
                keywords.append(" ").append(entities.get(0));
            }
        } else {
            // 如果没有提取到人名，提取query中最有意义的中文词汇（2-4个字）
            Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");
            Matcher matcher = pattern.matcher(query);
            while (matcher.find()) {
                String word = matcher.group();
                // 跳过常见无意义词
                if (!word.contains("关于") && !word.contains("结合") && !word.contains("请") 
                    && !word.contains("需要") && !word.contains("什么") && !word.contains("哪些")
                    && !word.contains("总结") && !word.contains("介绍") && !word.contains("根据")) {
                    if (keywords.length() == 0) {
                        keywords.append(word);
                    }
                    break;
                }
            }
            
            // 添加相关后缀（针对不同类型的查询）
            if (!entities.isEmpty() && keywords.length() > 0) {
                keywords.append(" ").append(entities.get(0));
            } else if (query.contains("工作") || query.contains("经历")) {
                keywords.append(" 工作 经历");
            } else if (query.contains("项目") || query.contains("经验")) {
                keywords.append(" 项目 经验");
            } else if (query.contains("简历") || query.contains("个人")) {
                keywords.append(" 简历 个人");
            }
        }
        
        String result = keywords.toString().trim();
        
        // 4. 如果提取结果太短，使用原始查询
        if (result.length() < 2) {
            log.info("关键词提取结果为空，使用原始Query");
            return query;
        }
        
        log.info("关键词提取完成，优化后Query: {}", result);
        return result;
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
        
        // 【修复2】优化关键词构建 - 优先使用人名直接检索
        // 从MongoDB优先检索
        String mongoResult = searchFromMongoDB(name);
        if (mongoResult != null && !mongoResult.contains("未找到") && !mongoResult.contains("暂无")) {
            result.append("【MongoDB个人信息】\n");
            result.append(mongoResult).append("\n\n");
            log.info("MongoDB找到个人信息");
        } else {
            // 尝试带项目的组合查询
            mongoResult = searchFromMongoDB(name + " 项目");
            if (mongoResult != null && !mongoResult.contains("未找到") && !mongoResult.contains("暂无")) {
                result.append("【MongoDB个人信息】\n");
                result.append(mongoResult).append("\n\n");
                log.info("MongoDB找到个人信息(组合查询)");
            }
        }
        
        // 【修复3】从Qdrant检索 - 优先使用精简Query
        String optimizedQdrantQuery = name + " 项目 经验";
        String qdrantResult = searchFromQdrant(optimizedQdrantQuery);
        if (qdrantResult != null && !qdrantResult.contains("查无相关数据")) {
            result.append("【Qdrant向量库个人信息】\n");
            result.append(qdrantResult).append("\n\n");
            log.info("Qdrant找到个人信息");
        } else {
            // 尝试工作经历相关的查询
            log.info("项目经验Query未命中，尝试工作经历检索...");
            String workExperienceQuery = name + " 工作 经历";
            qdrantResult = searchFromQdrant(workExperienceQuery);
            if (qdrantResult != null && !qdrantResult.contains("查无相关数据")) {
                result.append("【Qdrant向量库个人信息】\n");
                result.append(qdrantResult).append("\n\n");
                log.info("Qdrant找到个人信息(工作经历)");
            } else {
                // 如果精简Query失败，尝试只用人名
                log.info("工作经历Query未命中，尝试仅用人名检索...");
                qdrantResult = searchFromQdrant(name);
                if (qdrantResult != null && !qdrantResult.contains("查无相关数据")) {
                    result.append("【Qdrant向量库个人信息】\n");
                    result.append(qdrantResult).append("\n\n");
                    log.info("Qdrant找到个人信息(仅人名)");
                }
            }
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
        
        // 【修复4】检查是否有任何结果 - 如果没有明确告知用户
        if (!result.toString().contains("找到") && !result.toString().contains("记录")) {
            result.append("【查询结果】\n");
            result.append("未在当前知识库中查询到与[").append(name).append("]相关的项目经验信息。\n");
            result.append("\n【可能原因】\n");
            result.append("1. 该人员的信息尚未导入知识库\n");
            result.append("2. 知识库中该人员信息命名方式与查询词不匹配\n");
            result.append("3. 向量数据库中该人员信息的embedding质量较低\n");
            result.append("\n【建议操作】\n");
            result.append("1. 确认该人员的相关信息已正确导入MongoDB或Qdrant\n");
            result.append("2. 检查MongoDB的personal_data集合是否有数据\n");
            result.append("3. 检查Qdrant向量数据库是否已导入相关文档\n");
            result.append("4. 可尝试使用网络搜索获取该人员更多信息\n");
        }
        
        log.info("========== 个人项目经验查询完成 ==========");
        return result.toString();
    }
}
