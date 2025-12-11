package kz.finance.security.dto;

public record AppleSignInResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser
) {
    public static AppleSignInResponse of(AuthResponseDto authResponse, boolean isNewUser) {
        return new AppleSignInResponse(
                authResponse.accessToken(),
                authResponse.refreshToken(),
                authResponse.tokenType(),
                authResponse.expiresIn(),
                isNewUser
        );
    }
}


