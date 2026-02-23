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

@Service
public class NL2SQLService {
    private static final Logger log = LoggerFactory.getLogger(NL2SQLService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("naturalLanguageSQLAgentFallback")
    private NaturalLanguageSQLAgent languageSQLService;

    public List<Map<String, Object>> executeNaturalLanguageQuery(String naturalLanguage) {
        try {
            log.info("用户输入：{}", naturalLanguage);
            String sql = naturalLanguageToSQL(naturalLanguage);
            log.info("原始生成的SQL: {}", sql);

            // 清理 SQL（去除 markdown 代码块、反引号等）
            String cleanSql = cleanSQL(sql);
            log.info("清理后的SQL: {}", cleanSql);

            // 检查是否是"NOT_A_QUERY"标记（非查询请求）
            if ("NOT_A_QUERY".equals(cleanSql.trim())) {
                log.warn("AI判断该请求不是数据库查询请求: {}", naturalLanguage);
                throw new RuntimeException("该请求不是数据库查询请求，请使用通用对话模式处理");
            }

            // 验证SQL语句的合法性
            if (cleanSql == null || cleanSql.trim().isEmpty()) {
                throw new RuntimeException("生成的SQL语句为空");
            }

            // 再次验证必须是 SELECT 语句
            String upperSql = cleanSql.trim().toUpperCase();
            if (!upperSql.startsWith("SELECT")) {
                log.error("AI生成了非SELECT语句: {}", cleanSql);
                throw new RuntimeException("AI生成了非查询语句: " + cleanSql);
            }

            // 检查SQL是否包含危险操作（SELECT 语句中也不允许某些操作）
            if (upperSql.contains("DROP") || upperSql.contains("DELETE") || 
                upperSql.contains("UPDATE") || upperSql.contains("INSERT") ||
                upperSql.contains("ALTER") || upperSql.contains("TRUNCATE") ||
                upperSql.contains("CREATE ") || upperSql.contains("GRANT ") ||
                upperSql.contains("REVOKE ") || upperSql.contains("EXECUTE")) {
                log.error("SQL中包含危险操作: {}", cleanSql);
                throw new RuntimeException("不允许执行包含危险操作的SQL");
            }

            // 检查SQL中是否包含不合理的字符串字面量
            if (cleanSql.contains("'default'") || cleanSql.contains("'DEFAULT'") || 
                cleanSql.contains("'null'") || cleanSql.contains("'NULL'")) {
                log.warn("SQL中包含不合理的字符串字面量，尝试修正");
                throw new RuntimeException("生成的SQL包含不合理的字符串字面量");
            }

            // 验证SQL中的类型匹配
            validateSQLTypes(cleanSql);

            // 执行SQL查询 - 根据SQL类型选择正确的方法
            List<Map<String, Object>> result;
            if (upperSql.contains("COUNT(")) {
                // COUNT 查询返回单个值
                log.info("执行 COUNT 查询");
                Long count = jdbcTemplate.queryForObject(cleanSql, Long.class);
                result = List.of(Map.of("count", count));
            } else {
                // 普通 SELECT 查询
                log.info("执行普通 SELECT 查询");
                result = jdbcTemplate.queryForList(cleanSql);
            }
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
     * 清理 SQL 语句，去除 markdown 代码块、反引号等
     */
    private String cleanSQL(String sql) {
        if (sql == null) return null;
        
        String cleaned = sql.trim();
        
        // 去除 markdown 代码块标记
        if (cleaned.startsWith("```")) {
            // 去除 ```sql 或 ```
            cleaned = cleaned.replaceAll("^```\\w*\\n?", "");
            // 去除结尾的 ```
            cleaned = cleaned.replaceAll("\\n?```$", "");
        }
        
        // 去除单行反引号
        cleaned = cleaned.replaceAll("`([^`]+)`", "$1");
        
        // 去除 "sql" 关键字（如果有）
        cleaned = cleaned.replaceAll("(?i)^SQL:\\s*", "");
        
        return cleaned.trim();
    }

    
    public String naturalLanguageToSQL(String naturalLanguage) {
        // 获取数据库schema信息
        String schemaInfo = getDatabaseSchema();
        
        // 构建提示词
        String prompt = String.format("""
            你是一个MySQL专家。基于以下数据库结构，将自然语言转换为SQL查询语句。
            只返回SQL语句，不要任何解释。
            
            重要注意事项：
            1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE等修改数据的语句
            2. 确保SQL语句中的所有字段和表名都存在于提供的数据库结构中
            3. 对于统计查询（如查询表数量、数据数量等），使用正确的聚合函数和统计方法
            4. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值
            5. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容，不能是关键字
            6. 对于数值字段，不要使用引号，直接使用数字
            7. 对于日期字段，使用标准的日期格式，如'2024-01-01'
            8. 如果用户问题不是数据库查询请求（如询问概念、知识、方案、建议等），请返回"NOT_A_QUERY"作为标记
            9. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配
            10. 如果字段类型是BIGINT、INT等数值类型，不要使用字符串比较
            11. 查询表数量时，使用: SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
            12. 查询数据行数时，使用: SELECT COUNT(*) FROM 表名
            13. 特别强调：查询某个表有多少条数据时，必须使用 SELECT COUNT(*) FROM 表名 格式
            14. 绝对不要在 COUNT 查询中使用 WHERE 条件，除非用户明确指定筛选条件
            
            数据库结构：
            %s
            
            用户查询：%s
            
            SQL语句：""", schemaInfo, naturalLanguage);
        
        String sql = languageSQLService.convertToSQL(prompt);
        log.info("AI生成的SQL: {}", sql);
        return sql;
    }

    
    private String getDatabaseSchema() {
        StringBuilder schema = new StringBuilder();
        
        // 获取所有表信息
        List<Map<String, Object>> tables = jdbcTemplate.queryForList("""
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE()
            """);
        
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("TABLE_NAME");
            String tableComment = (String) table.get("TABLE_COMMENT");
            
            schema.append(String.format("表: %s (%s)\n", tableName, tableComment));
            
            // 获取表字段信息
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """, tableName);
            
            for (Map<String, Object> column : columns) {
                schema.append(String.format("  - %s: %s %s (%s)\n",
                    column.get("COLUMN_NAME"),
                    column.get("DATA_TYPE"),
                    "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL",
                    column.get("COLUMN_COMMENT")
                ));
            }
            schema.append("\n");
        }
        
        return schema.toString();
    }

    /**
     * 验证SQL中的类型匹配
     * @param sql SQL语句
     * @throws RuntimeException 如果发现类型不匹配
     */
    private void validateSQLTypes(String sql) {
        // 获取所有数值类型的字段
        List<Map<String, Object>> numericColumns = jdbcTemplate.queryForList("""
            SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND DATA_TYPE IN ('int', 'bigint', 'tinyint', 'smallint', 'decimal', 'float', 'double')
            """);
        
        // 检查SQL中是否对数值类型字段使用了字符串比较
        for (Map<String, Object> column : numericColumns) {
            String tableName = (String) column.get("TABLE_NAME");
            String columnName = (String) column.get("COLUMN_NAME");
            String dataType = (String) column.get("DATA_TYPE");
            
            // 构建可能的匹配模式
            String pattern1 = String.format("%s\\.%s\\s*=\\s*'[^']*'", tableName, columnName);
            String pattern2 = String.format("%s\\s*=\\s*'[^']*'", columnName);
            
            // 检查SQL中是否存在数值字段与字符串的比较
            if (sql.matches(".*" + pattern1 + ".*") || sql.matches(".*" + pattern2 + ".*")) {
                log.warn("SQL中对数值字段{}使用了字符串比较: {}", columnName, sql);
                throw new RuntimeException(String.format(
                    "SQL中对数值字段 %s (类型: %s) 使用了字符串比较，请检查SQL: %s", 
                    columnName, dataType, sql));
            }
        }
    }
}