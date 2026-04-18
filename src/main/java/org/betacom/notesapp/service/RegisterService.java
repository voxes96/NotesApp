package org.betacom.notesapp.service;

import org.betacom.notesapp.dto.register.RegisterRequest;
import org.betacom.notesapp.dto.register.RegisterResponse;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    RegisterService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public RegisterResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByLogin(request.getLogin())) {
            throw new IllegalArgumentException("Login already exists");
        }

        User user = new User();
        user.setLogin(request.getLogin());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getLogin(),
                savedUser.getCreatedAt()
        );
    }
}
