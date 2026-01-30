package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.ChatAssistantStream;
import org.fb.service.assistant.TermExtractionAgent;
import org.fb.service.assistant.TranslaterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ragTranslation/")
@Tag(name = "RAG增强翻译")
public class TranslationController {
    private static final Logger log = LoggerFactory.getLogger(TranslationController.class);

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

    @Autowired
    private TranslaterService translaterService;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @GetMapping(value = "/chat")
    @Operation(summary = "1-增强式对话")
    public Object ask(@RequestParam("question") String question) throws IOException {
        try {
            return chatAssistant.chat(question);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("textSegment cannot be null")) {
                // 记录错误并返回友好错误信息
                log.error("Embedding store contains null text segments, please check your data", e);
                return ResponseEntity.badRequest()
                        .body("暂时无法处理您的请求，请稍后重试");
            }
            throw e;
        }
    }


    @Operation(summary = "2-流式增强对话")
    @PostMapping(value = "/chatStream", produces = "text/stream;charset=utf-8")
    public Flux<String> chat2(@RequestBody ChatForm chatForm) {
        Flux<String> chat = null;
        try {
            chat = chatAssistantStream.chat(chatForm.getMemoryId(), chatForm.getMessage());
        } catch (Exception e) {
            // 记录错误并返回友好错误信息
            log.error("流式翻译过程出错：", e);
            throw e;
        }
        return chat;
    }

    @Operation(summary = "2.2-流式增强对话")
    @PostMapping(value = "/chatStream2", produces = "text/stream;charset=utf-8")
    public Flux<String> chat3(@RequestParam("question") String question) {
        Flux<String> chat = null;
        try {
            chat = chatAssistantStream.chat(question);
        } catch (Exception e) {
            // 记录错误并返回友好错误信息
            log.error("流式翻译过程出错：", e);
            throw e;
        }
        return chat;
    }

    @GetMapping(value = "/rag03/trans")
    @Operation(summary = "3-翻译对话")
    public Object trans(@RequestParam("content") String content) throws IOException {
        try {
            return translaterService.translate(content);
        } catch (Exception e) {
            log.error("当前翻译出错：", e);

            return ResponseEntity.badRequest().body("暂时无法处理您的请求，请稍后重试");
        }
    }

    @GetMapping(value = "/extractTerms")
    @Operation(summary = "4-术语提取与保存")
    public Object extractTerms(@RequestParam("content") String content) {
        try {
            log.info("开始术语提取: {}", content);
            String result = termExtractionAgent.chat(content);
            log.info("术语提取完成: {}", result);
            return result;
        } catch (Exception e) {
            log.error("术语提取出错：", e);
            return ResponseEntity.badRequest().body("暂时无法处理您的请求，请稍后重试");
        }
    }

    @GetMapping(value = "/correctAndTranslate")
    @Operation(summary = "5-术语纠正式翻译")
    public Object correctAndTranslate(
            @RequestParam("content") String content,
            @RequestParam(value = "targetLanguage", defaultValue = "英文") String targetLanguage) {
        try {
            log.info("开始术语纠翻译: {} -> {}", content, targetLanguage);
            String userMessage = "翻译成" + targetLanguage + "：" + content;
            String result = translaterService.translate(userMessage);
            log.info("术语纠翻译完成: {}", result);
            return result;
        } catch (Exception e) {
            log.error("术语纠翻译出错：", e);
            return ResponseEntity.badRequest().body("暂时无法处理您的请求，请稍后重试");
        }
    }
}
