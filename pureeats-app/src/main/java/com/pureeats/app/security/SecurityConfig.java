package com.pureeats.app.security;

import com.pureeats.user.security.JwtAuthenticationFilter;
import com.pureeats.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Coarse-grained, URL-pattern-based role gating. This is the ONLY place authorization rules
 * are declared, which is deliberate: it means pureeats-catalog-service, pureeats-order-service
 * etc. never need a compile dependency on Spring Security to enforce "who can call what" - they
 * only ever read {@code CurrentUserContext}/{@code @AuthenticationPrincipal} for row-level
 * ownership checks (see e.g. RestaurantService.assertOwnership).
 */
@Configuration
@EnableWebSecurity
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
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Onboarding: any authenticated user may register their first restaurant, which is
                        // what grants the RESTAURANT_OWNER role (see RestaurantService.create) - so this one
                        // route must be reachable before a user actually holds that role.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/store-owner/restaurants").authenticated()
                        .requestMatchers("/api/v1/store-owner/**").hasRole("RESTAURANT_OWNER")
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
