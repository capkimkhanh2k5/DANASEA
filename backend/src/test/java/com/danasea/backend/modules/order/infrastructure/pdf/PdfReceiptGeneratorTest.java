package com.danasea.backend.modules.order.infrastructure.pdf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.order.presentation.dtos.OrderReceiptItemResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderReceiptResponse;
import java.util.List;

class PdfReceiptGeneratorTest {
    @Test
    void longDiscountedReceiptMustPaginateWithoutLosingVietnameseTextOrTotals() throws Exception {
        String longName = "Tour lặn ngắm san hô Cù Lao Chàm - trọn gói cano và thiết bị an toàn, hướng dẫn viên ".repeat(4);
        var items = IntStream.rangeClosed(1,20).mapToObj(i -> new OrderReceiptItemResponse(UUID.randomUUID(), UUID.randomUUID(),
                "Service item " + i + ": " + longName, 1, new BigDecimal("1000"), new BigDecimal("1000"),
                new BigDecimal("10"), new BigDecimal("990"))).toList();
        var receipt = new OrderReceiptResponse(UUID.randomUUID(), "ORD-TEST", "REC-TEST", UUID.randomUUID(),
                new BigDecimal("19800"), new BigDecimal("200"), "COMPLETED", OffsetDateTime.now(), "VNPAY", items);
        byte[] bytes = new PdfReceiptGenerator().generate(receipt);
        try (var document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() > 1);
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    assertTrue(text.getXDirAdj() >= 44, "Text is left of the page margin");
                    assertTrue(text.getXDirAdj() + text.getWidthDirAdj() <= 551, "Text overflows right page margin");
                    assertTrue(text.getYDirAdj() <= 800, "Text overflows the bottom page margin");
                    super.processTextPosition(text);
                }
            };
            String text = stripper.getText(document);
            assertTrue(text.contains("Service item 20:"));
            assertTrue(text.contains("Cù Lao Chàm"));
            assertTrue(text.contains("19,800.00 VND"));
            assertTrue(text.contains("200.00 VND"));
            assertTrue(text.contains("TOTAL AMOUNT:"));
            for (int i=1;i<=document.getNumberOfPages();i++) assertTrue(text.contains("Page " + i + " / " + document.getNumberOfPages()));
        }
        Files.createDirectories(Path.of("target", "test-artifacts"));
        Files.write(Path.of("target", "test-artifacts", "long-receipt.pdf"), bytes);
    }

    @Test
    void unbrokenNamesAndUnsupportedCharactersMustNotBreakPdf() throws Exception {
        var item = new OrderReceiptItemResponse(UUID.randomUUID(),UUID.randomUUID(),"X".repeat(500)+" 🌊",1,
                BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN);
        var receipt = new OrderReceiptResponse(UUID.randomUUID(), "ORD-TEST", "REC-TEST", UUID.randomUUID(), BigDecimal.TEN,
                BigDecimal.ZERO, "PAID",null,null,List.of(item));
        try (var document=Loader.loadPDF(new PdfReceiptGenerator().generate(receipt))) {
            assertTrue(new PDFTextStripper().getText(document).contains("TOTAL AMOUNT:"));
        }
    }
}
