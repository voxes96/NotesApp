package org.betacom.notesapp.service;

import org.betacom.notesapp.dto.login.LoginRequest;
import org.betacom.notesapp.dto.login.LoginResponse;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.UserRepository;
import org.betacom.notesapp.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    @Autowired
    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLogin(request.login())
                .orElseThrow(() -> new BadCredentialsException("Invalid login or password"));
        
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid login or password");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getLogin());
        
        return new LoginResponse(token, jwtUtil.getExpirationTime());
    }
}
