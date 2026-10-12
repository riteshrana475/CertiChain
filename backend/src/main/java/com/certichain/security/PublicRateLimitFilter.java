package com.certichain.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/public/") || path.startsWith("/api/shared/")) {
            String key = request.getRemoteAddr() + " " + (path.startsWith("/api/shared/") ? "share" : "public");
            if (!allow(key)) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"detail\":\"Too many requests. Wait a minute and try again.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.started > 60_000) {
                return new Window(now, 1);
            }
            current.count += 1;
            return current;
        });
        return window.count <= 60;
    }

    private static final class Window {
        private final long started;
        private int count;

        private Window(long started, int count) {
            this.started = started;
            this.count = count;
        }
    }
}
