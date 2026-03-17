package com.neobank.auth.service;

import com.neobank.auth.api.dto.AuthResponse;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.api.dto.UserProfileResponse;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.auth.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Retrieves the profile of the currently authenticated user.
     * The authentication principal must already be loaded from JWT.
     *
     * @param authentication the Spring Security Authentication object containing the current user
     * @return UserProfileResponse with user details and assigned roles
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Authentication authentication) {
        String email = authentication.getName();

        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        Set<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet())
                : Set.of();

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                roles,
                user.isEnabled()
        );
    }
}