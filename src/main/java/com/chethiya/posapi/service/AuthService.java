package com.chethiya.posapi.service;

import com.chethiya.posapi.domain.User;
import com.chethiya.posapi.dto.AuthDtos.AuthResponse;
import com.chethiya.posapi.dto.AuthDtos.LoginRequest;
import com.chethiya.posapi.dto.AuthDtos.RegisterRequest;
import com.chethiya.posapi.exception.DuplicateResourceException;
import com.chethiya.posapi.repository.UserRepository;
import com.chethiya.posapi.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));

        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow();

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), user.getUsername(), user.getRole());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists: " + username);
        }

        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), user.getUsername(), user.getRole());
    }
}
