package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.auth.LoginRequest;
import com.dikoin.manuals.dtos.auth.LoginResponse;
import com.dikoin.manuals.servicios.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
