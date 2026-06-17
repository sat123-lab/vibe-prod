package com.example.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Matches public static media by raw {@code requestURI}.
 *
 * <p>Spring Security's string/PathPattern matchers often fail for files served by
 * {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler} because
 * those requests bypass {@code DispatcherServlet} path parsing.</p>
 */
public final class PublicMediaRequestMatcher implements RequestMatcher {

    public static final PublicMediaRequestMatcher INSTANCE = new PublicMediaRequestMatcher();

    private PublicMediaRequestMatcher() {}

    public static boolean isPublicMediaRequest(HttpServletRequest request) {
        String path = normalizedPath(request);
        if (path == null) {
            return false;
        }
        return path.startsWith("/uploads/")
                || path.equals("/uploads")
                || path.startsWith("/media/serve/")
                || path.startsWith("/files/");
    }

    static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return isPublicMediaRequest(request);
    }
}
