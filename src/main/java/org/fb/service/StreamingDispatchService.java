package org.fb.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.constant.BusinessConstant;
import org.fb.service.assistant.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式业务分发服务
 * 负责流式对话的意图识别和业务分发
 * 逻辑与ChatServiceImpl.processByUserMeanings()保持一致
 * */
@Slf4j
@Service
public class StreamingDispatchService {

    @Autowired
    private ChatTypeAssistantStream chatTypeAssistantStream;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

    @Autowired
    private DoctorAgent doctorAgent;

    @Autowired
    private TranslaterService translaterService;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @Autowired
    private NaturalLanguageSQLAgent naturalLanguageSQLAgent;

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;

    /**
     * 流式处理用户消息
     * 包含意图识别和业务分发逻辑
     *
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @return 内容块的Flux流
     */
    public Flux<String> chat(Long memoryId, String userMessage) {
        log.info("StreamingDispatchService.chat 开始处理, memoryId: {}, message: {}", memoryId, userMessage);

        // 第一步：进行意图识别
        Long tempMemoryId = System.currentTimeMillis();
        log.info("开始意图识别, tempMemoryId: {}", tempMemoryId);

        return chatTypeAssistantStream.chat(tempMemoryId, userMessage)
                .collectList()
                .flatMapMany(intentChunks -> {
                    // 合并意图识别的结果
                    String intentResponse = String.join("", intentChunks);
                    log.info("意图识别结果: {}", intentResponse);

                    // 提取意图
                    String intent = extractIntent(intentResponse);
                    log.info("解析出的意图: {}, 原始响应: {}", intent, intentResponse);

                    // 第二步：根据意图选择业务处理服务
                    log.info("开始选择业务处理服务, intent: {}", intent);

                    Flux<String> resultFlux;

                    if (BusinessConstant.MEDICAL_TYPE.equals(intent)) {
                        log.info("选择业务处理服务: DoctorAgent");
                        resultFlux = processWithDoctorAgent(memoryId, userMessage);
                    } else if (BusinessConstant.TRANSLATION_TYPE.equals(intent)) {
                        log.info("选择业务处理服务: TranslaterService");
                        resultFlux = processWithTranslaterService(memoryId, userMessage);
                    } else if (BusinessConstant.TERM_EXTRACTION_TYPE.equals(intent)) {
                        log.info("选择业务处理服务: TermExtractionAgent");
                        resultFlux = processWithTermExtractionAgent(userMessage);
                    } else if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
                        log.info("选择业务处理服务: NaturalLanguageSQLAgent");
                        resultFlux = processWithNaturalLanguageSQLAgent(userMessage);
                    } else {
                        log.info("选择业务处理服务: ChatAssistantStream (默认)");
                        resultFlux = chatAssistantStream.chat(memoryId, userMessage);
                    }

                    return resultFlux;
                });
    }

    /**
     * 使用医生Agent处理（非流式，转Flux）
     */
    private Flux<String> processWithDoctorAgent(Long memoryId, String userMessage) {
        log.info("调用DoctorAgent.chat, memoryId: {}, message: {}", memoryId, userMessage);
        try {
            String result = doctorAgent.chat(memoryId, userMessage);
            log.info("DoctorAgent返回结果长度: {}", result != null ? result.length() : 0);
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("DoctorAgent处理失败", e);
            return Flux.just("抱歉，处理您的医疗咨询时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用翻译服务处理（非流式，转Flux）
     */
    private Flux<String> processWithTranslaterService(Long memoryId, String userMessage) {
        log.info("调用TranslaterService.translate, memoryId: {}, message: {}", memoryId, userMessage);
        try {
            String result = translaterService.translate(memoryId, userMessage);
            log.info("TranslaterService返回结果长度: {}", result != null ? result.length() : 0);
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("TranslaterService处理失败", e);
            return Flux.just("抱歉，翻译处理时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用术语提取Agent处理（非流式，转Flux）
     */
    private Flux<String> processWithTermExtractionAgent(String userMessage) {
        log.info("调用TermExtractionAgent.chat, message: {}", userMessage);
        try {
            String result = termExtractionAgent.chat(userMessage);
            log.info("TermExtractionAgent返回结果长度: {}", result != null ? result.length() : 0);
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("TermExtractionAgent处理失败", e);
            return Flux.just("抱歉，术语提取时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用自然语言SQL Agent处理（非流式，转Flux）
     */
    private Flux<String> processWithNaturalLanguageSQLAgent(String userMessage) {
        log.info("调用NaturalLanguageSQLAgent.convertToSQL, message: {}", userMessage);
        try {
            String result = naturalLanguageSQLAgent.convertToSQL(userMessage);
            log.info("NaturalLanguageSQLAgent返回结果长度: {}", result != null ? result.length() : 0);
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("NaturalLanguageSQLAgent处理失败", e);
            return Flux.just("抱歉，SQL查询时出现错误: " + e.getMessage());
        }
    }

    /**
     * 从AI返回的响应中提取intent
     */
    private String extractIntent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        // 尝试解析JSON格式的响应
        try {
            Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String intent = matcher.group(1).trim().toLowerCase();
                log.info("从JSON中提取的intent: {}", intent);
                return intent;
            }
        } catch (Exception e) {
            log.warn("解析JSON intent失败", e);
        }

        // 如果JSON解析失败，使用兜底策略
        String lowerResponse = aiResponse.toLowerCase();
        if (lowerResponse.contains(BusinessConstant.MEDICAL_TYPE)) {
            return BusinessConstant.MEDICAL_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TRANSLATION_TYPE)) {
            return BusinessConstant.TRANSLATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TERM_EXTRACTION_TYPE)) {
            return BusinessConstant.TERM_EXTRACTION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.SQL_OPERATION_TYPE)) {
            return BusinessConstant.SQL_OPERATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.DEFAULT_TYPE)) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        return BusinessConstant.DEFAULT_TYPE;
    }
}
