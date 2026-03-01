package org.fb.engine;

import lombok.extern.slf4j.Slf4j;
import org.fb.constant.BusinessConstant;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 意图识别规则引擎
 * 使用规则引擎优先匹配，LLM 兜底
 * 
 * 规则优先级（数字越小优先级越高）：
 * 1. RAG_RETRIEVAL - 知识库检索
 * 2. MEDICAL - 医疗预约
 * 3. SQL_TRANSFER - 数据库查询
 * 4. TRANSLATION - 翻译
 * 5. TERM_EXTRACTION - 术语提取
 * 6. GENERAL - 通用问答（默认）
 */
@Slf4j
@Component
public class IntentRecognitionEngine {

    /**
     * 规则列表（按优先级排序）
     */
    private List<IntentRule> rules;

    /**
     * 缓存已识别过的消息，避免重复匹配
     */
    private final Map<String, String> intentCache = new ConcurrentHashMap<>();

    /**
     * 缓存大小限制
     */
    private static final int CACHE_MAX_SIZE = 10000;

    /**
     * 初始化规则列表
     */
    @PostConstruct
    public void init() {
        rules = List.of(
                // ====== 1. RAG 检索类规则（最高优先级） ======
                // 匹配"结合知识库"、"根据文档"、"过往项目经历"等需要检索已有内容的请求
                IntentRule.of(BusinessConstant.RAG_RETRIEVAL_TYPE, 10,
                        List.of(
                                // 明确提及知识库/文档
                                "(结合|基于|根据|参照).*(知识库|文档|资料|内容|文件)",
                                "(查询|检索|搜索).*(知识库|文档|资料|内容)",
                                "(过往|以前|曾经|之前).*(项目|经历|经验|工作|任职)",
                                "(我的|用户|客户).*(项目|经历|经验|历史)",
                                "(参考|借助).*(知识库|文档)",
                                // 英文关键词
                                "based on (the )?(knowledge|document|file)",
                                "retrieve (from|search).*(knowledge|document)",
                                "past (project|experience|history)"
                        ),
                        "知识库检索"
                ),

                // ====== 2. 股票价值分析类规则 ======
                IntentRule.of(BusinessConstant.STOCK_ANALYSIS_TYPE, 15,
                        List.of(
                                "分析.*公司",
                                "帮我分析.*公司",
                                "股票.*分析",
                                "价值.*投资",
                                "投资.*建议",
                                "公司.*估值",
                                "基本面.*分析",
                                "财报.*分析",
                                "市盈率",
                                "市值.*分析",
                                "st(ock)?\\s+analy",
                                "analy(ze|ysis)\\s+.*(company|stock)"
                        ),
                        "股票价值分析"
                ),

                // ====== 3. 医疗类规则 ======
                IntentRule.of(BusinessConstant.MEDICAL_TYPE, 20,
                        List.of(
                                "医疗|健康|医院|挂号|预约|医生|看病|疾病|症状|治疗|体检|就诊|问诊",
                                "预约(?!.*翻译)",  // 排除"预约翻译"
                                "取消预约|删除预约|撤销预约",
                                "挂号|门诊|住院|手术|检查|化验|处方"
                        ),
                        "医疗健康"
                ),

                // ====== 3. SQL 查询类规则 ======
                // 重要：必须是明确的数据库查询请求，不包括询问知识/概念的问题
                IntentRule.of(BusinessConstant.SQL_OPERATION_TYPE, 30,
                        List.of(
                                // 明确要求查询数据库
                                "^(查询|查一下|帮我查|查一下|找一下).*(表|数据库|数据|记录)",
                                ".*(表|数据库).*(数据|记录).*(查询|获取|找出)",
                                "^.*(有多少|多少条|几条).*(记录|数据).*$",
                                // SELECT 开头的 SQL
                                "^\\s*(select|SELECT)\\s+"
                        ),
                        "数据库查询"
                ),

                // ====== 4. 翻译类规则 ======
                IntentRule.of(BusinessConstant.TRANSLATION_TYPE, 40,
                        List.of(
                                "翻译|译成|译为|多语言|语言转换",
                                "translate (to|into)?\\s*\\w+",
                                "把.*翻译成",
                                "(英译中|中译英|日译中|中译日)"
                        ),
                        "翻译"
                ),

                // ====== 5. 术语提取类规则 ======
                IntentRule.of(BusinessConstant.TERM_EXTRACTION_TYPE, 50,
                        List.of(
                                "术语|提取|专业词汇|词汇提取",
                                "(提取|提取出).*(术语|词汇|专业词)",
                                "(找出|识别).*(术语|专业词|关键词)"
                        ),
                        "术语提取"
                ),

                // ====== 6. 通用类规则（默认，优先级最低） ======
                IntentRule.of(BusinessConstant.DEFAULT_TYPE, 100,
                        List.of(
                                ".*"  // 匹配任意内容，作为兜底
                        ),
                        "通用问答"
                )
        );

        log.info("意图识别规则引擎初始化完成，共加载 {} 条规则", rules.size());
        rules.forEach(rule -> log.info("  - {}", rule));
    }

    /**
     * 识别意图（带缓存）
     * 
     * @param message 用户消息
     * @return 识别的意图类型
     */
    public String recognize(String message) {
        if (message == null || message.trim().isEmpty()) {
            log.warn("消息为空，使用默认意图");
            return BusinessConstant.DEFAULT_TYPE;
        }

        String trimmedMessage = message.trim();

        // 先检查缓存
        String cachedIntent = intentCache.get(trimmedMessage);
        if (cachedIntent != null) {
            log.debug("意图识别命中缓存: {} -> {}", trimmedMessage.substring(0, Math.min(20, trimmedMessage.length())), cachedIntent);
            return cachedIntent;
        }

        // 执行规则匹配
        String intent = doRecognize(trimmedMessage);

        // 添加到缓存（限制缓存大小）
        if (intentCache.size() < CACHE_MAX_SIZE) {
            intentCache.put(trimmedMessage, intent);
        }

        log.info("意图识别结果: {} -> {}, 消息: {}", 
                trimmedMessage.substring(0, Math.min(30, trimmedMessage.length())), 
                intent, 
                trimmedMessage.length() > 50 ? "..." : trimmedMessage);

        return intent;
    }

    /**
     * 执行规则匹配（核心逻辑）
     */
    private String doRecognize(String message) {
        // 按优先级顺序遍历规则，找到第一个匹配的
        for (IntentRule rule : rules) {
            if (rule.matches(message)) {
                log.debug("匹配到规则: {}, 消息: {}", rule.getIntent(), message);
                
                // 如果匹配到默认规则，但消息中有其他关键词特征，进行二次判断
                if (BusinessConstant.DEFAULT_TYPE.equals(rule.getIntent())) {
                    String refinedIntent = refineDefaultIntent(message);
                    if (refinedIntent != null) {
                        log.debug("默认规则二次判断优化: {} -> {}", rule.getIntent(), refinedIntent);
                        return refinedIntent;
                    }
                }
                
                return rule.getIntent();
            }
        }

        // 理论上不会走到这里，因为默认规则会匹配所有
        log.warn("未匹配到任何规则，使用默认意图");
        return BusinessConstant.DEFAULT_TYPE;
    }

    /**
     * 对默认意图进行二次优化判断
     * 处理一些边界情况
     */
    private String refineDefaultIntent(String message) {
        String lowerMessage = message.toLowerCase();

        // 如果包含"数据库"但不是查询请求，归类为通用
        if (lowerMessage.contains("数据库") || lowerMessage.contains("sql")) {
            // 已经排除了 sql_transfer 的明确查询请求
            // 这里确认不是查询，就是通用知识问题
            if (!lowerMessage.matches(".*(查询|查|找|获取|select).*(表|数据|记录).*")) {
                return BusinessConstant.DEFAULT_TYPE;
            }
        }

        // 其他情况保持默认
        return null;
    }

    /**
     * 检查是否需要启用 RAG 检索
     * 
     * @param intent 识别的意图
     * @return 是否需要检索
     */
    public boolean shouldRetrieve(String intent) {
        return BusinessConstant.RAG_RETRIEVAL_TYPE.equals(intent);
    }

    /**
     * 检查是否需要使用 LLM 进行意图识别（兜底）
     * 当规则引擎无法确定时返回 true
     * 
     * @param intent 识别的意图
     * @return 是否需要 LLM 兜底
     */
    public boolean needsLLMFallback(String intent) {
        // 当识别结果是默认类型时，可能需要 LLM 进一步确认
        // 这里暂时返回 false，完全使用规则引擎
        return false;
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        intentCache.clear();
        log.info("意图识别缓存已清空");
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
                "size", intentCache.size(),
                "maxSize", CACHE_MAX_SIZE
        );
    }
}
