package org.fb.service.impl;

import org.fb.service.assistant.NaturalLanguageSQLAgent;
import org.fb.util.AIAPIErrorHandler;
import org.fb.util.AIInputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 自然语言转SQL服务
 * 提供文本到SQL查询的转换功能，包含完善的错误处理和输入验证
 */
@Service
public class NL2SQLService {

    private static final Logger log = LoggerFactory.getLogger(NL2SQLService.class);

    // AI模型输入限制配置
    @Value("${ai.model.max-input-length:30000}")
    private int maxInputLength;

    // Schema最大长度配置
    @Value("${ai.sql.schema-max-length:15000}")
    private int schemaMaxLength;

    // 系统提示词模板长度估算
    private static final int SYSTEM_PROMPT_ESTIMATED_LENGTH = 800;

    // 重试配置
    private static final int MAX_RETRY_COUNT = 2;
    private static final long RETRY_DELAY_MS = 1000;

    // 需要真正查询数据库的关键词模式（必须有具体的查询动词和目标）
    private static final Pattern QUERY_DATABASE_PATTERN = Pattern.compile(
        "(查询|获取|查找|统计|列出|显示|有多少|多少个|哪些|有几个|显示所有|看看|查看|检索|搜索)\\s*.*",
        Pattern.CASE_INSENSITIVE
    );

    // 不需要查询数据库的关键词模式（数据库概念、设计、优化、原理等讨论）
    private static final Pattern DATABASE_CONCEPT_PATTERN = Pattern.compile(
        "(怎么设计|如何设计|如何优化|如何提高|优化|性能|索引|缓存|表结构|设计原则|原理|是什么|有什么作用|如何实现|怎么实现|如何创建|怎么创建|如何配置|怎么配置|如何工作|怎么工作)\\s*.*数据库.*|" +
        ".*数据库\\s*(怎么设计|如何设计|如何优化|如何提高|优化|性能|索引|缓存|表结构|设计原则|原理|是什么|有什么作用|如何实现|怎么实现|如何创建|怎么创建|如何配置|怎么配置|如何工作|怎么工作).*|" +
        "(SQL|sql)\\s*(怎么|如何|是什么|语法|语句|写法|编写|优化|性能)|" +
        "(mysql|MySQL|SqlServer|Oracle|PostgreSQL|数据库).*\\b(优化|调优|性能|原理|架构|设计|概念|学习|教程|方法|技巧)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // 表示一般性讨论的关键词（不是具体查询）
    private static final Pattern GENERAL_DISCUSSION_PATTERN = Pattern.compile(
        "^(怎么|如何|是什么|为什么|能不能|可以|是否|怎样|多少)\\b.*|" +
        "\\b(怎么|如何|是什么|为什么|能不能|可以|是否|怎样|多少)\\b.*",
        Pattern.CASE_INSENSITIVE
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("naturalLanguageSQLAgentFallback")
    private NaturalLanguageSQLAgent languageSQLService;

    /**
     * 判断是否需要真正查询数据库
     * @param naturalLanguage 用户输入
     * @return true-需要查询数据库，false-不需要
     */
    public boolean shouldQueryDatabase(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.trim().isEmpty()) {
            return false;
        }

        String trimmedInput = naturalLanguage.trim();
        log.info("预判断用户输入是否需要查询数据库: {}", trimmedInput);

        // 1. 检查是否明确要求查询数据库数据
        boolean hasQueryKeyword = QUERY_DATABASE_PATTERN.matcher(trimmedInput).find();

        // 2. 检查是否是数据库概念、设计、优化等讨论（这些不需要查询数据库）
        boolean isDatabaseConcept = DATABASE_CONCEPT_PATTERN.matcher(trimmedInput).find();

        // 3. 检查是否是一般性讨论问题
        boolean isGeneralQuestion = GENERAL_DISCUSSION_PATTERN.matcher(trimmedInput).find();

        // 判断逻辑：
        // - 如果包含明确的查询关键词（查询、获取、查找等），且不全是概念讨论，则需要查询
        // - 如果是概念讨论、性能优化、设计问题等，不需要查询数据库
        // - 如果是一般性问题（怎么、如何、是什么等），需要进一步判断

        if (isDatabaseConcept) {
            log.info("用户输入是数据库概念/设计/优化讨论，不需要查询数据库");
            return false;
        }

        if (hasQueryKeyword && !isGeneralQuestion) {
            log.info("用户输入包含明确的查询关键词，需要查询数据库");
            return true;
        }

        // 对于一般性问题，需要更详细的判断
        if (isGeneralQuestion) {
            // 检查是否询问具体数据
            boolean hasSpecificTarget = trimmedInput.matches(".*\\b(医生|患者|用户|病人|客户|订单|预约|产品|员工|部门|学生|课程|图书|商品)\\b.*");

            if (hasSpecificTarget) {
                // 检查是否有查询意图
                boolean hasIntentToQuery = trimmedInput.matches(".*\\b(有多少|有多少个|哪些|有几个|列出|查询|查看|显示)\\b.*");
                if (hasIntentToQuery) {
                    log.info("用户输入是关于具体实体的查询问题，需要查询数据库");
                    return true;
                }
            }

            // 询问数据库本身的问题
            if (trimmedInput.toLowerCase().contains("database") ||
                trimmedInput.toLowerCase().contains("数据库") ||
                trimmedInput.toLowerCase().contains("mysql") ||
                trimmedInput.toLowerCase().contains("sql")) {
                log.info("用户输入是关于数据库本身的问题，不需要查询数据库");
                return false;
            }

            // 其他一般性问题，不查询数据库
            log.info("用户输入是一般性问题，不需要查询数据库");
            return false;
        }

        // 默认不查询，使用general处理
        log.info("默认不查询数据库");
        return false;
    }

    public List<Map<String, Object>> executeNaturalLanguageQuery(String naturalLanguage) {
        try {
            // 预判断是否需要查询数据库
            if (!shouldQueryDatabase(naturalLanguage)) {
                log.info("预判断结果：不需要查询数据库，返回null触发general处理");
                return null;
            }

            log.info("用户输入：{}", naturalLanguage);

            // 调用增强的自然语言转SQL方法
            String sql = naturalLanguageToSQL(naturalLanguage);
            log.info("原始AI生成的SQL: [{}]", sql);

            // 清理SQL语句，移除markdown代码块标记
            sql = cleanSQL(sql);
            log.info("清理后的SQL: [{}]", sql);

            // 验证SQL语句的合法性
            if (sql == null || sql.trim().isEmpty()) {
                throw new RuntimeException("生成的SQL语句为空");
            }

            // 检查SQL是否包含危险操作
            String upperSql = sql.toUpperCase();
            if (upperSql.contains("DROP") || upperSql.contains("DELETE") ||
                upperSql.contains("UPDATE") || upperSql.contains("INSERT") ||
                upperSql.contains("ALTER") || upperSql.contains("TRUNCATE")) {
                throw new RuntimeException("不允许执行修改数据的SQL操作");
            }

            // 检查SQL中是否包含不合理的字符串字面量（如'default'、'null'等）
            if (sql.contains("'default'") || sql.contains("'DEFAULT'") ||
                sql.contains("'null'") || sql.contains("'NULL'")) {
                log.warn("SQL中包含不合理的字符串字面量，尝试重新生成");
                throw new RuntimeException("生成的SQL包含不合理的字符串字面量，请重新表述查询需求");
            }

            // 验证SQL中的类型匹配
            validateSQLTypes(sql);

            // 执行SQL查询
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            log.info("查询结果: {}", result);

            return result;
        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            log.error("执行自然语言查询失败", e);
            // 尝试解析AI错误
            AIAPIErrorHandler.AIErrorResult errorResult = AIAPIErrorHandler.parseError(e.getMessage());
            String userMessage = AIAPIErrorHandler.generateUserFriendlyMessage(errorResult);
            throw new RuntimeException(userMessage);
        }
    }

    /**
     * 清理AI生成的SQL语句，移除markdown代码块标记
     * @param rawSQL 原始SQL字符串
     * @return 清理后的SQL
     */
    private String cleanSQL(String rawSQL) {
        if (rawSQL == null || rawSQL.trim().isEmpty()) {
            return rawSQL;
        }

        String cleaned = rawSQL.trim();

        // 移除 ```sql 和 ``` 标记
        // 匹配 ```sql 或 ``` 在行首的情况
        cleaned = cleaned.replaceAll("(?m)^\\s*```sql\\s*", "");
        cleaned = cleaned.replaceAll("(?m)^\\s*```\\s*", "");

        // 匹配 ```sql 或 ``` 在行尾的情况
        cleaned = cleaned.replaceAll("(?m)\\s*```\\s*$", "");

        // 如果还有残留的 ```sql 标记（可能在中间），尝试移除
        cleaned = cleaned.replaceAll("```sql", "");
        cleaned = cleaned.replaceAll("```", "");

        // 清理首尾空白和换行
        cleaned = cleaned.trim();

        // 确保只有一条SQL语句（取第一条SELECT语句）
        String[] lines = cleaned.split(";");
        if (lines.length > 0) {
            cleaned = lines[0].trim();
        }

        log.debug("清理后的SQL: {}", cleaned);
        return cleaned;
    }

    public String naturalLanguageToSQL(String naturalLanguage) {
        // 1. 验证用户输入
        if (naturalLanguage == null || naturalLanguage.trim().isEmpty()) {
            throw new RuntimeException("用户输入为空，无法生成SQL");
        }

        String trimmedInput = naturalLanguage.trim();
        log.info("开始处理自然语言转SQL，用户输入长度: {}", trimmedInput.length());

        // 2. 获取数据库schema信息（智能筛选相关表）
        String schemaInfo = getDatabaseSchema(trimmedInput);
        log.info("原始schema信息长度: {}", schemaInfo.length());

        // 3. 构建提示词
        String prompt = buildSQLPrompt(schemaInfo, trimmedInput);

        // 4. 验证prompt长度
        AIInputValidator.ValidationResult validation = AIInputValidator.validate(prompt, maxInputLength);
        if (!validation.isValid()) {
            log.warn("Prompt长度验证失败: {}", validation.getMessage());

            // 如果提供了截断的输入，尝试使用
            if (validation.getTruncatedInput() != null) {
                log.info("尝试使用截断的prompt进行重试");
                prompt = AIInputValidator.truncate(prompt, maxInputLength);
            } else {
                // 无法处理，返回错误
                throw new RuntimeException("SQL生成失败：输入内容过长，请简化您的查询需求。");
            }
        }

        // 5. 调用AI服务生成SQL（带重试逻辑）
        return executeSQLConversionWithRetry(prompt, trimmedInput);
    }

    /**
     * 带重试机制的SQL转换
     * @param prompt 提示词
     * @param originalQuery 原始用户查询
     * @return 生成的SQL
     */
    private String executeSQLConversionWithRetry(String prompt, String originalQuery) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                log.info("第{}次调用AI服务生成SQL", retryCount + 1);
                return languageSQLService.convertToSQL(prompt);
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("SQL转换失败 (尝试 {}/{}): {}", retryCount, MAX_RETRY_COUNT + 1, e.getMessage());

                // 分析错误类型
                AIAPIErrorHandler.AIErrorResult errorResult = AIAPIErrorHandler.parseError(e.getMessage());

                // 如果是不可重试的错误，直接抛出
                if (!AIAPIErrorHandler.isRetryable(errorResult)) {
                    log.error("不可重试的错误: {}", errorResult.getErrorType());
                    throw new RuntimeException(AIAPIErrorHandler.generateUserFriendlyMessage(errorResult));
                }

                // 如果还有重试次数，等待后重试
                if (retryCount <= MAX_RETRY_COUNT) {
                    long delay = RETRY_DELAY_MS * retryCount; // 指数退避
                    log.info("等待{}ms后进行第{}次重试", delay, retryCount + 1);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("SQL转换被中断");
                    }
                }
            }
        }

        // 所有重试都失败了
        log.error("SQL转换在{}次尝试后仍然失败", MAX_RETRY_COUNT + 1);
        if (lastException != null) {
            AIAPIErrorHandler.AIErrorResult finalError = AIAPIErrorHandler.parseError(lastException.getMessage());
            throw new RuntimeException(AIAPIErrorHandler.generateUserFriendlyMessage(finalError));
        }
        throw new RuntimeException("SQL转换失败，请稍后重试");
    }

    /**
     * 构建SQL生成提示词
     * @param schemaInfo 数据库schema信息
     * @param userQuery 用户查询
     * @return 完整的提示词
     */
    private String buildSQLPrompt(String schemaInfo, String userQuery) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一个MySQL专家。基于以下数据库结构，将自然语言转换为SQL查询语句。\n");
        prompt.append("只返回SQL语句，不要任何解释。\n\n");
        prompt.append("重要注意事项：\n");
        prompt.append("1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE等修改数据的语句\n");
        prompt.append("2. 确保SQL语句中的所有字段和表名都存在于提供的数据库结构中\n");
        prompt.append("3. 对于统计查询（如查询表数量、数据数量等），使用正确的聚合函数和统计方法\n");
        prompt.append("4. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值\n");
        prompt.append("5. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容，不能是关键字\n");
        prompt.append("6. 对于数值字段，不要使用引号，直接使用数字\n");
        prompt.append("7. 对于日期字段，使用标准的日期格式，如'2024-01-01'\n");
        prompt.append("8. 如果不确定如何转换，返回SELECT 1语句\n");
        prompt.append("9. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配\n");
        prompt.append("10. 如果字段类型是BIGINT、INT等数值类型，不要使用字符串比较\n");
        prompt.append("11. 查询表数量时，使用: SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()\n");
        prompt.append("12. 查询数据行数时，使用: SELECT COUNT(*) FROM 表名\n\n");

        // 数据库结构
        prompt.append("数据库结构：\n");
        prompt.append(schemaInfo).append("\n\n");

        // 用户查询
        prompt.append("用户查询：").append(userQuery).append("\n\n");
        prompt.append("SQL语句：");

        return prompt.toString();
    }

    /**
     * 根据用户查询智能筛选相关的表结构
     * 实现智能schema选择，避免过多无关表信息导致prompt过长
     * @param naturalLanguage 用户查询
     * @return 相关的表结构信息
     */
    private String getDatabaseSchema(String naturalLanguage) {
        StringBuilder schema = new StringBuilder();

        // 定义业务关键词与表名的映射关系（支持更多业务场景）
        Map<String, List<String>> keywordToTables = new HashMap<>();
        keywordToTables.put("预约", Arrays.asList("appointment"));
        keywordToTables.put("医生", Arrays.asList("appointment", "doctor"));
        keywordToTables.put("患者", Arrays.asList("appointment", "patient"));
        keywordToTables.put("病人", Arrays.asList("appointment", "patient"));
        keywordToTables.put("挂号", Arrays.asList("appointment"));
        keywordToTables.put("用户", Arrays.asList("users", "user"));
        keywordToTables.put("订单", Arrays.asList("orders", "order"));
        keywordToTables.put("产品", Arrays.asList("products", "product"));
        keywordToTables.put("部门", Arrays.asList("department", "dept"));
        keywordToTables.put("员工", Arrays.asList("employee", "staff"));
        keywordToTables.put("课程", Arrays.asList("course", "class"));
        keywordToTables.put("学生", Arrays.asList("student"));
        keywordToTables.put("图书", Arrays.asList("book", "library"));
        keywordToTables.put("商品", Arrays.asList("product", "goods", "item"));

        // 分析用户查询，提取关键词
        String lowerQuery = naturalLanguage.toLowerCase();
        Set<String> relevantTables = new HashSet<>();

        // 默认包含appointment表（最常用的业务表）- 但限制为必需时才包含
        boolean hasExplicitTableReference = false;

        for (Map.Entry<String, List<String>> entry : keywordToTables.entrySet()) {
            if (lowerQuery.contains(entry.getKey().toLowerCase())) {
                relevantTables.addAll(entry.getValue());
                hasExplicitTableReference = true;
            }
        }

        log.info("用户查询: {}，初步识别的相关表: {}", naturalLanguage, relevantTables);

        try {
            // 获取所有表信息
            List<Map<String, Object>> allTables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()");

            // 如果没有明确匹配到表，只返回最常用的几个表
            if (relevantTables.isEmpty()) {
                log.info("未匹配到明确的业务表，返回appointment表的schema");
                relevantTables.add("appointment");
            }

            // 按相关性排序处理表
            List<Map<String, Object>> sortedTables = new ArrayList<>();
            Set<String> processedTables = new HashSet<>();

            // 首先处理明确相关的表
            for (String tableName : relevantTables) {
                for (Map<String, Object> table : allTables) {
                    String dbTableName = (String) table.get("TABLE_NAME");
                    if (dbTableName.equalsIgnoreCase(tableName) && !processedTables.contains(dbTableName)) {
                        sortedTables.add(table);
                        processedTables.add(dbTableName);
                    }
                }
            }

            // 添加主业务表appointment（如果没有被包含）
            if (!processedTables.contains("appointment")) {
                for (Map<String, Object> table : allTables) {
                    String tableName = (String) table.get("TABLE_NAME");
                    if ("appointment".equalsIgnoreCase(tableName) && !processedTables.contains(tableName)) {
                        sortedTables.add(table);
                        processedTables.add(tableName);
                        break;
                    }
                }
            }

            // 收集完整的schema信息
            StringBuilder fullSchema = new StringBuilder();
            for (Map<String, Object> table : sortedTables) {
                String tableName = (String) table.get("TABLE_NAME");
                String tableComment = (String) table.get("TABLE_COMMENT");
                fullSchema.append(String.format("表: %s (%s)\n", tableName, tableComment != null ? tableComment : ""));

                // 获取表字段信息
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION", tableName);

                for (Map<String, Object> column : columns) {
                    fullSchema.append(String.format("  - %s: %s %s (%s)\n",
                        column.get("COLUMN_NAME"),
                        column.get("DATA_TYPE"),
                        "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL",
                        column.get("COLUMN_COMMENT") != null ? column.get("COLUMN_COMMENT") : ""
                    ));
                }
                fullSchema.append("\n");
            }

            // 截断schema以符合长度限制
            int estimatedUserQueryLength = naturalLanguage.length();
            int availableSchemaLength = AIInputValidator.calculateMaxSchemaLength(
                SYSTEM_PROMPT_ESTIMATED_LENGTH,
                estimatedUserQueryLength,
                schemaMaxLength
            );

            if (fullSchema.length() > availableSchemaLength) {
                log.warn("Schema信息过长 ({} > {})，需要截断", fullSchema.length(), availableSchemaLength);
                schema.append(AIInputValidator.truncateSchema(fullSchema.toString(), availableSchemaLength));
            } else {
                schema.append(fullSchema);
            }

            log.info("最终schema信息长度: {}", schema.length());

            // 如果没有找到相关表，返回简要的表列表提示
            if (schema.length() == 0) {
                schema.append("表: appointment (预约信息表)\n");
                schema.append("  - id: bigint NOT NULL (主键ID)\n");
                schema.append("  - username: varchar NULL (患者姓名)\n");
                schema.append("  - id_card: varchar NULL (身份证号)\n");
                schema.append("  - department: varchar NULL (预约科室)\n");
                schema.append("  - date: varchar NULL (预约日期)\n");
                schema.append("  - time: varchar NULL (预约时间)\n");
                schema.append("  - doctor_name: varchar NULL (预约医生姓名)\n");
            }

        } catch (Exception e) {
            log.error("获取数据库schema失败，返回默认schema", e);
            // 返回默认的appointment表结构
            schema.append("表: appointment (预约信息表)\n");
            schema.append("  - id: bigint NOT NULL (主键ID)\n");
            schema.append("  - username: varchar NULL (患者姓名)\n");
            schema.append("  - id_card: varchar NULL (身份证号)\n");
            schema.append("  - department: varchar NULL (预约科室)\n");
            schema.append("  - date: varchar NULL (预约日期)\n");
            schema.append("  - time: varchar NULL (预约时间)\n");
            schema.append("  - doctor_name: varchar NULL (预约医生姓名)\n");
        }

        return schema.toString();
    }

    /**
     * 验证SQL中的类型匹配
     * @param sql SQL语句
     * @throws RuntimeException 如果发现类型不匹配
     */
    private void validateSQLTypes(String sql) {
        // 简化验证：只验证appointment表中的数值字段
        try {
            List<Map<String, Object>> numericColumns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appointment' " +
                "AND DATA_TYPE IN ('int', 'bigint', 'tinyint', 'smallint', 'decimal', 'float', 'double')");
            
            for (Map<String, Object> column : numericColumns) {
                String columnName = (String) column.get("COLUMN_NAME");
                String dataType = (String) column.get("DATA_TYPE");
                
                // 检查SQL中是否存在数值字段与字符串的比较
                if (sql.matches(".*appointment\\." + columnName + "\\s*=\\s*'[^']*'.*") ||
                    sql.matches(".*\\s+" + columnName + "\\s*=\\s*'[^']*'.*")) {
                    log.warn("SQL中对数值字段{}使用了字符串比较: {}", columnName, sql);
                    throw new RuntimeException(String.format(
                        "SQL中对数值字段 %s (类型: %s) 使用了字符串比较，请检查SQL: %s",
                        columnName, dataType, sql));
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            }
            log.warn("验证SQL类型时出错: {}", e.getMessage());
        }
    }
}

