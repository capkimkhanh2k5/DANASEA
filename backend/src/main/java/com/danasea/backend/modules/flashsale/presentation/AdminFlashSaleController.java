package com.danasea.backend.modules.flashsale.presentation;

import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.*;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Policy;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/flash-sales")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFlashSaleController {
    private final FlashSaleService service;
    public AdminFlashSaleController(FlashSaleService service) { this.service=service; }
    private UUID actor() { return SecurityUtils.getCurrentUserId().orElseThrow(); }
    public record Preview(UUID serviceId,UUID optionId,BigDecimal unitSubsidy) {}
    @GetMapping("/eligible-services") public Object eligible(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.eligible(page,size); }
    @PostMapping("/preview") public Object preview(@RequestBody Preview p) { return service.preview(p.serviceId(),p.optionId(),p.unitSubsidy()); }
    @PostMapping public ResponseEntity<AdminDetail> create(@RequestBody Terms t) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(t,actor())); }
    @GetMapping public Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(page,size); }
    @GetMapping("/{id}") public Object detail(@PathVariable UUID id) { return service.detail(id); }
    @PatchMapping("/{id}") public Object update(@PathVariable UUID id,@RequestBody Terms t) { return service.update(id,t,actor()); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id,actor()); return ResponseEntity.noContent().build(); }
    @PostMapping("/{id}/activate") public Object activate(@PathVariable UUID id) { return service.activate(id,actor()); }
    @PostMapping("/{id}/cancel") public Object cancel(@PathVariable UUID id) { return service.cancel(id,actor()); }
    @PostMapping("/commission-policies") public Object policy(@RequestBody Policy p) { return service.configure(p,actor()); }
    @GetMapping("/commission-policies") public Object policies(@RequestParam UUID vendorId) { return service.policies(vendorId); }
}
