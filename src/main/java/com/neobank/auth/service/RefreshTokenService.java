package com.neobank.auth.service;

import com.neobank.auth.domain.RefreshSessionEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RefreshSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final long refreshTokenTtlSeconds;
    private final String refreshTokenPepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshSessionRepository refreshSessionRepository,
            @Value("${security.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds,
            @Value("${security.jwt.refresh-token-pepper:CHANGE_ME_REFRESH_TOKEN_PEPPER}") String refreshTokenPepper
    ) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.refreshTokenPepper = refreshTokenPepper;
    }

    @Transactional
    public IssuedRefreshToken issue(UserEntity user, String ipAddress, String deviceInfo) {
        String rawToken = generateRawToken();
        RefreshSessionEntity session = new RefreshSessionEntity();
        session.setUser(user);
        session.setTokenHash(hash(rawToken));
        session.setTokenFamilyId(UUID.randomUUID());
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtlSeconds));
        session.setDeviceInfo(truncate(deviceInfo, 255));
        session.setIpAddress(truncate(ipAddress, 64));
        RefreshSessionEntity saved = refreshSessionRepository.save(session);
        return new IssuedRefreshToken(rawToken, saved);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RotatedRefreshToken rotate(String rawToken, String ipAddress, String deviceInfo) {
        String tokenHash = hash(rawToken);
        RefreshSessionEntity current = refreshSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> unauthorized("Invalid refresh token"));

        Instant now = Instant.now();
        if (current.isRevoked() || current.getReplacedBySessionId() != null) {
            refreshSessionRepository.revokeAllActiveByUserId(current.getUser().getId(), now);
            throw unauthorized("Refresh token reuse detected");
        }

        if (current.getExpiresAt().isBefore(now)) {
            current.setRevoked(true);
            current.setRevokedAt(now);
            current.setLastUsedAt(now);
            refreshSessionRepository.save(current);
            throw unauthorized("Refresh token expired");
        }

        String newRawToken = generateRawToken();
        RefreshSessionEntity replacement = new RefreshSessionEntity();
        replacement.setUser(current.getUser());
        replacement.setTokenHash(hash(newRawToken));
        replacement.setTokenFamilyId(current.getTokenFamilyId());
        replacement.setRevoked(false);
        replacement.setExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
        replacement.setDeviceInfo(truncate(deviceInfo, 255));
        replacement.setIpAddress(truncate(ipAddress, 64));
        RefreshSessionEntity savedReplacement = refreshSessionRepository.save(replacement);

        current.setRevoked(true);
        current.setRevokedAt(now);
        current.setLastUsedAt(now);
        current.setReplacedBySessionId(savedReplacement.getId());
        refreshSessionRepository.save(current);

        return new RotatedRefreshToken(newRawToken, current.getUser());
    }

    @Transactional
    public boolean revoke(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshSessionEntity session = refreshSessionRepository.findByTokenHash(tokenHash).orElse(null);
        if (session == null || session.isRevoked()) {
            return false;
        }

        session.setRevoked(true);
        session.setRevokedAt(Instant.now());
        session.setLastUsedAt(Instant.now());
        refreshSessionRepository.save(session);
        return true;
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return refreshSessionRepository.revokeAllActiveByUserId(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<RefreshSessionEntity> listActiveSessions(UUID userId) {
        return refreshSessionRepository.findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "refresh token is required");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(refreshTokenPepper.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public record IssuedRefreshToken(String rawToken, RefreshSessionEntity session) {}

    public record RotatedRefreshToken(String rawToken, UserEntity user) {}
}
