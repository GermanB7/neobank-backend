package com.neobank.auth.security;

import com.neobank.observability.web.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${neobank.monitoring.prometheus-public-endpoint-enabled:false}")
    private boolean prometheusPublicEndpointEnabled;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            CorrelationIdFilter correlationIdFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                        // Public endpoints: health, documentation and auth.
                        auth.requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll();

                        if (prometheusPublicEndpointEnabled) {
                            auth.requestMatchers("/actuator/prometheus").permitAll();
                        } else {
                            auth.requestMatchers("/actuator/prometheus").hasRole("ADMIN");
                        }

                        auth.requestMatchers("/actuator/**").hasRole("ADMIN");
                        // Public authentication endpoints
                        auth.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh").permitAll();
                        auth.requestMatchers(HttpMethod.GET, "/auth/register", "/auth/login", "/auth/refresh").denyAll();
                        // Authenticated endpoints
                        auth.requestMatchers(HttpMethod.GET, "/auth/me", "/auth/sessions").authenticated();
                        auth.requestMatchers(HttpMethod.POST, "/auth/logout", "/auth/logout-all").authenticated();
                        // Admin-only endpoints
                        auth.requestMatchers("/admin/**").hasRole("ADMIN");
                        auth.requestMatchers("/reconciliation/**").hasRole("ADMIN");
                        // All other requests require authentication
                        auth.anyRequest().authenticated();
                })
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, CorrelationIdFilter.class)
                .build();
    }
}