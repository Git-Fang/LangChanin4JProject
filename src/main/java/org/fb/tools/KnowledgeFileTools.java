package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knowledge目录文件检索工具
 * 用于检索项目根目录下的knowledge文件夹中的相关文件内容
 */
@Component
public class KnowledgeFileTools {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileTools.class);

    /**
     * Knowledge目录路径
     * Docker环境: /app/knowledge
     * 本地环境: 项目根目录/knowledge
     */
    @Value("${app.knowledge.path:/app/knowledge}")
    private String knowledgePath;

    /**
     * 最大读取文件大小（字节）- 5MB
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 排除的文件扩展名（不支持的文件格式）
     */
    private static final List<String> EXCLUDED_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".zip", ".rar", ".tar", ".gz", ".7z",
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico",
            ".mp3", ".mp4", ".wav", ".avi", ".mov",
            ".class", ".jar", ".exe", ".dll"
    );

    /**
     * 检索knowledge目录中的相关文件内容
     * @param keyword 用户输入的关键词
     * @return 匹配的文件内容
     */
    @Tool(name = "search_knowledge_file", value = "检索本地knowledge目录文件:根据用户输入的关键词{{keyword}}在knowledge目录下搜索相关文件并返回文件内容，支持.txt和.md文件检索")
    public String searchKnowledgeFile(@P(value = "关键词", required = true) String keyword) {
        log.info("========== 开始knowledge目录检索 ==========");
        log.info("检索路径: {}", knowledgePath);
        log.info("检索关键词: {}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("关键词为空，返回空结果");
            return "请提供检索关键词";
        }

        try {
            Path knowledgeDir = Paths.get(knowledgePath);
            
            // 检查目录是否存在
            if (!Files.exists(knowledgeDir)) {
                log.warn("Knowledge目录不存在: {}", knowledgePath);
                return "知识库目录不存在";
            }

            if (!Files.isDirectory(knowledgeDir)) {
                log.warn("Knowledge路径不是目录: {}", knowledgePath);
                return "知识库路径配置错误";
            }

            // 遍历目录搜索匹配的文件
            List<SearchResult> results = searchFiles(knowledgeDir, keyword.trim());

            if (results.isEmpty()) {
                log.info("未找到匹配的文件内容");
                return "未在知识库中找到与\"" + keyword + "\"相关的内容";
            }

            // 构建返回结果
            StringBuilder sb = new StringBuilder();
            sb.append("在知识库中找到").append(results.size()).append("个相关文件：\n\n");

            for (SearchResult result : results) {
                sb.append("【文件: ").append(result.relativePath).append("】\n");
                sb.append("匹配内容：\n");
                sb.append(result.matchedContent).append("\n\n");
            }

            log.info("知识库检索完成，找到{}个匹配结果", results.size());
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage(), e);
            return "知识库检索失败：" + e.getMessage();
        }
    }

    /**
     * 搜索目录下的文件
     */
    private List<SearchResult> searchFiles(Path dir, String keyword) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        // 转换为小写用于不区分大小写匹配
        String lowerKeyword = keyword.toLowerCase();
        
        // 编译正则表达式，支持中英文关键词
        Pattern pattern = Pattern.compile(lowerKeyword, Pattern.CASE_INSENSITIVE);

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // 跳过不支持的文件类型
                String fileName = file.getFileName().toString().toLowerCase();
                if (isExcludedExtension(fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                // 跳过过大的文件
                if (attrs.size() > MAX_FILE_SIZE) {
                    log.debug("跳过过大文件: {} ({} bytes)", file.getFileName(), attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                try {
                    // 读取文件内容
                    String content = Files.readString(file);
                    
                    // 检查是否包含关键词
                    if (containsKeyword(content, pattern)) {
                        // 提取匹配的内容片段
                        String matchedContent = extractMatchedContent(content, pattern, keyword);
                        
                        // 获取相对于knowledge目录的路径
                        Path relativePath = dir.relativize(file);
                        
                        results.add(new SearchResult(relativePath.toString(), matchedContent));
                        log.debug("找到匹配文件: {}", relativePath);
                    }
                } catch (Exception e) {
                    log.warn("读取文件失败: {}, 原因: {}", file.getFileName(), e.getMessage());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // 跳过隐藏目录
                if (dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    /**
     * 检查是否为排除的文件扩展名
     */
    private boolean isExcludedExtension(String fileName) {
        for (String ext : EXCLUDED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查内容是否包含关键词
     */
    private boolean containsKeyword(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find();
    }

    /**
     * 提取包含关键词的内容片段
     */
    private String extractMatchedContent(String content, Pattern pattern, String keyword) {
        StringBuilder sb = new StringBuilder();
        
        // 将内容按行分割
        String[] lines = content.split("\n");
        
        // 用于标记已添加的行，避免重复
        boolean[] added = new boolean[lines.length];
        
        Matcher matcher = pattern.matcher(content);
        
        // 收集所有匹配的行号
        List<Integer> matchedLineNumbers = new ArrayList<>();
        int lineStart = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineEnd = lineStart + line.length();
            
            // 检查这行是否包含关键词
            Matcher lineMatcher = pattern.matcher(line);
            if (lineMatcher.find()) {
                matchedLineNumbers.add(i);
                // 同时添加上下文（前后各一行）
                if (i > 0) matchedLineNumbers.add(i - 1);
                if (i < lines.length - 1) matchedLineNumbers.add(i + 1);
            }
            lineStart = lineEnd + 1; // +1 for newline
        }

        // 去重并排序
        List<Integer> uniqueLines = new ArrayList<>();
        for (Integer lineNum : matchedLineNumbers) {
            if (!uniqueLines.contains(lineNum)) {
                uniqueLines.add(lineNum);
            }
        }
        uniqueLines.sort(Integer::compareTo);

        // 限制返回的行数，避免过长
        int maxLines = 50;
        int count = 0;
        for (Integer lineNum : uniqueLines) {
            if (count >= maxLines) break;
            
            String line = lines[lineNum].trim();
            if (!line.isEmpty()) {
                sb.append(lineNum + 1).append(": ").append(line).append("\n");
                count++;
            }
        }

        String result = sb.toString().trim();
        
        // 如果结果过长，截取前面部分
        if (result.length() > 3000) {
            result = result.substring(0, 3000) + "\n...（内容过长，已截断）";
        }
        
        return result;
    }

    /**
     * 搜索结果内部类
     */
    private static class SearchResult {
        String relativePath;
        String matchedContent;

        SearchResult(String relativePath, String matchedContent) {
            this.relativePath = relativePath;
            this.matchedContent = matchedContent;
        }
    }
}
