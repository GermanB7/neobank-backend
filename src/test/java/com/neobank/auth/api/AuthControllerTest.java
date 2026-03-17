package com.neobank.auth.api;

import com.neobank.auth.api.dto.AuthResponse;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController authController = new AuthController(authService);

    @Test
    void registerReturnsCreatedWhenPayloadIsValid() {
        RegisterRequest request = new RegisterRequest("alice@neobank.com", "password123");
        AuthResponse expected = new AuthResponse("token-1", "Bearer", 3600);

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
        AuthResponse expected = new AuthResponse("token-2", "Bearer", 3600);

        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("token-2", response.getBody().accessToken());
        assertEquals("Bearer", response.getBody().tokenType());
        assertEquals(3600, response.getBody().expiresInSeconds());
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
