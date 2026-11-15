package com.danasea.backend.modules.checkin.presentation.controllers;

import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase;
import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase.EarlyDispatchRequest;
import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase.EarlyDispatchResponse;
import com.danasea.backend.modules.checkin.application.usecases.VerifyCheckinUseCase;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/vendor/checkin")
@RequiredArgsConstructor
public class VendorCheckinController {

    private final VerifyCheckinUseCase verifyCheckinUseCase;
    private final EarlyDispatchSlotUseCase earlyDispatchSlotUseCase;

    @PostMapping("/verify")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<VerifyCheckinResponse> verifyCheckin(@Valid @RequestBody VerifyCheckinRequest request) {
        log.info("Vendor quét mã QR xác thực check-in");
        VerifyCheckinResponse response = verifyCheckinUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/early-dispatch")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<EarlyDispatchResponse> earlyDispatch(@Valid @RequestBody EarlyDispatchRequest request) {
        log.info("Vendor xác nhận điều phối khách chơi ca sớm (FIFO)");
        EarlyDispatchResponse response = earlyDispatchSlotUseCase.execute(request);
        return ResponseEntity.ok(response);
    }
}
