package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.auth.LoginRequest;
import com.dikoin.manuals.dtos.auth.LoginResponse;
import com.dikoin.manuals.entidades.AppUser;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.repositorios.AppUserRepository;
import com.dikoin.manuals.servicios.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException("Credenciales incorrectas"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException("Credenciales incorrectas");
        }

        String role = user.getRole().getName().name();
        return new LoginResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                role,
                "demo-token-" + role.toLowerCase() + "-" + user.getId()
        );
    }
}
