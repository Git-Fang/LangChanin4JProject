package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatForm;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.ChatAssistantStream;
import org.fb.service.assistant.TranslaterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ragTranslation/")
@Slf4j
@Tag(name = "RAG增强翻译")
public class TranslationController {

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

    @Autowired
    private TranslaterService translaterService;

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
}
