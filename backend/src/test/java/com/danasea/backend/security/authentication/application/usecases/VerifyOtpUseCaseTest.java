package com.danasea.backend.security.authentication.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.security.authentication.application.ports.OtpStorePort;
import com.danasea.backend.security.authentication.domain.exceptions.OtpExpiredException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpInvalidException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpMaxAttemptsExceededException;
import com.danasea.backend.security.authentication.domain.models.OtpDetails;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyOtpUseCaseTest {

    @Mock
    private AccountInternalApi accountInternalApi;

    @Mock
    private OtpStorePort otpStorePort;

    private VerifyOtpUseCase verifyOtpUseCase;

    @BeforeEach
    void setUp() {
        verifyOtpUseCase = new VerifyOtpUseCase(accountInternalApi, otpStorePort);
    }

    @Test
    void execute_OtpNotFoundOrExpired_ThrowsOtpExpiredException() {
        String email = "test@example.com";
        String rawOtp = "123456";

        when(otpStorePort.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyOtpUseCase.execute(email, rawOtp))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void execute_MaxAttemptsExceeded_DeletesOtpAndThrowsException() {
        String email = "test@example.com";
        String rawOtp = "123456";

        OtpDetails otpDetails = new OtpDetails("hash", 5, OffsetDateTime.now());
        when(otpStorePort.findByEmail(email)).thenReturn(Optional.of(otpDetails));

        assertThatThrownBy(() -> verifyOtpUseCase.execute(email, rawOtp))
                .isInstanceOf(OtpMaxAttemptsExceededException.class);

        verify(otpStorePort).delete(email);
    }

    @Test
    void execute_IncorrectOtp_IncrementsAttemptsAndThrowsException() {
        String email = "test@example.com";
        String rawOtp = "123456";
        String correctHash = "different_hash";

        OtpDetails otpDetails = new OtpDetails(correctHash, 2, OffsetDateTime.now());
        when(otpStorePort.findByEmail(email)).thenReturn(Optional.of(otpDetails));

        assertThatThrownBy(() -> verifyOtpUseCase.execute(email, rawOtp))
                .isInstanceOf(OtpInvalidException.class);

        ArgumentCaptor<OtpDetails> captor = ArgumentCaptor.forClass(OtpDetails.class);
        verify(otpStorePort).save(eq(email), captor.capture(), eq(Duration.ofMinutes(5)));

        OtpDetails updatedOtp = captor.getValue();
        assertThat(updatedOtp.attempts()).isEqualTo(3);
        assertThat(updatedOtp.codeHash()).isEqualTo(correctHash);
    }

    @Test
    void execute_CorrectOtpAndUserNotFound_ThrowsException() {
        String email = "test@example.com";
        String rawOtp = "123456";
        String hash = HashUtils.sha256(rawOtp);

        OtpDetails otpDetails = new OtpDetails(hash, 0, OffsetDateTime.now());
        when(otpStorePort.findByEmail(email)).thenReturn(Optional.of(otpDetails));
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyOtpUseCase.execute(email, rawOtp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    void execute_Success_UpdatesUserAndDeletesOtp() {
        String email = "test@example.com";
        String rawOtp = "123456";
        String hash = HashUtils.sha256(rawOtp);

        OtpDetails otpDetails = new OtpDetails(hash, 0, OffsetDateTime.now());
        when(otpStorePort.findByEmail(email)).thenReturn(Optional.of(otpDetails));

        User user = mock(User.class);
        when(accountInternalApi.findUserByEmail(email)).thenReturn(Optional.of(user));

        verifyOtpUseCase.execute(email, rawOtp);

        verify(user).setIsEmailVerified(true);
        verify(accountInternalApi).saveUser(user);
        verify(otpStorePort).delete(email); // one-time-use verification
    }
}
