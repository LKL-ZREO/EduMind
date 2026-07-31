package com.firedemo.edumind.platform.web;

import com.firedemo.edumind.shared.exception.ErrorCode;
import com.firedemo.edumind.shared.result.Result;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Ensures legacy Result.error responses carry a matching HTTP transport status. */
@RestControllerAdvice
public class ResultHttpStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Result<?> result) || result.isSuccess() || result.getCode() == null) {
            return body;
        }

        if (result.getRequestId() == null) {
            String requestId = MDC.get("requestId");
            result.setRequestId(requestId != null ? requestId : "N/A");
        }

        if (response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus() >= 400) {
            return body;
        }

        response.setStatusCode(ErrorCode.httpStatusFor(result.getCode()));
        return body;
    }
}
