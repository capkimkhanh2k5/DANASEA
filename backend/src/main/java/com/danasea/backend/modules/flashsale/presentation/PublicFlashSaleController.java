package com.danasea.backend.modules.flashsale.presentation;

import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.PublicOffer;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flash-sales")
public class PublicFlashSaleController {
    private final FlashSaleService service;
    public PublicFlashSaleController(FlashSaleService service) { this.service=service; }
    @GetMapping("/active") public List<PublicOffer> active() { return service.active(); }
    @GetMapping("/{id}") public PublicOffer detail(@PathVariable UUID id) { return service.publicDetail(id); }
}
