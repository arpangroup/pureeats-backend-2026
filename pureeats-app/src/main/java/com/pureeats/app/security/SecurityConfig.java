package com.pureeats.app.security;

import com.pureeats.user.security.JwtAuthenticationFilter;
import com.pureeats.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Coarse-grained, URL-pattern-based role gating is still the primary mechanism and covers every
 * endpoint in the app - it's deliberate that pureeats-catalog-service, pureeats-order-service etc.
 * never need a compile dependency on Spring Security to enforce "who can call what", and instead
 * only ever read {@code CurrentUserContext}/{@code @AuthenticationPrincipal} for row-level
 * ownership checks (see e.g. RestaurantService.assertOwnership).
 * <p>
 * {@code @EnableMethodSecurity} additionally turns on {@code @PreAuthorize} for modules that
 * already depend on Spring Security (currently just pureeats-user-service - see
 * {@code AdminAuditController}) as an optional second, independent layer for endpoints that want
 * it; it is not required and does not replace the URL-pattern rules above.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Carved out before the broad "/api/v1/auth/**" permitAll below: revoking
                        // every session for "the current user" only means something once a JWT
                        // identifies who that is - same pattern as the store-owner-onboarding route.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/logout-all").authenticated()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/restaurants/**",
                                "/api/v1/restaurant-categories/**",
                                "/api/v1/locations/**",
                                "/api/v1/coupons",
                                "/api/v1/pages/**",
                                "/api/v1/settings",
                                "/api/v1/promo-sliders",
                                "/api/v1/languages",
                                "/api/v1/payment-gateways",
                                "/api/v1/ratings/restaurants/**",
                                "/api/v1/ratings/drivers/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Onboarding: any authenticated user may register their first restaurant, which is
                        // what grants the STORE_OWNER role (see RestaurantService.create) - so this one
                        // route must be reachable before a user actually holds that role.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/store-owner/restaurants").authenticated()
                        .requestMatchers("/api/v1/store-owner/**").hasRole("STORE_OWNER")
                        .requestMatchers("/api/v1/delivery/**").hasRole("DELIVERY")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
