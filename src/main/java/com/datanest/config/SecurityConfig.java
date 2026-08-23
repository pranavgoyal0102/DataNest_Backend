package com.datanest.config;

import com.datanest.security.FirebaseAuthenticationFilter;
import com.datanest.security.SecurityErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final SecurityErrorWriter errorWriter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // Stateless bearer-token API: no cookies, so no CSRF surface.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(
                                        HttpMethod.OPTIONS, "/**"
                                ).permitAll()
                                .requestMatchers(
                                        "/actuator/health"
                                ).permitAll()
                                .anyRequest().authenticated()
                )
                .addFilterBefore(
                        firebaseAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(handling ->
                        handling
                                .authenticationEntryPoint(
                                        (request, response, ex) ->
                                                errorWriter.write(
                                                        response,
                                                        HttpStatus.UNAUTHORIZED.value(),
                                                        "Authentication required"
                                                )
                                )
                                .accessDeniedHandler(
                                        (request, response, ex) ->
                                                errorWriter.write(
                                                        response,
                                                        HttpStatus.FORBIDDEN.value(),
                                                        "Access denied"
                                                )
                                )
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                allowedOrigins
        );

        // PATCH must be here: the update route is @PatchMapping, and PATCH is not a
        // CORS-safelisted method, so a browser preflight that does not see it listed
        // blocks the request before it ever reaches the server. PUT is deliberately
        // absent - no route serves it since the update endpoint moved to PATCH.
        configuration.setAllowedMethods(
                List.of(
                        "GET", "POST", "PATCH", "DELETE", "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
