package org.betacom.notesapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.betacom.notesapp.config.limiter.RateLimitProperties;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class RateLimitIntegrationTest extends BaseIntegrationTest {

    public static final String RATELIMITUSER = "ratelimituser";
    public static final String USER_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    private static final String TEST_IP = "192.168.1.100";

    @BeforeEach
    void setUp() {
        // Create test user for login attempts if not exists
        if (!userRepository.existsByLogin(RATELIMITUSER)) {
            User user = new User();
            user.setLogin(RATELIMITUSER);
            user.setPassword(passwordEncoder.encode(USER_PASSWORD));
            userRepository.save(user);
        }
    }

    @Test
    void shouldEnforceRateLimitOn6thLoginRequest() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("login", RATELIMITUSER);
        loginRequest.put("password", USER_PASSWORD);

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        // First 5 requests should succeed
        for (int i = 0 ; i < rateLimitProperties.getCapacity() ; i++) {
            MvcResult result = mockMvc.perform(post("/login")
                            .header("X-Forwarded-For", TEST_IP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().is2xxSuccessful()) // Should not be rate limited
                    .andReturn();

            int status = result.getResponse().getStatus();
            assertTrue(status == 200 || status == 401, 
                    "Request " + i + " should return 200 or 401, but got: " + status);

            // Small delay to ensure requests are processed sequentially
            Thread.sleep(10);
        }

        // 6th request should be rate limited
        MvcResult rateLimitedResult = mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests()) // HTTP 429
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        // Verify Retry-After header contains a valid number
        String retryAfter = rateLimitedResult.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfter, "Retry-After header should be present");
        
        int retryAfterSeconds = Integer.parseInt(retryAfter);
        assertTrue(retryAfterSeconds > 0 && retryAfterSeconds <= rateLimitProperties.getRefillMinutes() * 60,
                "Retry-After should be between 1 and 60 seconds, got: " + retryAfterSeconds);
    }

    @Test
    void shouldIsolateRateLimitsByIP() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("login", RATELIMITUSER);
        loginRequest.put("password", USER_PASSWORD);

        String requestBody = objectMapper.writeValueAsString(loginRequest);

        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        // Make 5 requests from IP1
        for (int i = 0 ; i < rateLimitProperties.getCapacity() ; i++) {
            mockMvc.perform(post("/login")
                            .header("X-Forwarded-For", ip1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().is2xxSuccessful());
            Thread.sleep(10);
        }

        // 6th request from IP1 should be rate limited
        mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", ip1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Request from IP2 should still work (different bucket)
        mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", ip2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void shouldAllowRequestsAfterRateLimitExpires() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("login", RATELIMITUSER);
        loginRequest.put("password", USER_PASSWORD);

        String requestBody = objectMapper.writeValueAsString(loginRequest);
        String testIp = "192.168.1.50";

        // Exhaust rate limit (5 requests)
        for (int i = 0 ; i < rateLimitProperties.getCapacity() ; i++) {
            mockMvc.perform(post("/login")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().is2xxSuccessful());
            Thread.sleep(10);
        }

        // 6th request should be blocked
        MvcResult blockedResult = mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        String retryAfter = blockedResult.getResponse().getHeader("Retry-After");
        int waitSeconds = Integer.parseInt(retryAfter);

        // Wait for rate limit to expire (add 1 second buffer)
        Thread.sleep((waitSeconds + 1) * 1000);

        // Request should now succeed (or at least not be rate limited)
        mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void shouldIncludeRemainingRequestsHeader() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("login", RATELIMITUSER);
        loginRequest.put("password", USER_PASSWORD);

        String requestBody = objectMapper.writeValueAsString(loginRequest);
        String testIp = "192.168.1.99";

        // First request should have remaining tokens
        MvcResult firstResult = mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().exists("X-Rate-Limit-Remaining"))
                .andReturn();

        String remaining = firstResult.getResponse().getHeader("X-Rate-Limit-Remaining");
        assertNotNull(remaining, "X-Rate-Limit-Remaining header should be present");
        
        int remainingCount = Integer.parseInt(remaining);
        assertTrue(remainingCount >= 0 && remainingCount < 5, 
                "After first request, remaining should be less than 5, got: " + remainingCount);
    }
}
