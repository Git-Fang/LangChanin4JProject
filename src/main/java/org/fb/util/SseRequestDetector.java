package org.fb.util;

import org.springframework.http.MediaType;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

public final class SseRequestDetector {

    private static final List<MediaType> SSE_COMPATIBLE_TYPES = List.of(
            MediaType.TEXT_EVENT_STREAM,
            MediaType.APPLICATION_JSON
    );

    private SseRequestDetector() {
    }

    public static boolean isSseRequest(WebRequest request) {
        if (request == null) {
            return false;
        }

        String contentType = request.getHeader("Content-Type");
        String accept = request.getHeader("Accept");

        if (isSseCompatible(contentType)) {
            return true;
        }
        if (isSseCompatible(accept)) {
            return true;
        }

        return false;
    }

    public static boolean isSseCompatible(String mediaTypeString) {
        if (mediaTypeString == null || mediaTypeString.isEmpty()) {
            return false;
        }

        return SSE_COMPATIBLE_TYPES.stream()
                .anyMatch(type -> mediaTypeString.contains(type.toString()));
    }

    public static MediaType getResponseContentType(WebRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return MediaType.TEXT_EVENT_STREAM;
        }
        return MediaType.APPLICATION_JSON;
    }
}
