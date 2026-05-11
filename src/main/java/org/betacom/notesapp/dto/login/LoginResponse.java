package org.betacom.notesapp.dto.login;

public record LoginResponse (
    String token,
    long expiresIn
) {}
