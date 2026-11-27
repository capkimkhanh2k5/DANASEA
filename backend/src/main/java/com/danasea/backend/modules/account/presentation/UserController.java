package com.danasea.backend.modules.account.presentation;

import com.danasea.backend.modules.account.application.usecases.ChangePasswordUseCase;
import com.danasea.backend.modules.account.application.usecases.GetMyProfileUseCase;
import com.danasea.backend.modules.account.application.usecases.UpdateProfileUseCase;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.presentation.dtos.ChangePasswordRequest;
import com.danasea.backend.modules.account.presentation.dtos.UpdateProfileRequest;
import com.danasea.backend.modules.account.presentation.dtos.UserProfileResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        User user = getMyProfileUseCase.execute(principal.getName());
        return ResponseEntity.ok(mapToResponse(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        User updatedUser = updateProfileUseCase.execute(
                principal.getName(),
                request.fullName(),
                request.avatarUrl(),
                request.locale()
        );
        return ResponseEntity.ok(mapToResponse(updatedUser));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        changePasswordUseCase.execute(
                principal.getName(),
                request.currentPassword(),
                request.newPassword()
        );
        return ResponseEntity.ok().build();
    }

    private UserProfileResponse mapToResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getLocale(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getIsEmailVerified()
        );
    }
}
