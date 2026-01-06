package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PersonalDataTools {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Tool(name = "search_personal_resume", value = "查询个人简历信息:根据用户输入的关键词{{keyword}}从MongoDB数据库中查询个人简历相关数据并返回")
    public String searchPersonalResume(@P(value = "关键词", required = true) String keyword) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("content").regex(keyword, "i"));
            
            List<Map> results = mongoTemplate.find(query, Map.class, "personal_data");
            
            if (results.isEmpty()) {
                return "未找到与关键词\"" + keyword + "\"相关的个人简历信息";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("找到").append(results.size()).append("条相关记录：\n");
            for (Map<String, Object> result : results) {
                sb.append("- ").append(result.get("content")).append("\n");
            }
            
            return sb.toString();
        } catch (Exception e) {
            return "查询个人简历信息时出错：" + e.getMessage();
        }
    }

    @Tool(name = "get_all_personal_info", value = "获取所有个人信息:从MongoDB数据库中获取所有存储的个人信息并返回")
    public String getAllPersonalInfo() {
        try {
            List<Map> results = mongoTemplate.findAll(Map.class, "personal_data");
            
            if (results.isEmpty()) {
                return "MongoDB数据库中暂无个人信息数据";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("共找到").append(results.size()).append("条个人信息记录：\n");
            for (Map<String, Object> result : results) {
                sb.append("- ").append(result.get("content")).append("\n");
            }
            
            return sb.toString();
        } catch (Exception e) {
            return "获取个人信息时出错：" + e.getMessage();
        }
    }
}
