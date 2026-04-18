package org.betacom.notesapp.dto.register;

import java.time.LocalDateTime;
import java.util.UUID;

public class RegisterResponse {
    
    private UUID id;
    private String login;
    private LocalDateTime createdAt;
    
    public RegisterResponse(UUID id, String login, LocalDateTime createdAt) {
        this.id = id;
        this.login = login;
        this.createdAt = createdAt;
    }

    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getLogin() {
        return login;
    }
    
    public void setLogin(String login) {
        this.login = login;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
