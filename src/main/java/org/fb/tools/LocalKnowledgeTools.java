package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地知识库检索工具
 * 用于检索 .agent 和 knowledge 目录下的本地知识文件
 */
@Component
public class LocalKnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeTools.class);

    // 知识库根目录
    private static final String KNOWLEDGE_DIR = "knowledge";
    private static final String AGENT_DIR = ".agent";

    /**
     * 获取本地知识库的目录结构信息
     * @param basePath 知识库基础路径（可选，默认为 knowledge/）
     * @return 知识库的目录结构描述
     */
    @Tool(name = "local_kb_get_structure", value = "获取本地知识库的目录结构信息:根据传入的基础路径(basePath)获取本地知识库的目录结构概览，帮助了解知识库的组织结构和可用内容领域")
    public String getKnowledgeBaseStructure(@P(value = "知识库基础路径，默认为knowledge/", required = false) String basePath) {
        String base = (basePath != null && !basePath.isEmpty()) ? basePath : KNOWLEDGE_DIR;
        log.info("获取本地知识库目录结构，basePath: {}", base);

        StringBuilder result = new StringBuilder();
        result.append("本地知识库目录结构（").append(base).append("）：\n\n");

        // 检查知识库根目录是否存在
        Path knowledgePath = Paths.get(base);
        if (!Files.exists(knowledgePath)) {
            // 尝试检查常见位置
            List<String> commonPaths = new ArrayList<>();
            commonPaths.add(base);
            commonPaths.add("./" + base);
            commonPaths.add("../" + base);
            commonPaths.add("./" + base + "-personal");
            commonPaths.add("../" + base + "-personal");

            for (String path : commonPaths) {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    knowledgePath = p;
                    log.info("找到知识库目录: {}", path);
                    break;
                }
            }

            if (!Files.exists(knowledgePath)) {
                return "未找到本地知识库目录。可用路径包括：" + String.join(", ", commonPaths) + "。请确认知识库位置后重试。";
            }
        }

        try {
            // 读取根目录下的 data_structure.md（如果存在）
            Path structFile = knowledgePath.resolve("data_structure.md");
            if (Files.exists(structFile)) {
                String content = new String(Files.readAllBytes(structFile), StandardCharsets.UTF_8);
                result.append("=== 知识库索引文件内容 ===\n");
                result.append(content.substring(0, Math.min(content.length(), 2000)));
                result.append("\n\n[提示：如需查看完整索引文件，请使用 Read 工具读取完整内容]\n\n");
            }

            // 列出所有子目录
            result.append("=== 知识库子目录 ===\n");
            try (Stream<Path> paths = Files.list(knowledgePath).filter(p -> p.toFile().isDirectory())) {
                List<Path> dirs = paths.collect(Collectors.toList());
                for (Path dir : dirs) {
                    result.append("- ").append(dir.getFileName()).append("/\n");

                    // 尝试列出二级目录
                    try (Stream<Path> subPaths = Files.list(dir).filter(p -> p.toFile().isDirectory())) {
                        List<Path> subDirs = subPaths.collect(Collectors.toList());
                        for (Path subDir : subDirs) {
                            result.append("  └─ ").append(subDir.getFileName()).append("/\n");
                        }
                    }
                }
            }

            result.append("\n=== 使用说明 ===\n");
            result.append("- 使用 local_kb_search 工具进行关键词搜索\n");
            result.append("- 搜索会遍历所有 Markdown、PDF、Excel 等文件\n");
            result.append("- 优先查看 data_structure.md 了解目录结构\n");

        } catch (IOException e) {
            log.error("读取知识库目录结构失败", e);
            return "获取知识库目录结构失败：" + e.getMessage();
        }

        return result.toString();
    }

    /**
     * 在本地知识库中搜索关键词
     * @param query 搜索关键词
     * @param basePath 知识库基础路径（可选，默认为 knowledge/）
     * @return 匹配的搜索结果摘要
     */
    @Tool(name = "local_kb_search", value = "在本地知识库中搜索关键词:根据传入的查询关键词{{query}}在本地知识库目录中进行文本搜索，返回包含关键词的文件路径和相关内容片段。")
    public String searchLocalKnowledgeBase(
            @P(value = "搜索关键词", required = true) String query,
            @P(value = "知识库基础路径，默认为knowledge/", required = false) String basePath) {

        String base = (basePath != null && !basePath.isEmpty()) ? basePath : KNOWLEDGE_DIR;
        log.info("搜索本地知识库，query: {}, basePath: {}", query, base);

        StringBuilder result = new StringBuilder();
        result.append("搜索关键词：").append(query).append("\n");
        result.append("搜索目录：").append(base).append("\n\n");

        Path knowledgePath = Paths.get(base);
        if (!Files.exists(knowledgePath)) {
            // 尝试常见位置
            for (String path : new String[]{base, "./" + base, "../" + base}) {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    knowledgePath = p;
                    log.info("找到知识库目录: {}", path);
                    break;
                }
            }
            if (!Files.exists(knowledgePath)) {
                return "未找到本地知识库目录。请确认以下路径是否存在：\n" +
                        "- " + base + "\n" +
                        "- ./" + base + "\n" +
                        "- ../" + base + "\n\n" +
                        "提示：可以使用 local_kb_get_structure 工具先查看知识库结构。";
            }
        }

        List<SearchResult> results = new ArrayList<>();
        int maxResults = 50; // 限制返回结果数量

        try {
            // 第一步：检查文件名是否直接匹配（模糊匹配）
            List<Path> filenameMatches = searchByFilename(knowledgePath, query);
            for (Path match : filenameMatches) {
                if (results.size() >= maxResults) break;
                // 提取文件内容作为匹配片段
                String snippet = "[文件名匹配] " + match.getFileName().toString();
                if (match.toString().endsWith(".pdf")) {
                    snippet += " (PDF文件，将提取全文进行检索)";
                } else if (match.toString().endsWith(".txt") || match.toString().endsWith(".md")) {
                    // 读取文件前500字符作为预览
                    try {
                        String content = new String(Files.readAllBytes(match), StandardCharsets.UTF_8);
                        String preview = content.substring(0, Math.min(500, content.length())).replace("\n", " ");
                        snippet += ": " + preview;
                    } catch (Exception e) {
                        log.warn("读取文件预览失败: {}", match);
                    }
                }
                results.add(new SearchResult(match.toString(), snippet));
            }

            // 第二步：递归搜索所有文件内容
            searchFiles(knowledgePath, query, results, maxResults);

        } catch (IOException e) {
            log.error("搜索知识库失败", e);
            return "搜索知识库失败：" + e.getMessage();
        }

        if (results.isEmpty()) {
            result.append("未在本地知识库中找到与【").append(query).append("】相关的内容。\n\n");
            result.append("建议：\n");
            result.append("1. 尝试使用其他关键词\n");
            result.append("2. 检查知识库路径是否正确\n");
            result.append("3. 确认知识库中是否包含相关文档\n");
        } else {
            result.append("找到 ").append(results.size()).append(" 个匹配结果：\n\n");
            int count = 0;
            for (SearchResult sr : results) {
                if (count >= 20) { // 限制显示前20个结果
                    result.append("\n... 还有 ").append(results.size() - 20).append(" 个结果未显示\n");
                    break;
                }
                result.append("【文件】").append(sr.relativePath).append("\n");
                result.append("【匹配片段】\n");
                result.append(sr.snippet).append("\n\n");
                count++;
            }
        }

        result.append("=== 搜索说明 ===\n");
        result.append("- 以上搜索覆盖本地知识库目录（.agent 和 knowledge）\n");
        result.append("- 搜索包含文件名模糊匹配和文件内容检索\n");
        result.append("- 如果未找到相关信息，系统将自动搜索在线知识库（mysql/qdrant/mongoDB）\n");
        result.append("- 如需更精确的搜索，请使用更具体的关键词\n");

        return result.toString();
    }

    /**
     * 根据文件名模糊搜索匹配的文件
     * 支持关键词分词匹配、去除空格匹配、去除特殊字符匹配
     */
    private List<Path> searchByFilename(Path knowledgePath, String query) {
        List<Path> matches = new ArrayList<>();
        String[] queryTerms = query.toLowerCase().split("[\\s,，。]+");

        try (Stream<Path> paths = Files.walk(knowledgePath)) {
            paths.filter(p -> p.toFile().isFile())
                 .filter(p -> {
                     String filename = p.getFileName().toString().toLowerCase();
                     // 检查是否匹配：包含所有查询词
                     for (String term : queryTerms) {
                         if (term.length() < 2) continue; // 忽略单字符
                         if (!filename.contains(term)) {
                             return false;
                         }
                     }
                     return true;
                 })
                 .forEach(matches::add);
        } catch (IOException e) {
            log.warn("文件名搜索失败: {}", e.getMessage());
        }
        return matches;
    }

    /**
     * 在本地知识库的 .agent/skills 目录中搜索
     * @param query 搜索关键词
     * @return 匹配的搜索结果摘要
     */
    @Tool(name = "local_agent_skill_search", value = "在.agent技能目录中搜索关键词:在.agent/skills目录下搜索与{{query}}相关的技能定义、参考文档或配置信息")
    public String searchAgentSkills(
            @P(value = "搜索关键词", required = true) String query) {

        log.info("搜索 .agent/skills 目录，query: {}", query);

        StringBuilder result = new StringBuilder();
        result.append("搜索 .agent/skills 目录：").append(query).append("\n\n");

        Path agentPath = Paths.get(AGENT_DIR);
        if (!Files.exists(agentPath)) {
            return "未找到 .agent 目录。技能目录搜索不可用。";
        }

        List<SearchResult> results = new ArrayList<>();

        try {
            // 搜索 .agent/skills 目录
            Path skillsPath = agentPath.resolve("skills");
            if (Files.exists(skillsPath)) {
                searchFiles(skillsPath, query, results, 30);
            }

            // 搜索 .agent 下的 SKILL.md 文件
            searchFiles(agentPath, query, results, 20);

        } catch (IOException e) {
            log.error("搜索 agent 目录失败", e);
            return "搜索 .agent 目录失败：" + e.getMessage();
        }

        if (results.isEmpty()) {
            result.append("未在 .agent 目录中找到与【").append(query).append("】相关的内容。\n");
        } else {
            result.append("找到 ").append(Math.min(results.size(), 10)).append(" 个匹配结果：\n\n");
            int count = 0;
            for (SearchResult sr : results) {
                if (count >= 10) break;
                result.append("【文件】").append(sr.relativePath).append("\n");
                result.append("【匹配片段】\n").append(sr.snippet).append("\n\n");
                count++;
            }
        }

        return result.toString();
    }

    /**
     * 递归搜索文件
     */
    private void searchFiles(Path dir, String query, List<SearchResult> results, int maxResults) throws IOException {
        if (results.size() >= maxResults) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toFile().isFile())
                 .filter(p -> {
                     String name = p.getFileName().toString().toLowerCase();
                     // 搜索常见文本文件类型，包括 PDF
                     return name.endsWith(".md") ||
                            name.endsWith(".txt") ||
                            name.endsWith(".json") ||
                            name.endsWith(".yaml") ||
                            name.endsWith(".yml") ||
                            name.endsWith(".xml") ||
                            name.endsWith(".csv") ||
                            name.endsWith(".pdf");
                 })
                 .limit(maxResults - results.size() + 10) // 稍微多取一些
                 .forEach(path -> {
                     String name = path.getFileName().toString().toLowerCase();
                     if (name.endsWith(".pdf")) {
                         searchInPdf(path, query, results);
                     } else {
                         searchInFile(path, query, results);
                     }
                 });
        }
    }

    /**
     * 在单个文件中搜索关键词
     */
    private void searchInFile(Path filePath, String query, List<SearchResult> results) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            searchInContent(content, query, results, filePath.toString());
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath);
        }
    }

    /**
     * 使用 Apache Tika 在 PDF 文件中搜索关键词
     */
    private void searchInPdf(Path filePath, String query, List<SearchResult> results) {
        try {
            // 使用 Tika 提取 PDF 文本内容
            String content = extractTextFromPdf(filePath);
            if (content != null && !content.isEmpty()) {
                searchInContent(content, query, results, filePath.toString());
            }
        } catch (Exception e) {
            log.warn("读取 PDF 文件失败: {}, 错误: {}", filePath, e.getMessage());
        }
    }

    /**
     * 使用 Apache PDFBox 从 PDF 文件中提取文本内容
     */
    private String extractTextFromPdf(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text;
        }
    }

    /**
     * 在文本内容中搜索关键词并生成结果片段
     */
    private void searchInContent(String content, String query, List<SearchResult> results, String filePath) {
        String[] lines = content.split("\n");
        String queryLower = query.toLowerCase();

        // 简单关键词匹配
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(queryLower)) {
                // 提取上下文片段
                int start = Math.max(0, i - 2);
                int end = Math.min(lines.length, i + 3);
                StringBuilder snippet = new StringBuilder();
                for (int j = start; j < end; j++) {
                    if (j > start) snippet.append("\n");
                    snippet.append(j == i ? ">>> " : "    ");
                    snippet.append(lines[j]);
                }

                results.add(new SearchResult(filePath, snippet.toString()));

                // 每文件最多保留3个匹配
                long countInFile = results.stream()
                        .filter(r -> r.relativePath.equals(filePath))
                        .count();
                if (countInFile >= 3) {
                    break;
                }
            }
        }
    }

    /**
     * 搜索结果内部类
     */
    private static class SearchResult {
        String relativePath;
        String snippet;

        SearchResult(String relativePath, String snippet) {
            this.relativePath = relativePath;
            this.snippet = snippet;
        }
    }
}
