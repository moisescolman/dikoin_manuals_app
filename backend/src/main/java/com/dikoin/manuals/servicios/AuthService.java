package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.auth.LoginRequest;
import com.dikoin.manuals.dtos.auth.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
