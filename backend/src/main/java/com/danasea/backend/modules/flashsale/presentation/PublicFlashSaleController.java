package com.danasea.backend.modules.flashsale.presentation;

import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flash-sales")
public class PublicFlashSaleController {
    private final FlashSaleService service;
    public PublicFlashSaleController(FlashSaleService service) { this.service=service; }
    @GetMapping("/active") public List<PublicOffer> active() { return service.active(); }
    @GetMapping("/{id}") public PublicOffer detail(@PathVariable UUID id) { return service.publicDetail(id); }
}
