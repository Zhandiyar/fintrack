package kz.finance.fintrack.controller;


import jakarta.validation.Valid;
import kz.finance.fintrack.dto.ChangePasswordRequest;
import kz.finance.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserService userService;

    @PutMapping("/change-password")
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(request.currentPassword(), request.newPassword());
    }

    @DeleteMapping("/delete-account")
    public void deleteAccount() {
        userService.deleteUserAndAllData();
    }
}
