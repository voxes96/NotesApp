package org.betacom.notesapp.config.limiter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit.login")
public class RateLimitProperties {
    
    private int capacity = 5;
    private int refillTokens = 5;
    private int refillMinutes = 1;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(int refillTokens) {
        this.refillTokens = refillTokens;
    }

    public int getRefillMinutes() {
        return refillMinutes;
    }

    public void setRefillMinutes(int refillMinutes) {
        this.refillMinutes = refillMinutes;
    }
}
