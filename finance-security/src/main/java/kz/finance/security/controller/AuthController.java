package kz.finance.security.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import kz.finance.security.dto.ApiResponse;
import kz.finance.security.dto.AuthResponseDto;
import kz.finance.security.dto.AppleSignInRequest;
import kz.finance.security.dto.AppleSignInResponse;
import kz.finance.security.dto.ForgotPasswordRequestDto;
import kz.finance.security.dto.GoogleSignInRequest;
import kz.finance.security.dto.LoginRequestDto;
import kz.finance.security.dto.LogoutRequest;
import kz.finance.security.dto.RefreshTokenRequestDto;
import kz.finance.security.dto.RegisterRequestDto;
import kz.finance.security.dto.ResetPasswordRequestDto;
import kz.finance.security.model.UserEntity;
import kz.finance.security.service.AppleAuthService;
import kz.finance.security.service.EmailService;
import kz.finance.security.service.GoogleTokenVerifierService;
import kz.finance.security.service.PasswordResetService;
import kz.finance.security.service.RefreshTokenService;
import kz.finance.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final GoogleTokenVerifierService googleVerifier;
    private final RefreshTokenService refreshTokenService;
    private final AppleAuthService appleAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequestDto request) {
        userService.registerUser(
                request.username(),
                request.email(),
                request.password(),
                request.pro()
        );

        UserEntity user = userService.getByUsernameOrThrow(request.username());

        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(user);

        return ResponseEntity.ok(ApiResponse.success("User registered successfully", tokens));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        // достаём пользователя
        UserEntity user = userService.getByUsernameOrThrow(request.username());

        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(user);

        return ResponseEntity.ok(ApiResponse.success("Login successful", tokens));
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        UserEntity user = userService.getByEmailOrThrow(request.email());
        var resetToken = passwordResetService.createOrUpdatePasswordResetTokenForUser(user);

        // Формируем ссылку на сброс пароля
        String deeplink = "https://fin-track.pro/reset-password?token=" + resetToken.getToken();

        // Отправляем письмо
        String subject = "Сброс пароля / Password Reset";

        String text = "Здравствуйте, " + user.getUsername() + "!\n\n" +
                      "Чтобы сбросить пароль, перейдите по ссылке:\n" + deeplink + "\n\n" +
                      "Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо.\n\n" +
                      "—\n\n" +
                      "Hello, " + user.getUsername() + "!\n\n" +
                      "To reset your password, click the link below:\n" + deeplink + "\n\n" +
                      "If you didn’t request a password reset, you can safely ignore this message.";

        emailService.sendSimpleMessage(user.getEmail(), subject, text);

        return ResponseEntity.ok(ApiResponse.success("Password reset link sent to your email"));
    }

    @GetMapping("/reset-password")
    public ResponseEntity<ApiResponse> validateResetToken(@RequestParam String token) {
            passwordResetService.validatePasswordResetToken(token);
            return ResponseEntity.ok(ApiResponse.success("Token is valid"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
            passwordResetService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(ApiResponse.success("Password reset successful"));
    }

    @PostMapping("/google-signin")
    public ResponseEntity<ApiResponse> googleSignIn(@RequestBody GoogleSignInRequest request) {
        String idToken = request.idToken();

        // Верификация токена
        GoogleIdToken.Payload payload = googleVerifier.verify(idToken);

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        UserEntity user = userService.getOrCreateGoogleUser(email, name);

        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(user);

        return ResponseEntity.ok(ApiResponse.success("Google login successful", tokens));
    }

    @PostMapping("/apple")
    public ResponseEntity<ApiResponse> appleSignIn(@Valid @RequestBody AppleSignInRequest request) {
        // Validate and extract user info from Apple token
        var userInfo = appleAuthService.validateAndExtractUserInfo(
                request.token(),
                request.fullName(),
                request.email()
        );
        log.info("Apple Sign-in request for sub={}", userInfo.sub());

        // Check if user already exists by Apple ID before creating/getting
        boolean isNewUser = userService.findByAppleId(userInfo.sub()).isEmpty();

        // Get or create user
        UserEntity user = appleAuthService.getOrCreateAppleUser(userInfo);

        // Generate JWT tokens
        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(user);

        // Return response with isNewUser flag
        AppleSignInResponse response = AppleSignInResponse.of(tokens, isNewUser);

        return ResponseEntity.ok(ApiResponse.success("Apple login successful", response));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse> createGuestUser() {
        UserEntity guestUser = userService.createGuestUser();
        refreshTokenService.revokeAllUserTokens(guestUser);
        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(guestUser);
        return ResponseEntity.ok(ApiResponse.success("Guest user created successfully", tokens));
    }

    @PreAuthorize("hasRole('GUEST')")
    @PostMapping("/register-from-guest")
    public ResponseEntity<ApiResponse> registerFromGuest(@RequestBody RegisterRequestDto request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Unauthorized access");
        }

        String guestUsername = auth.getName();
        UserEntity guestUser = userService.getByUsernameOrThrow(guestUsername);

        if (!guestUser.isGuest()) {
            throw new AccessDeniedException("Only guest users can register here.");
        }

        UserEntity newUser = userService.upgradeGuestToUser(
                guestUser,
                request.username(),
                request.email(),
                request.password()
        );
        refreshTokenService.revokeAllUserTokens(newUser);
        AuthResponseDto tokens = refreshTokenService.generateTokenPairForUser(newUser);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        AuthResponseDto tokens = refreshTokenService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(@RequestBody LogoutRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Пользователь не авторизован в принципе
        if (auth == null || !auth.isAuthenticated() ||
            "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(ApiResponse.success("Already logged out"));
        }

        String username = auth.getName();
        UserEntity user = userService.getByUsernameOrThrow(username);

        refreshTokenService.revokeToken(request.refreshToken(), user);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/logout/all")
    public ResponseEntity<ApiResponse> logoutAllDevices() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() ||
            "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(ApiResponse.success("Already logged out"));
        }

        String username = auth.getName();
        UserEntity user = userService.getByUsernameOrThrow(username);

        refreshTokenService.revokeAllUserTokens(user);

        return ResponseEntity.ok(ApiResponse.success("Logged out from all devices"));
    }
}
