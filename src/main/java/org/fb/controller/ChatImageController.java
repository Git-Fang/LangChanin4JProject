package org.fb.controller;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.config.EnvConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/xiaozhi")
@Slf4j
@Tag(name = "9006--阿里万相文生图测试")
public class ChatImageController {

    @Autowired
    private WanxImageModel wanxImageModel;

    @Autowired
    private EnvConf envConf;

    @Autowired
    private ChatModel chatModel;


    @GetMapping("/chatImage")
    @Operation(summary = "图生文接口测试")
    public String chatImage(String base64Data, String imageType, String prompt) throws IOException {

        // 获取图片的MIME类型
        String mimeType = "image/" + imageType;

        UserMessage userMessage = UserMessage.from(TextContent.from(prompt), ImageContent.from(base64Data, mimeType));
        ChatResponse chatResponse = chatModel.chat(userMessage);
        String text = chatResponse.aiMessage().text();
        log.info("text:{}", text);
        return text;
    }


    @GetMapping("/wanxImage")
    @Operation(summary = "阿里万相文生图简单测试1")
    public String wanxImage(String prompt) throws IOException {
        Response<Image> imageResponse = wanxImageModel.generate(prompt);
        URI imageUri = imageResponse.content().url();
        log.info("imageUri: {}", imageUri);
        return imageResponse.content().toString();
    }


    @GetMapping("/wanxImage2")
    @Operation(summary = "阿里万相文基于prompt生图测试2")
    public String wanxImage2(String prompt) throws IOException {
        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .apiKey(envConf.dashscopeApiKey)
                        .model(ImageSynthesis.Models.WANX_V1)
                        .prompt(prompt)
                        .style("<watercolor>")
                        .n(1)
                        .size("1024*1024")
                        .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();
        ImageSynthesisResult result = null;
        try {
            log.info("---sync call, please wait a moment----");
            result = imageSynthesis.call(param);
        } catch (ApiException | NoApiKeyException e){
            throw new RuntimeException(e.getMessage());
        }
        log.info("文生图结果："+JsonUtils.toJson(result));
        return JsonUtils.toJson(result);
    }



    private String getMimeType(String imagePath) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(imagePath);
            String contentType = java.nio.file.Files.probeContentType(path);
            if (contentType != null) {
                return contentType;
            }

            // 如果无法自动检测，则根据扩展名判断
            String extension = imagePath.toLowerCase();
            if (extension.endsWith(".png")) {
                return "image/png";
            } else if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (extension.endsWith(".gif")) {
                return "image/gif";
            } else if (extension.endsWith(".bmp")) {
                return "image/bmp";
            } else {
                return "image/png"; // 默认返回png类型
            }
        } catch (IOException e) {
            log.error("获取图片MIME类型失败", e);
            return "image/png"; // 出错时默认返回png类型
        }
    }
}
