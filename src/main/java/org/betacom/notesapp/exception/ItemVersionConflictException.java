package org.betacom.notesapp.exception;

public class ItemVersionConflictException extends RuntimeException {
    private final Integer currentVersion;
    
    public ItemVersionConflictException(String message, Integer currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }
    
    public Integer getCurrentVersion() {
        return currentVersion;
    }
}
