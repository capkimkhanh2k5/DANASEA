package com.danasea.backend.modules.flashsale.domain;

public class FlashSaleException extends IllegalArgumentException {
    private final String code;
    public FlashSaleException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
