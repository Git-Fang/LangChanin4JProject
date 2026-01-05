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

            // 执行SQL查询
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            log.info("查询结果: {}", result);

            return result;
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
            你是一个MySQL专家。基于以下数据库结构，将自然语言转换为SQL语句。
            只返回SQL语句，不要任何解释。
            
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
    

}