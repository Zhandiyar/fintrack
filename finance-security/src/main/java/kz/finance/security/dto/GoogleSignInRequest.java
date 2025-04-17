package kz.finance.security.dto;

public record GoogleSignInRequest(
        String idToken,
        String platform
) {
}
