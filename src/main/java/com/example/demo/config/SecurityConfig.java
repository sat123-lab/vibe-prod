package com.example.demo.config;

import com.example.demo.security.AdminAccessGuard;
import com.example.demo.security.JwtFilter;
import com.example.demo.security.RateLimitFilter;
import com.example.demo.security.SecureHeadersFilter;
import com.example.demo.security.SecurityProperties;
import com.example.demo.security.PublicMediaRequestMatcher;
import com.example.demo.security.WebSocketHandshakeMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecureHeadersFilter secureHeadersFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AdminAccessGuard adminAccessGuard;
    private final SecurityProperties securityProperties;

    /**
     * Legacy disk uploads — still served for older posts; new images use MySQL BLOB.
     */
    @Bean
    public WebSecurityCustomizer publicUploadsCustomizer() {
        return web -> web.ignoring().requestMatchers(
                "/uploads",
                "/uploads/**"
        );
    }

    @Bean
    @Order(0)
    public SecurityFilterChain publicMediaFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PublicMediaRequestMatcher.INSTANCE)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // STOMP/SockJS handshake — JWT validated on STOMP CONNECT frame.
                        .requestMatchers(WebSocketHandshakeMatcher.INSTANCE).permitAll()
                        // Static uploads + DB-served post images — public read.
                        .requestMatchers(PublicMediaRequestMatcher.INSTANCE).permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/api/auth/**",
                                "/security/refresh",
                                "/upload/**",
                                // Feed & read-only post APIs (both legacy /posts and /api/posts paths)
                                "/posts/feed",
                                "/posts/feed/page",
                                "/posts/*/image",
                                "/posts/user/*/count",
                                "/api/posts/feed",
                                "/api/posts/feed/page",
                                "/api/posts/*/image",
                                "/api/posts/user/*/count",
                                "/ads/active",
                                "/actuator/health",
                                "/actuator/info",
                                "/referrals/clicks",
                                "/referrals/resolve"
                        ).permitAll()
                        // Single-post read — numeric id only (avoids shadowing /posts/feed etc.)
                        .requestMatchers("/posts/{postId:[0-9]+}").permitAll()
                        .requestMatchers("/api/posts/{postId:[0-9]+}").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Authentication required\"}");
                }))
                .addFilterBefore(secureHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminAccessGuard, JwtFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * CORS for Flutter Web (browser) and dev tools. Mobile apps do not send Origin
     * headers and are unaffected. Set {@code app.security.cors.allowed-origins} in
     * production to your web app URL(s), comma-separated.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = securityProperties.getCors().getAllowedOrigins();

        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            // Flutter Web + local dev + Render preview deploys
            config.setAllowedOriginPatterns(List.of(
                    "*",
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "https://*.onrender.com"
            ));
            config.setAllowCredentials(false);
        } else {
            // Explicit prod origins plus local Flutter Web / Render dev builds.
            config.setAllowedOriginPatterns(Arrays.asList(origins.split("\\s*,\\s*")));
            config.addAllowedOriginPattern("http://localhost:*");
            config.addAllowedOriginPattern("http://127.0.0.1:*");
            config.addAllowedOriginPattern("https://*.onrender.com");
            config.setAllowCredentials(true);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "X-Access-Token",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Device-Id",
                "*"
        ));
        config.setExposedHeaders(List.of(
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "Retry-After",
                "Content-Type",
                "Content-Length"
        ));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
