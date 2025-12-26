package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.TranslaterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/ragTranslation/trans/")
@Slf4j
@Tag(name = "RAG增强翻译")
public class TranslationController {

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private TranslaterService translaterService;

    @GetMapping(value = "/rag01/chat")
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

    @GetMapping(value = "/rag02/trans")
    @Operation(summary = "2-翻译对话")
    public Object trans(@RequestParam("content") String content) throws IOException {
        try {
            return translaterService.translate(content);
        } catch (Exception e) {
            log.error("当前翻译出错：", e);

            return ResponseEntity.badRequest().body("暂时无法处理您的请求，请稍后重试");
        }
    }
}
