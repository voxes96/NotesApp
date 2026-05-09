package org.betacom.notesapp.dto;

public record LoginResponse (
    String token,
    long expiresIn
) {}
