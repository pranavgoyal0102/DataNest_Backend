package com.datanest.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verifies the Firebase ID token on the {@code Authorization: Bearer ...} header and
 * puts the verified UID into the {@link SecurityContextHolder} as the authentication
 * principal. The UID is never taken from the request path or body.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        // Preflight carries no Authorization header, and health must stay open.
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || "/actuator/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header =
                request.getHeader(
                        HttpHeaders.AUTHORIZATION
                );

        if (header == null || !header.startsWith(BEARER_PREFIX)) {

            // No credentials offered: leave the context empty and let the
            // authentication entry point produce the 401.
            filterChain.doFilter(request, response);
            return;
        }

        String idToken =
                header.substring(
                        BEARER_PREFIX.length()
                ).trim();

        try {

            FirebaseToken token =
                    firebaseAuth.verifyIdToken(idToken);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            token.getUid(),
                            null,
                            List.of()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (FirebaseAuthException | IllegalArgumentException ex) {

            SecurityContextHolder.clearContext();

            // Never log the token itself.
            log.debug(
                    "Firebase ID token verification failed: {}",
                    ex.getMessage()
            );

            errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid or expired token"
            );

            return;
        }

        filterChain.doFilter(request, response);
    }
}
