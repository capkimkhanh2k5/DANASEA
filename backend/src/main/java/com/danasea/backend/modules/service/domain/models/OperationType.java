package com.danasea.backend.modules.service.domain.models;

/**
 * Hình thức vận hành dịch vụ (độc lập với OptionType SHARED / PRIVATE):
 * - FIXED_DEPARTURE: Chuyến có giờ khởi hành cố định (cano tour, tàu lặn, v.v.)
 * - DURATION_BASED: Dịch vụ theo lượt / thời lượng (thuê SUP 1h, Jetski 30p, cano dù bay)
 */
public enum OperationType {
    FIXED_DEPARTURE,
    DURATION_BASED
}
