package com.neobank.auth.api;

import com.neobank.auth.api.dto.AuthResponse;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.LogoutRequest;
import com.neobank.auth.api.dto.RefreshTokenRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.api.dto.SessionResponse;
import com.neobank.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController authController = new AuthController(authService);

    @Test
    void registerReturnsCreatedWhenPayloadIsValid() {
        RegisterRequest request = new RegisterRequest("alice@neobank.com", "password123");
        AuthResponse expected = new AuthResponse("token-1", null, "Bearer", 3600);

        when(authService.register(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("token-1", response.getBody().accessToken());
        assertEquals("Bearer", response.getBody().tokenType());
        assertEquals(3600, response.getBody().expiresInSeconds());
    }

    @Test
    void loginReturnsOkWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("alice@neobank.com", "password123");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        AuthResponse expected = new AuthResponse("token-2", "refresh-1", "Bearer", 3600);
        when(authService.login(request, "10.0.0.1", "JUnit")).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.login(request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("token-2", response.getBody().accessToken());
        assertEquals("refresh-1", response.getBody().refreshToken());
        assertEquals("Bearer", response.getBody().tokenType());
    }

    @Test
    void refreshReturnsOkWhenRefreshTokenIsValid() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-old");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");

        AuthResponse expected = new AuthResponse("token-3", "refresh-2", "Bearer", 3600);
        when(authService.refresh(request, "10.0.0.2", "JUnit")).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.refresh(request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("refresh-2", response.getBody().refreshToken());
    }

    @Test
    void logoutReturnsNoContent() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        Authentication authentication = mock(Authentication.class);

        ResponseEntity<Void> response = authController.logout(request, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).logout(request, authentication);
    }

    @Test
    void sessionsReturnsCurrentUserSessions() {
        Authentication authentication = mock(Authentication.class);
        SessionResponse session = new SessionResponse(UUID.randomUUID(), Instant.now(), null, Instant.now().plusSeconds(100), "JUnit", "127.0.0.1");
        when(authService.listSessions(authentication)).thenReturn(List.of(session));

        ResponseEntity<List<SessionResponse>> response = authController.sessions(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice@neobank.com", "password123");
        when(authService.register(request)).thenThrow(new IllegalArgumentException("Email already registered"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authController.register(request));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals("Email already registered", ex.getReason());
    }
}
