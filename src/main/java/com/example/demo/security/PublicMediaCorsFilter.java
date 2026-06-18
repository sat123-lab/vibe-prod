package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures every public media response (including 404/403) carries CORS headers.
 *
 * <p>Without this, browsers report a CORS failure instead of the real HTTP
 * status when a file is missing — which floods the Flutter web console.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicMediaCorsFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PublicMediaRequestMatcher.isPublicMediaRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        res.setHeader("Access-Control-Max-Age", "86400");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        chain.doFilter(req, res);
    }
}
