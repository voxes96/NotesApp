package org.betacom.notesapp.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateItemRequest {

    private String title;
    
    private String content;
    
    @NotNull(message = "Version is required")
    private Integer version;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
