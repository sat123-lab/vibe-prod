package com.example.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Matches WebSocket upgrade requests by raw {@code requestURI}, not servlet/MVC path.
 * Spring's {@code PathPatternRequestMatcher} often fails for STOMP endpoints because
 * the handshake is handled outside {@code DispatcherServlet}.
 */
public final class WebSocketHandshakeMatcher implements RequestMatcher {

    public static final WebSocketHandshakeMatcher INSTANCE = new WebSocketHandshakeMatcher();

    private WebSocketHandshakeMatcher() {}

    @Override
    public boolean matches(HttpServletRequest request) {
        return JwtFilter.isWebSocketHandshakeRequest(request);
    }
}
