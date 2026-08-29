package com.pureeats.app.filter;

import com.pureeats.domain.common.RequestIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every request gets a correlation id - reused from the inbound {@code X-Request-ID} header if the
 * client sent one, generated otherwise. Populates {@link RequestIdContext} (read by
 * {@code GlobalExceptionHandler} and the auth audit/notification logging) and echoes it back on
 * the response so client-side logs and server-side logs can be joined on the same value.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            RequestIdContext.set(requestId);
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            RequestIdContext.clear();
        }
    }
}
