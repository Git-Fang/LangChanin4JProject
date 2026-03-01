package org.fb.bean;

import java.util.List;

public class ChatForm {

   private Long memoryId;

   private String message;

   private List<String> extractedTexts;

   /**
    * 使用的模型ID (deepseek, qwen, ollama, kimi)
    * 如果为空则使用默认模型
    */
   private String modelId;

   public Long getMemoryId() {
       return memoryId;
   }

   public void setMemoryId(Long memoryId) {
       this.memoryId = memoryId;
   }

   public String getMessage() {
       return message;
   }

   public void setMessage(String message) {
       this.message = message;
   }

   public List<String> getExtractedTexts() {
       return extractedTexts;
   }

   public void setExtractedTexts(List<String> extractedTexts) {
        this.extractedTexts = extractedTexts;
   }

   public String getModelId() {
        return modelId;
   }

   public void setModelId(String modelId) {
        this.modelId = modelId;
   }
}
