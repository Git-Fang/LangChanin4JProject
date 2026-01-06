package org.fb.service.impl;

import org.fb.service.assistant.NaturalLanguageSQLAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private NaturalLanguageSQLAgent languageSQLService;

    public List<Map<String, Object>> executeNaturalLanguageQuery(String naturalLanguage) {
        try {
            log.info("用户输入：{}", naturalLanguage);
            String sql = naturalLanguageToSQL(naturalLanguage);
            log.info("生成的SQL: {}", sql);

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
            3. 如果用户查询的内容在数据库中不存在，返回一个简单的SELECT 1语句
            4. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值
            5. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容，不能是关键字
            6. 对于数值字段，不要使用引号，直接使用数字
            7. 对于日期字段，使用标准的日期格式，如'2024-01-01'
            8. 如果不确定如何转换，返回SELECT 1语句
            9. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配
            10. 如果字段类型是BIGINT、INT等数值类型，不要使用字符串比较
            
            数据库结构：
            %s
            
            用户查询：%s
            
            SQL语句：""", schemaInfo, naturalLanguage);
        
        return languageSQLService.convertToSQL(prompt);
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