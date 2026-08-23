package com.datanest.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the CORS method list against drifting away from the routes.
 *
 * <p>This exists because it already drifted once: the allowed-methods list was written
 * when the update route was PUT, the route later moved to PATCH, and the list was not
 * updated - so browsers had every file update blocked at preflight while curl and the
 * unit tests stayed perfectly green.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private void preflightAllows(String method) throws Exception {

        mockMvc.perform(
                        options("/api/files/00000000-0000-0000-0000-000000000000")
                                .header("Origin", ORIGIN)
                                .header("Access-Control-Request-Method", method)
                                .header("Access-Control-Request-Headers", "authorization,content-type")
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().stringValues(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString(method)
                        )
                ));
    }

    @Test
    @DisplayName("preflight allows PATCH - the update route")
    void preflightAllowsPatch() throws Exception {

        preflightAllows("PATCH");
    }

    @Test
    @DisplayName("preflight allows DELETE - permanent delete")
    void preflightAllowsDelete() throws Exception {

        preflightAllows("DELETE");
    }

    @Test
    @DisplayName("preflight allows POST - upload, trash, restore")
    void preflightAllowsPost() throws Exception {

        preflightAllows("POST");
    }

    @Test
    @DisplayName("preflight allows GET - listing, sync, search")
    void preflightAllowsGet() throws Exception {

        preflightAllows("GET");
    }

    @Test
    @DisplayName("a disallowed origin is rejected")
    void disallowedOriginIsRejected() throws Exception {

        mockMvc.perform(
                        options("/api/files")
                                .header("Origin", "http://evil.example.com")
                                .header("Access-Control-Request-Method", "GET")
                )
                .andExpect(status().isForbidden());
    }
}
