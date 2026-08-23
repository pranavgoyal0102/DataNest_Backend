package com.datanest.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;

    /**
     * Location of the service-account JSON, e.g. {@code file:/etc/secrets/firebase.json}
     * or {@code classpath:firebase.json}. When blank, Application Default Credentials
     * are used (GOOGLE_APPLICATION_CREDENTIALS).
     */
    @Value("${firebase.credentials:}")
    private String credentialsLocation;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions options =
                FirebaseOptions.builder()
                        .setCredentials(
                                loadCredentials()
                        )
                        .build();

        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseAuth firebaseAuth(
            FirebaseApp firebaseApp
    ) {

        return FirebaseAuth.getInstance(firebaseApp);
    }

    private GoogleCredentials loadCredentials() throws IOException {

        if (!StringUtils.hasText(credentialsLocation)) {
            return GoogleCredentials.getApplicationDefault();
        }

        Resource resource =
                resourceLoader.getResource(
                        credentialsLocation
                );

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "firebase.credentials points at a missing resource: "
                            + credentialsLocation
            );
        }

        try (InputStream in = resource.getInputStream()) {
            return GoogleCredentials.fromStream(in);
        }
    }
}
