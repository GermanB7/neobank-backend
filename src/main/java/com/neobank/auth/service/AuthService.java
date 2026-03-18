package com.neobank.auth.service;

import com.neobank.audit.service.AuditService;
import com.neobank.auth.api.dto.AuthResponse;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.LogoutRequest;
import com.neobank.auth.api.dto.RefreshTokenRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.api.dto.SessionResponse;
import com.neobank.auth.api.dto.UserProfileResponse;
import com.neobank.auth.domain.RefreshSessionEntity;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.domain.events.UserRegisteredEvent;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.auth.security.JwtService;
import com.neobank.shared.domain.DomainEventPublisher;
import com.neobank.shared.metrics.ObservabilityMetrics;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final ObservabilityMetrics observabilityMetrics;
    private final DomainEventPublisher domainEventPublisher;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            ObservabilityMetrics observabilityMetrics,
            DomainEventPublisher domainEventPublisher
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.observabilityMetrics = observabilityMetrics;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    @CacheEvict(cacheNames = "user_by_email", key = "#req.email().trim().toLowerCase()")
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

        domainEventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, TOKEN_TYPE, jwtService.getExpirationSeconds());
    }

    public AuthResponse login(LoginRequest req) {
        return login(req, null, null);
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public AuthResponse login(LoginRequest req, String ipAddress, String deviceInfo) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.password())
            );
        } catch (AuthenticationException ex) {
            observabilityMetrics.incrementLoginFailure();
            auditService.recordEvent(
                    "LOGIN_FAILURE",
                    null,
                    email,
                    "AUTH",
                    null,
                    "FAILURE",
                    "Authentication failed"
            );
            throw ex;
        }

        observabilityMetrics.incrementLoginSuccess();
        UserEntity user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        auditService.recordEvent(
                "LOGIN_SUCCESS",
                user != null ? user.getId() : null,
                email,
                "AUTH",
                null,
                "SUCCESS",
                "Authentication succeeded"
        );

        String accessToken = jwtService.generateToken(email);
        String refreshToken = null;
        if (user != null) {
            refreshToken = refreshTokenService.issue(user, ipAddress, deviceInfo).rawToken();
        }

        return new AuthResponse(accessToken, refreshToken, TOKEN_TYPE, jwtService.getExpirationSeconds());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String deviceInfo) {
        try {
            RefreshTokenService.RotatedRefreshToken rotated =
                    refreshTokenService.rotate(request.refreshToken(), ipAddress, deviceInfo);
            String email = rotated.user().getEmail();
            String accessToken = jwtService.generateToken(email);

            auditService.recordEvent(
                    "REFRESH_SUCCESS",
                    rotated.user().getId(),
                    email,
                    "AUTH",
                    null,
                    "SUCCESS",
                    "Refresh token rotated"
            );

            return new AuthResponse(accessToken, rotated.rawToken(), TOKEN_TYPE, jwtService.getExpirationSeconds());
        } catch (ResponseStatusException ex) {
            auditService.recordEvent(
                    "REFRESH_REJECTED",
                    null,
                    null,
                    "AUTH",
                    null,
                    "FAILURE",
                    ex.getReason()
            );
            throw ex;
        }
    }

    @Transactional
    public void logout(LogoutRequest request, Authentication authentication) {
        boolean revoked = refreshTokenService.revoke(request.refreshToken());

        String actorEmail = authentication != null ? authentication.getName() : null;
        UserEntity actor = actorEmail != null ? userRepository.findByEmailIgnoreCase(actorEmail).orElse(null) : null;

        auditService.recordEvent(
                "LOGOUT",
                actor != null ? actor.getId() : null,
                actorEmail,
                "AUTH",
                null,
                revoked ? "SUCCESS" : "FAILURE",
                revoked ? "Refresh session revoked" : "Refresh token not found or already revoked"
        );
    }

    @Transactional
    public int logoutAll(Authentication authentication) {
        String email = authentication.getName();
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        int revokedSessions = refreshTokenService.revokeAllForUser(user.getId());
        auditService.recordEvent(
                "LOGOUT_ALL",
                user.getId(),
                email,
                "AUTH",
                null,
                "SUCCESS",
                "Revoked sessions=" + revokedSessions
        );
        return revokedSessions;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(Authentication authentication) {
        String email = authentication.getName();
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        return refreshTokenService.listActiveSessions(user.getId()).stream()
                .map(this::toSessionResponse)
                .toList();
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

    /**
     * Cached user lookup by email.
     * Results are cached for 30 minutes.
     * Cache is invalidated on user registration.
     *
     * This is safe to cache because:
     * - Email is the stable, read-only user identifier
     * - User modifications are not frequent in this workflow
     * - Cache invalidation is explicit on registration
     */
    @Cacheable(cacheNames = "user_by_email", key = "#email.toLowerCase()")
    public Optional<UserEntity> getUserByEmailCached(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    private SessionResponse toSessionResponse(RefreshSessionEntity session) {
        return new SessionResponse(
                session.getId(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.getDeviceInfo(),
                session.getIpAddress()
        );
    }
}

