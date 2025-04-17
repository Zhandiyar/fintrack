package kz.finance.security.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import kz.finance.security.config.GoogleClientConfig;
import kz.finance.security.config.JwtTokenProvider;
import kz.finance.security.dto.ApiResponse;
import kz.finance.security.dto.ForgotPasswordRequestDto;
import kz.finance.security.dto.GoogleSignInRequest;
import kz.finance.security.dto.LoginRequestDto;
import kz.finance.security.dto.RegisterRequestDto;
import kz.finance.security.dto.ResetPasswordRequestDto;
import kz.finance.security.exception.TokenException;
import kz.finance.security.model.UserEntity;
import kz.finance.security.service.EmailService;
import kz.finance.security.service.GoogleTokenVerifierService;
import kz.finance.security.service.PasswordResetService;
import kz.finance.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifierService googleVerifier;
    private final GoogleClientConfig googleClientConfig;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequestDto request) {
        userService.registerUser(
                request.username(),
                request.email(),
                request.password(),
                request.pro()
        );
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String token = jwtTokenProvider.generateToken(authentication);

        return ResponseEntity.ok(ApiResponse.success("User registered successfully", token));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String token = jwtTokenProvider.generateToken(authentication);
        return ResponseEntity.ok(ApiResponse.success("Login successful", token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        UserEntity user = userService.getByEmailOrThrow(request.email());
        var resetToken = passwordResetService.createOrUpdatePasswordResetTokenForUser(user);

        // Формируем ссылку на сброс пароля
        String resetUrl = "https://fin-track.pro/reset-password?token=" + resetToken.getToken();

        // Отправляем письмо через Resend
        String subject = "Сброс пароля";
        String text = "Здравствуйте, " + user.getUsername() + "!\n\n" +
                      "Чтобы сбросить пароль, перейдите по ссылке:\n" + resetUrl + "\n\n" +
                      "Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо.";

        emailService.sendSimpleMessage(user.getEmail(), subject, text);

        return ResponseEntity.ok(ApiResponse.success("Password reset link sent to your email"));
    }


    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        try {
            var tokenEntity = passwordResetService.validatePasswordResetToken(request.token());
            UserEntity user = tokenEntity.getUser();
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userService.save(user);
            return ResponseEntity.ok(ApiResponse.success("Password reset successful"));
        } catch (TokenException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/google-signin")
    public ResponseEntity<ApiResponse> googleSignIn(@RequestBody GoogleSignInRequest request) {
        String idToken = request.idToken();
        String platform = Optional.ofNullable(request.platform()).orElse("web").toLowerCase();

        // Выбор clientId по платформе
        String clientId = switch (platform) {
            case "android" -> googleClientConfig.getAndroidClientId();
            case "ios" -> googleClientConfig.getIosClientId();
            default -> googleClientConfig.getWebClientId();
        };

        // Верификация токена
        GoogleIdToken.Payload payload = googleVerifier.verify(idToken, clientId);

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        UserEntity user = userService.getOrCreateGoogleUser(email, name);

        String token = jwtTokenProvider.generateToken(user.getUsername());

        return ResponseEntity.ok(ApiResponse.success("Google login successful", token));
    }
}
