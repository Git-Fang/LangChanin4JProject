package org.fb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = jacksonConverter.getObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        converters.add(jacksonConverter);
        
        converters.add(new SseHttpMessageConverter());
    }
    
    private static class SseHttpMessageConverter extends AbstractHttpMessageConverter<Object> {
        private final ObjectMapper objectMapper;
        
        public SseHttpMessageConverter() {
            super(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON);
            this.objectMapper = new ObjectMapper();
            this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        }
        
        @Override
        protected boolean supports(Class<?> clazz) {
            return true;
        }
        
        @Override
        protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage) {
            throw new UnsupportedOperationException("SSE read not supported");
        }
        
        @Override
        protected void writeInternal(Object object, HttpOutputMessage outputMessage) throws IOException {
            try {
                String json = objectMapper.writeValueAsString(object);
                outputMessage.getBody().write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new HttpMessageNotWritableException("Failed to write SSE message", e);
            }
        }
    }
}
