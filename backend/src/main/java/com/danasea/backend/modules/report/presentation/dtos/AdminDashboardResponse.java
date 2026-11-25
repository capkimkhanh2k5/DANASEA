package com.danasea.backend.modules.report.presentation.dtos;

import com.danasea.backend.modules.report.domain.models.DashboardMetrics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardResponse {

    private String status = "ADMIN_ACCESS_GRANTED";
    private BigDecimal totalRevenue;
    private BigDecimal netPayout;
    private long newOrders;
    private long completedOrders;
    private double completionRate;
    private double cancellationRate;
    private List<ReportAlertDto> alerts = new ArrayList<>();
    private Map<String, Long> vendorBadgeDistribution = new LinkedHashMap<>();

    public AdminDashboardResponse() {
        this.status = "ADMIN_ACCESS_GRANTED";
    }

    public AdminDashboardResponse(
            BigDecimal totalRevenue,
            BigDecimal netPayout,
            long newOrders,
            long completedOrders,
            double completionRate,
            double cancellationRate,
            List<ReportAlertDto> alerts) {
        this(
                totalRevenue,
                netPayout,
                newOrders,
                completedOrders,
                completionRate,
                cancellationRate,
                alerts,
                new LinkedHashMap<>());
    }

    public AdminDashboardResponse(
            BigDecimal totalRevenue,
            BigDecimal netPayout,
            long newOrders,
            long completedOrders,
            double completionRate,
            double cancellationRate,
            List<ReportAlertDto> alerts,
            Map<String, Long> vendorBadgeDistribution) {
        this.status = "ADMIN_ACCESS_GRANTED";
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        this.netPayout = netPayout != null ? netPayout : BigDecimal.ZERO;
        this.newOrders = newOrders;
        this.completedOrders = completedOrders;
        this.completionRate = completionRate;
        this.cancellationRate = cancellationRate;
        this.alerts = alerts != null ? alerts : new ArrayList<>();
        this.vendorBadgeDistribution =
                vendorBadgeDistribution != null
                        ? new LinkedHashMap<>(vendorBadgeDistribution)
                        : new LinkedHashMap<>();
    }

    public static AdminDashboardResponse from(DashboardMetrics metrics) {
        if (metrics == null) {
            return new AdminDashboardResponse(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0.0, 0.0, List.of(), Map.of());
        }
        List<ReportAlertDto> alertDtos =
                metrics.getAlerts() != null
                        ? metrics.getAlerts().stream().map(ReportAlertDto::from).toList()
                        : List.of();
        return new AdminDashboardResponse(
                metrics.getTotalRevenue(),
                metrics.getNetRevenue(),
                metrics.getNewOrders(),
                metrics.getCompletedOrders(),
                metrics.getCompletionRate(),
                metrics.getCancellationRate(),
                alertDtos,
                metrics.getVendorBadgeDistribution());
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getNetPayout() {
        return netPayout;
    }

    public void setNetPayout(BigDecimal netPayout) {
        this.netPayout = netPayout;
    }

    public long getNewOrders() {
        return newOrders;
    }

    public void setNewOrders(long newOrders) {
        this.newOrders = newOrders;
    }

    public long getCompletedOrders() {
        return completedOrders;
    }

    public void setCompletedOrders(long completedOrders) {
        this.completedOrders = completedOrders;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public double getCancellationRate() {
        return cancellationRate;
    }

    public void setCancellationRate(double cancellationRate) {
        this.cancellationRate = cancellationRate;
    }

    public List<ReportAlertDto> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<ReportAlertDto> alerts) {
        this.alerts = alerts;
    }

    public Map<String, Long> getVendorBadgeDistribution() {
        return vendorBadgeDistribution;
    }

    public void setVendorBadgeDistribution(Map<String, Long> vendorBadgeDistribution) {
        this.vendorBadgeDistribution =
                vendorBadgeDistribution != null
                        ? new LinkedHashMap<>(vendorBadgeDistribution)
                        : new LinkedHashMap<>();
    }
}
