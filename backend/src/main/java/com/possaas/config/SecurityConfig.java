package com.possaas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public TenantHeaderFilter tenantHeaderFilter() {
        return new TenantHeaderFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   TenantHeaderFilter tenantHeaderFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":401,\"message\":\"No autenticado\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":403,\"message\":\"Acceso denegado\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/public/**", "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/onboarding/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/billing/**", "/api/v1/accounting/**").hasRole("ADMIN")
                        // Compatibilidad: endpoints tenant/legacy (sync, POS, admin por headers) permanecen accesibles;
                        // los controles finos se mantienen en controladores/servicios existentes.
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantHeaderFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
