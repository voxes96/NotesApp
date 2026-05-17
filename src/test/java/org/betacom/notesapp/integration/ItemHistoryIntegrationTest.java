package org.betacom.notesapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.betacom.notesapp.dto.item.CreateItemRequest;
import org.betacom.notesapp.dto.item.ItemResponse;
import org.betacom.notesapp.dto.item.UpdateItemRequest;
import org.betacom.notesapp.dto.login.LoginRequest;
import org.betacom.notesapp.dto.login.LoginResponse;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class ItemHistoryIntegrationTest extends BaseIntegrationTest {

    public static final String TESTUSER = "testuser";
    public static final String USER_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws Exception {
        // Create test user
        if (!userRepository.existsByLogin(TESTUSER)) {
            User user = new User();
            user.setLogin(TESTUSER);
            user.setPassword(passwordEncoder.encode(USER_PASSWORD));
            User savedUser = userRepository.save(user);
        }
    }

    @Test
    void shouldTrackItemHistoryWithEnvers() throws Exception {
        LoginRequest loginRequest = new LoginRequest(
                TESTUSER,
                USER_PASSWORD
        );
        MvcResult loginResult = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();
        String responseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseBody, LoginResponse.class);
        String jwtToken = loginResponse.token();

        // Step 1: Create item
        CreateItemRequest createRequest = new CreateItemRequest(
                "Initial Title",
                "Initial Content"
        );

        MvcResult createResult = mockMvc.perform(post("/items")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Initial Title"))
                .andExpect(jsonPath("$.content").value("Initial Content"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        responseBody = createResult.getResponse().getContentAsString();
        ItemResponse itemResponse = objectMapper.readValue(responseBody, ItemResponse.class);
        UUID itemId = itemResponse.id();

        // Wait a bit to ensure different timestamps
        Thread.sleep(100);

        // Step 2: First edit
        UpdateItemRequest updateRequest1 = new UpdateItemRequest(
                "Updated Title 1",
                "Updated Content 1",
                0
        );

        mockMvc.perform(patch("/items/" + itemId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title 1"))
                .andExpect(jsonPath("$.content").value("Updated Content 1"))
                .andExpect(jsonPath("$.version").value(1));

        Thread.sleep(100);

        // Step 3: Second edit
        UpdateItemRequest updateRequest2 = new UpdateItemRequest(
                "Updated Title 2",
                "Updated Content 2",
                1
        );

        mockMvc.perform(patch("/items/" + itemId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title 2"))
                .andExpect(jsonPath("$.content").value("Updated Content 2"))
                .andExpect(jsonPath("$.version").value(2));

        Thread.sleep(100);

        // Step 4: Third edit
        UpdateItemRequest updateRequest3 = new UpdateItemRequest(
                "Final Title",
                "Final Content",
                2
        );

        mockMvc.perform(patch("/items/" + itemId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final Title"))
                .andExpect(jsonPath("$.content").value("Final Content"))
                .andExpect(jsonPath("$.version").value(3));

        Thread.sleep(100);

        // Step 5: Retrieve history and verify all revisions
        mockMvc.perform(get("/items/" + itemId + "/history")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))) // 1 create + 3 edits
                
                // Verify first revision (ADD - creation)
                .andExpect(jsonPath("$[0].revisionType").value("ADD"))
                .andExpect(jsonPath("$[0].title").value("Initial Title"))
                .andExpect(jsonPath("$[0].content").value("Initial Content"))
                .andExpect(jsonPath("$[0].changedBy").value(TESTUSER))
                .andExpect(jsonPath("$[0].timestamp").exists())
                
                // Verify second revision (MOD - first edit)
                .andExpect(jsonPath("$[1].revisionType").value("MOD"))
                .andExpect(jsonPath("$[1].title").value("Updated Title 1"))
                .andExpect(jsonPath("$[1].content").value("Updated Content 1"))
                .andExpect(jsonPath("$[1].changedBy").value(TESTUSER))
                .andExpect(jsonPath("$[1].timestamp").exists())
                
                // Verify third revision (MOD - second edit)
                .andExpect(jsonPath("$[2].revisionType").value("MOD"))
                .andExpect(jsonPath("$[2].title").value("Updated Title 2"))
                .andExpect(jsonPath("$[2].content").value("Updated Content 2"))
                .andExpect(jsonPath("$[2].changedBy").value(TESTUSER))
                .andExpect(jsonPath("$[2].timestamp").exists())
                
                // Verify fourth revision (MOD - third edit)
                .andExpect(jsonPath("$[3].revisionType").value("MOD"))
                .andExpect(jsonPath("$[3].title").value("Final Title"))
                .andExpect(jsonPath("$[3].content").value("Final Content"))
                .andExpect(jsonPath("$[3].changedBy").value(TESTUSER))
                .andExpect(jsonPath("$[3].timestamp").exists())
                
                // Verify revisions are ordered by revision number
                .andExpect(jsonPath("$[0].revision").value(1))
                .andExpect(jsonPath("$[1].revision").value(2))
                .andExpect(jsonPath("$[2].revision").value(3))
                .andExpect(jsonPath("$[3].revision").value(4));
    }

}
