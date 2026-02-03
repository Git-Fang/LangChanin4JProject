package org.fb.service.impl;

import org.fb.service.assistant.NaturalLanguageSQLAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class NL2SQLService {
    private static final Logger log = LoggerFactory.getLogger(NL2SQLService.class);

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
            throw new RuntimeException("查询失败: " + e.getMessage());
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
        // 获取数据库schema信息（智能筛选相关表）
        String schemaInfo = getDatabaseSchema(naturalLanguage);

        // 构建提示词
        String prompt = String.format(
            "你是一个MySQL专家。基于以下数据库结构，将自然语言转换为SQL查询语句。\n" +
            "只返回SQL语句，不要任何解释。\n\n" +
            "重要注意事项：\n" +
            "1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE等修改数据的语句\n" +
            "2. 确保SQL语句中的所有字段和表名都存在于提供的数据库结构中\n" +
            "3. 对于统计查询（如查询表数量、数据数量等），使用正确的聚合函数和统计方法\n" +
            "4. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值\n" +
            "5. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容，不能是关键字\n" +
            "6. 对于数值字段，不要使用引号，直接使用数字\n" +
            "7. 对于日期字段，使用标准的日期格式，如'2024-01-01'\n" +
            "8. 如果不确定如何转换，返回SELECT 1语句\n" +
            "9. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配\n" +
            "10. 如果字段类型是BIGINT、INT等数值类型，不要使用字符串比较\n" +
            "11. 查询表数量时，使用: SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()\n" +
            "12. 查询数据行数时，使用: SELECT COUNT(*) FROM 表名\n\n" +
            "数据库结构：\n" +
            "%s\n\n" +
            "用户查询：%s\n\n" +
            "SQL语句：", schemaInfo, naturalLanguage);

        return languageSQLService.convertToSQL(prompt);
    }

    /**
     * 根据用户查询智能筛选相关的表结构
     * @param naturalLanguage 用户查询
     * @return 相关的表结构信息
     */
    private String getDatabaseSchema(String naturalLanguage) {
        StringBuilder schema = new StringBuilder();
        
        // 定义业务关键词与表名的映射关系
        java.util.Map<String, java.util.List<String>> keywordToTables = new java.util.HashMap<>();
        keywordToTables.put("预约", java.util.Arrays.asList("appointment"));
        keywordToTables.put("医生", java.util.Arrays.asList("appointment"));
        keywordToTables.put("患者", java.util.Arrays.asList("appointment"));
        keywordToTables.put("病人", java.util.Arrays.asList("appointment"));
        keywordToTables.put("挂号", java.util.Arrays.asList("appointment"));
        keywordToTables.put("用户", java.util.Arrays.asList("users", "user"));
        keywordToTables.put("订单", java.util.Arrays.asList("orders", "order"));
        keywordToTables.put("产品", java.util.Arrays.asList("products", "product"));
        
        // 分析用户查询，提取关键词
        String lowerQuery = naturalLanguage.toLowerCase();
        java.util.Set<String> relevantTables = new java.util.HashSet<>();
        
        // 默认包含appointment表（最常用的业务表）
        relevantTables.add("appointment");
        
        for (java.util.Map.Entry<String, java.util.List<String>> entry : keywordToTables.entrySet()) {
            if (lowerQuery.contains(entry.getKey().toLowerCase())) {
                relevantTables.addAll(entry.getValue());
            }
        }
        
        log.info("用户查询: {}，相关的表: {}", naturalLanguage, relevantTables);
        
        try {
            // 获取所有表信息
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()");
            
            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("TABLE_NAME");
                
                // 只处理相关的表
                if (!relevantTables.contains(tableName.toLowerCase())) {
                    continue;
                }
                
                String tableComment = (String) table.get("TABLE_COMMENT");
                schema.append(String.format("表: %s (%s)\n", tableName, tableComment != null ? tableComment : ""));

                // 获取表字段信息
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION", tableName);

                for (Map<String, Object> column : columns) {
                    schema.append(String.format("  - %s: %s %s (%s)\n",
                        column.get("COLUMN_NAME"),
                        column.get("DATA_TYPE"),
                        "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL",
                        column.get("COLUMN_COMMENT") != null ? column.get("COLUMN_COMMENT") : ""
                    ));
                }
                schema.append("\n");
            }
            
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
