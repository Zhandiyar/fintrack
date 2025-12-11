package kz.finance.security.dto;

public record AppleUserInfo(
        String sub,              // Apple user ID
        String email,            // Email (may be null)
        Boolean emailVerified,   // Email verification status
        String firstName,        // First name (from fullName)
        String lastName          // Last name (from fullName)
) {
}


