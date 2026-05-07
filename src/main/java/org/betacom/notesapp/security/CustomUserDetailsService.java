package org.betacom.notesapp.security;

import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userRepository.findByLogin(login)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + login));
        
        return org.springframework.security.core.userdetails.User
            .withUsername(user.getLogin())
            .password(user.getPassword())
            .authorities(new ArrayList<>())
            .build();
    }
}
