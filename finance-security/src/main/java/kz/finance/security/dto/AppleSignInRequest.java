package kz.finance.security.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleSignInRequest(
        @NotBlank(message = "Apple identity token is required")
        String token,
        String fullName,
        String email
) {
}


