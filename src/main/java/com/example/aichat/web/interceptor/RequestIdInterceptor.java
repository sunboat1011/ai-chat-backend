package com.example.aichat.web.interceptor;

import com.example.aichat.common.util.IdGenerator;
import com.example.aichat.common.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_KEY = "requestId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String requestId = IdGenerator.generateRequestId();
        RequestContext.setRequestId(requestId);
        MDC.put(REQUEST_ID_KEY, requestId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        MDC.remove(REQUEST_ID_KEY);
        RequestContext.clear();
    }
}
