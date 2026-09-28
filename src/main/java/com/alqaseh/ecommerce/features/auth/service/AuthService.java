package com.alqaseh.ecommerce.features.auth.service;

import com.alqaseh.ecommerce.features.auth.dto.request.LoginRequest;
import com.alqaseh.ecommerce.features.auth.dto.response.LoginResponse;
import com.alqaseh.ecommerce.shared.result.Result;

public interface AuthService {

    Result<LoginResponse> login(LoginRequest request);
}
