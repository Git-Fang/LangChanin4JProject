package org.fb.service.impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.fb.bean.MarkdownBlock;
import org.fb.bean.SummaryChunk;
import org.fb.service.DocumentSummarizationService;
import org.fb.service.MarkdownDocumentParser;
import org.fb.service.MarkdownSemanticChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档概要摘要服务实现
 */
@Service
public class DocumentSummarizationServiceImpl implements DocumentSummarizationService {
    private static final Logger log = LoggerFactory.getLogger(DocumentSummarizationServiceImpl.class);

    /**
     * 切分长度（1024字符）
     */
    private static final int CHUNK_SIZE = 1024;

    /**
     * 摘要最大字数
     */
    private static final int SUMMARY_MAX_CHARS = 150;

    /**
     * 句号分割符（中文句号、英文句号、感叹号、问号）
     */
    private static final String SENTENCE_DELIMITERS = "[。！？.!?]";

    @Autowired
    @Qualifier("qwenChatModel")
    private ChatModel chatModel;

    @Autowired
    @Qualifier("mdcExecutorService")
    private ExecutorService executorService;

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("qdrantEmbeddingStore")
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Autowired
    private MarkdownDocumentParser markdownDocumentParser;
    
    @Autowired
    private MarkdownSemanticChunker markdownSemanticChunker;

    @Value("${summarization.prompt-template:summarization-prompt}")
    private String promptTemplatePath;

    private String promptTemplate;

    /**
     * 构造函数，初始化提示词模板
     */
    public DocumentSummarizationServiceImpl() {
        initPromptTemplate();
    }

    /**
     * 初始化提示词模板
     */
    private void initPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(promptTemplatePath + ".txt");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    promptTemplate = new String(is.readAllBytes(), "UTF-8");
                    log.info("成功加载摘要提示词模板");
                }
            } else {
                log.warn("提示词模板文件不存在，使用默认模板");
                promptTemplate = getDefaultPromptTemplate();
            }
        } catch (Exception e) {
            log.warn("加载提示词模板失败，使用默认模板: {}", e.getMessage());
            promptTemplate = getDefaultPromptTemplate();
        }
    }

    /**
     * 获取默认提示词模板
     */
    private String getDefaultPromptTemplate() {
        return "请为以下文本生成一段150字以内的精炼中文摘要。\n\n要求：\n1. 严格控制在150字以内\n2. 突出核心信息，去除冗余表达\n3. 保留关键数据、名词和动词\n4. 句意完整，不截断句子\n5. 只输出摘要内容，不要任何解释说明\n\n文本内容：\n{{content}}";
    }

    @Override
    public List<SummaryChunk> summarizeDocument(MultipartFile file) {
        log.info("开始处理文档: {}", file.getOriginalFilename());
        
        String articleTitle = file.getOriginalFilename();
        String documentText;
        
        try {
            // 解析文档
            documentText = parseDocument(file);
            log.info("文档解析完成，文本长度: {} 字符", documentText.length());
        } catch (Exception e) {
            log.error("文档解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
        
        return summarizeText(documentText, articleTitle);
    }

    @Override
    public List<SummaryChunk> summarizeText(String documentText, String articleTitle) {
        return summarizeText(documentText, articleTitle, true);
    }

    /**
     * 对文本进行概要摘要处理
     *
     * @param documentText 文档文本内容
     * @param articleTitle 文章标题
     * @param saveToQdrant 是否保存到Qdrant向量数据库
     * @return 切分摘要结果列表
     */
    public List<SummaryChunk> summarizeText(String documentText, String articleTitle, boolean saveToQdrant) {
        if (documentText == null || documentText.trim().isEmpty()) {
            log.warn("文档文本为空");
            return Collections.emptyList();
        }
        
        // 语义切分
        List<String> chunks = semanticChunking(documentText);
        log.info("文档切分完成，共 {} 个切片", chunks.size());
        
        // 并发生成摘要
        List<SummaryChunk> results = new ArrayList<>();
        List<CompletableFuture<SummaryChunk>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            final int index = i;
            final String chunk = chunks.get(i);
            
            CompletableFuture<SummaryChunk> future = CompletableFuture.supplyAsync(() -> {
                String summary = generateSummary(chunk);
                return new SummaryChunk(articleTitle, summary, chunk, index + 1);
            }, executorService);
            
            futures.add(future);
        }
        
        // 收集结果
        for (CompletableFuture<SummaryChunk> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("获取摘要结果失败", e);
            }
        }
        
        // 按切片顺序排序
        results.sort((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()));
        
        log.info("摘要生成完成，共生成 {} 个摘要", results.size());
        
        // 保存到Qdrant向量数据库
        if (saveToQdrant && !results.isEmpty()) {
            saveChunksToQdrant(results);
        }
        
        return results;
    }

    /**
     * 将切片摘要保存到Qdrant向量数据库
     * 格式：【文章标题】xxx | 【段落摘要】zzz | 【切片内容】xxx
     *
     * @param chunks 切片摘要列表
     */
    private void saveChunksToQdrant(List<SummaryChunk> chunks) {
        log.info("开始保存 {} 个切片到Qdrant向量数据库", chunks.size());
        
        try {
            for (SummaryChunk chunk : chunks) {
                // 构建存储格式：【文章标题】xxx | 【段落摘要】zzz | 【切片内容】xxx
                String formattedContent = String.format("【文章标题】%s | 【段落摘要】%s | 【切片内容】%s",
                        chunk.getArticleTitle() != null ? chunk.getArticleTitle() : "未命名",
                        chunk.getSummary() != null ? chunk.getSummary() : "",
                        chunk.getContent() != null ? chunk.getContent() : "");
                
                // 创建TextSegment
                TextSegment segment = TextSegment.from(formattedContent);
                segment.metadata().put("articleTitle", chunk.getArticleTitle());
                segment.metadata().put("summary", chunk.getSummary());
                segment.metadata().put("chunkIndex", chunk.getChunkIndex());
                segment.metadata().put("type", "SUMMARIZATION");
                
                // 向量化并存储
                List<TextSegment> segments = List.of(segment);
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                embeddingStore.addAll(embeddings, segments);
                
                log.debug("切片 {} 已保存到Qdrant", chunk.getChunkIndex());
            }
            
            log.info("切片摘要保存完成，共保存 {} 个切片到Qdrant", chunks.size());
            
        } catch (Exception e) {
            log.error("保存切片到Qdrant失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> semanticChunking(String text) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        // 先按段落分割（换行符）
        String[] paragraphs = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            // 跳过空段落
            if (paragraph.trim().isEmpty()) {
                continue;
            }
            
            // 如果当前块为空，直接添加段落
            if (currentChunk.length() == 0) {
                currentChunk.append(paragraph.trim());
            }
            // 如果添加当前段落会超过限制
            else if (currentChunk.length() + paragraph.trim().length() + 1 > CHUNK_SIZE) {
                // 先保存当前块
                String currentText = currentChunk.toString();
                
                // 如果当前块已经很长（>= CHUNK_SIZE * 0.7），按句号切分
                if (currentText.length() >= CHUNK_SIZE * 0.7) {
                    List<String> splitChunks = splitBySentenceWithLimit(currentText, CHUNK_SIZE);
                    chunks.addAll(splitChunks);
                } else {
                    chunks.add(currentText);
                }
                
                // 开始新的块
                currentChunk = new StringBuilder(paragraph.trim());
            }
            // 可以添加当前段落
            else {
                currentChunk.append("\n").append(paragraph.trim());
            }
        }
        
        // 处理最后一个块
        if (currentChunk.length() > 0) {
            String lastText = currentChunk.toString();
            if (lastText.length() >= CHUNK_SIZE * 0.7) {
                List<String> splitChunks = splitBySentenceWithLimit(lastText, CHUNK_SIZE);
                chunks.addAll(splitChunks);
            } else {
                chunks.add(lastText);
            }
        }
        
        return chunks;
    }

    /**
     * 按句号切分文本，确保每个切片不超过限制长度
     */
    private List<String> splitBySentenceWithLimit(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return result;
        }
        
        // 如果文本本身就不超过限制，直接返回
        if (text.length() <= maxLength) {
            result.add(text);
            return result;
        }
        
        // 按句号分割
        Pattern pattern = Pattern.compile(SENTENCE_DELIMITERS);
        String[] sentences = pattern.split(text);
        Matcher matcher = pattern.matcher(text);
        
        List<String> sentenceList = new ArrayList<>();
        int lastEnd = 0;
        
        while (matcher.find()) {
            String sentence = text.substring(lastEnd, matcher.end());
            sentenceList.add(sentence);
            lastEnd = matcher.end();
        }
        
        // 处理最后一个句子（如果没有句号结尾）
        if (lastEnd < text.length()) {
            sentenceList.add(text.substring(lastEnd));
        }
        
        // 组合句子，确保每个块不超过限制
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentenceList) {
            // 如果单个句子就超过限制，按字数强制切分
            if (sentence.length() > maxLength) {
                // 先保存当前块
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                
                // 强制切分长句子
                result.addAll(forceSplit(sentence, maxLength));
            }
            // 如果添加当前句子会超过限制
            else if (currentChunk.length() + sentence.length() > maxLength) {
                // 保存当前块
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                }
                // 开始新块
                currentChunk = new StringBuilder(sentence);
            }
            // 可以添加
            else {
                if (currentChunk.length() > 0) {
                    currentChunk.append(sentence.substring(sentence.length() - 1));
                } else {
                    currentChunk.append(sentence);
                }
            }
        }
        
        // 处理最后一个块
        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();
            if (!lastChunk.isEmpty()) {
                // 如果最后一个块太长，强制切分
                if (lastChunk.length() > maxLength) {
                    result.addAll(forceSplit(lastChunk, maxLength));
                } else {
                    result.add(lastChunk);
                }
            }
        }
        
        return result;
    }

    /**
     * 强制按字符数切分文本
     */
    private List<String> forceSplit(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            
            // 尽量在句号处切分
            if (end < text.length()) {
                String substr = text.substring(start, end);
                int lastDelimiter = -1;
                for (String delim : new String[]{"。", "！", "？", ".", "!", "?"}) {
                    int pos = substr.lastIndexOf(delim);
                    if (pos > lastDelimiter) {
                        lastDelimiter = pos;
                    }
                }
                
                // 如果找到句号，在句号后切分
                if (lastDelimiter > maxLength * 0.5) {
                    end = start + lastDelimiter + 1;
                }
            }
            
            result.add(text.substring(start, end).trim());
            start = end;
        }
        
        return result;
    }

    @Override
    public String generateSummary(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        
        try {
            // 构建提示词
            String prompt = promptTemplate.replace("{{content}}", chunk);
            
            // 调用LLM生成摘要
            String summary = chatModel.chat(prompt);
            
            // 清理摘要（去除可能的引号、换行等）
            summary = summary.trim();
            if (summary.startsWith("\"") && summary.endsWith("\"")) {
                summary = summary.substring(1, summary.length() - 1);
            }
            if (summary.startsWith("'") && summary.endsWith("'")) {
                summary = summary.substring(1, summary.length() - 1);
            }
            summary = summary.replaceAll("[\"']", "").trim();
            
            // 截断到200字
            if (summary.length() > SUMMARY_MAX_CHARS) {
                summary = summary.substring(0, SUMMARY_MAX_CHARS) + "...";
            }
            
            log.debug("生成摘要成功，长度: {} 字符", summary.length());
            return summary;
            
        } catch (Exception e) {
            log.error("生成摘要失败: {}", e.getMessage(), e);
            return "摘要生成失败: " + e.getMessage();
        }
    }

    /**
     * 解析文档
     */
    private String parseDocument(MultipartFile file) throws IOException {
        // 保存临时文件
        Path tempDir = Files.createTempDirectory("summarization");
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        Path tempFile = tempDir.resolve(UUID.randomUUID().toString() + "." + extension);
        
        try {
            // 保存上传的文件
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            String text;
            
            // 根据文件类型选择解析方法
            if ("pdf".equals(extension)) {
                // PDF文件使用PDFBox解析器，确保中文编码正确
                text = parsePdfWithTika(tempFile);
            } else if ("md".equals(extension) || "markdown".equals(extension)) {
                // Markdown文件使用专用解析器，保留图片、图表等结构
                text = parseMarkdownDocument(tempFile, tempDir);
            } else {
                // 其他文件使用LangChain4j的解析器
                Document document = FileSystemDocumentLoader.loadDocument(tempFile.toString(), new ApacheTikaDocumentParser());
                text = document.text();
            }
            
            // 清理和规范化文本
            text = normalizeText(text);
            
            log.info("文档解析完成，提取文本长度: {} 字符", text.length());
            return text;
            
        } finally {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                log.warn("清理临时文件失败", e);
            }
        }
    }

    /**
     * 解析Markdown文档
     * 使用专用解析器提取图片描述、图表信息等
     */
    private String parseMarkdownDocument(Path mdFile, Path baseDir) throws IOException {
        log.info("使用Markdown专用解析器解析文档");
        
        try {
            // 读取Markdown文件内容
            String mdContent = Files.readString(mdFile, StandardCharsets.UTF_8);
            
            if (mdContent == null || mdContent.trim().isEmpty()) {
                return "";
            }
            
            // 解析Markdown结构并处理图片、图表
            List<MarkdownBlock> blocks = markdownDocumentParser.parse(mdContent, baseDir);
            
            if (blocks == null || blocks.isEmpty()) {
                return normalizeText(mdContent);
            }
            
            // 使用语义切片器生成切片
            List<String> chunks = markdownSemanticChunker.chunk(blocks);
            
            // 合并为完整文本
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                if (i > 0) {
                    result.append("\n\n---\n\n");
                }
                result.append(chunks.get(i));
            }
            
            String processedText = result.toString();
            log.info("Markdown解析完成，原始长度: {}, 处理后长度: {}", mdContent.length(), processedText.length());
            
            return processedText;
            
        } catch (Exception e) {
            log.error("Markdown解析失败，使用备用解析: {}", e.getMessage());
            // 解析失败时，回退到简单文本提取
            return Files.readString(mdFile, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * 使用PDFBox解析PDF（更好的中文支持）
     */
    private String parsePdfWithTika(Path pdfPath) throws IOException {
        try (InputStream is = Files.newInputStream(pdfPath);
             PDDocument document = org.apache.pdfbox.Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);  // 按位置排序
            
            String text = stripper.getText(document);
            
            log.debug("PDF解析完成，页数: {}, 提取字符数: {}", document.getNumberOfPages(), text.length());
            
            return text;
            
        } catch (Exception e) {
            log.error("PDF解析失败: {}", e.getMessage(), e);
            throw new IOException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理和规范化文本
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String result = text;
        
        // 移除控制字符（保留换行和制表符）
        result = result.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // 规范化换行符
        result = result.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        
        // 合并多个连续空行
        result = result.replaceAll("\\n{3,}", "\n\n");
        
        // 移除行首行尾空白（保留段落缩进）
        String[] lines = result.split("\n");
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                normalized.append(line);
                if (i < lines.length - 1) {
                    normalized.append("\n");
                }
            }
        }
        
        return normalized.toString().trim();
    }
}
