package com.neobank.auth.service;

import com.neobank.auth.api.dto.AuthResponse;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.auth.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        RoleEntity userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded in roles table"));

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEnabled(true);
        user.setRoles(Set.of(userRole));

        userRepository.save(user);

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, TOKEN_TYPE, jwtService.getExpirationSeconds());
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password())
        );

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, TOKEN_TYPE, jwtService.getExpirationSeconds());
    }
}