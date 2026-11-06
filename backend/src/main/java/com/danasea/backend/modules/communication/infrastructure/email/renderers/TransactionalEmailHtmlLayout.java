package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.danasea.backend.modules.communication.infrastructure.email.EmailInlineAssetHelper;
import com.danasea.backend.shared.i18n.SupportedLanguage;

/**
 * Gmail-compatible HTML email layout builder inspired by modern e-ticket receipts (MoMo/Klook style).
 * Uses 100% inline CSS, table-based layout, embedded DANASEA logo (cid:danasea-logo),
 * real QR code rendering, static map preview with location marker dot, and structured info cards.
 */
public final class TransactionalEmailHtmlLayout {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s\"<>]+)");
    private static final Pattern MAP_COORDS_IN_URL = Pattern.compile("query=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");

    public enum Theme {
        OCEAN("#0d525c", "#27a3b1", "#e0f2fe", "#0369a1", "🌊"),
        SUCCESS("#0d525c", "#10b981", "#d1fae5", "#047857", "✅"),
        WARNING("#78350f", "#f59e0b", "#fef3c7", "#b45309", "⚠️"),
        DANGER("#7f1d1d", "#ef4444", "#fee2e2", "#b91c1c", "🚨");

        private final String bannerBg;
        private final String accentColor;
        private final String badgeBg;
        private final String badgeText;
        private final String icon;

        Theme(String bannerBg, String accentColor, String badgeBg, String badgeText, String icon) {
            this.bannerBg = bannerBg;
            this.accentColor = accentColor;
            this.badgeBg = badgeBg;
            this.badgeText = badgeText;
            this.icon = icon;
        }

        public String bannerBg() {
            return bannerBg;
        }

        public String accentColor() {
            return accentColor;
        }

        public String badgeBg() {
            return badgeBg;
        }

        public String badgeText() {
            return badgeText;
        }

        public String icon() {
            return icon;
        }
    }

    /**
     * Data model for an individual booked service item inside the E-Ticket card.
     */
    public record TicketItemView(
            String serviceName,
            String subOrderCode,
            String quantityLabel,
            String statusLabel,
            boolean isConfirmed,
            String departureTime,
            String locationName,
            String address,
            String googleMapsUrl,
            String subtotalFormatted
    ) {
    }

    /**
     * Data model for a complete Booking E-Ticket email (MoMo ticket style).
     */
    public record BookingTicketView(
            String customerName,
            String customerPhone,
            String customerEmail,
            String orderCode,
            String confirmedAtText,
            boolean isPartial,
            String qrData,
            String primaryDepartureTime,
            List<TicketItemView> items,
            String subtotalFormatted,
            String discountFormatted,
            String totalPaidFormatted,
            String refundPendingFormatted,
            String refundProcessedFormatted,
            String manageTicketUrl,
            String supportPhone,
            SupportedLanguage lang
    ) {
    }

    private TransactionalEmailHtmlLayout() {
    }

    /**
     * Builds a Google Maps search link from latitude/longitude or address.
     */
    public static String buildGoogleMapsUrl( Double lat, Double lng, String addressOrLocation) {
        if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
            return "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng;
        }
        if (addressOrLocation != null && !addressOrLocation.isBlank()) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(addressOrLocation.trim(), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Builds the MoMo-inspired E-Ticket confirmation HTML email with QR code,
     * 3-column item details, Google Maps link, customer info card, and footer.
     */
    public static String buildBookingTicketHtml(BookingTicketView view) {
        boolean isEn = view.lang() == SupportedLanguage.EN;
        Theme theme = view.isPartial() ? Theme.WARNING : Theme.SUCCESS;

        String qrPayload = (view.qrData() != null && !view.qrData().isBlank())
                ? view.qrData()
                : view.orderCode();
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=10&data="
                + URLEncoder.encode(qrPayload, StandardCharsets.UTF_8);

        StringBuilder content = new StringBuilder();

        // 1. Greeting & Confirmation Intro
        content.append("<div style=\"padding:4px 4px 18px 4px;font-size:15px;line-height:1.65;color:#1e293b;\">")
                .append("<p style=\"margin:0 0 10px 0;\">")
                .append(isEn ? "Hello " : "Xin chào ")
                .append("<strong>").append(escapeHtml(view.customerName())).append("</strong>,</p>")
                .append("<p style=\"margin:0;\">")
                .append(isEn
                        ? "Thank you for booking with <strong>DANASEA</strong>!<br/>DANASEA confirms your booking was processed at <strong>"
                                + escapeHtml(view.confirmedAtText()) + "</strong>. Your e-ticket details are below:"
                        : "Cảm ơn bạn đã sử dụng dịch vụ của <strong>DANASEA</strong>!<br/>DANASEA xác nhận bạn đã đặt dịch vụ thành công lúc <strong>"
                                + escapeHtml(view.confirmedAtText()) + "</strong>. Chi tiết vé của bạn như sau:")
                .append("</p></div>");

        // 2. CARD 1: E-TICKET CARD (QR + Departure + Services + Location + Total)
        content.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background-color:#ffffff;border:1px solid #cbd5e1;border-radius:16px;overflow:hidden;margin-bottom:18px;\">")
                .append("<tr><td style=\"padding:24px 20px;\">");

        // Centered Booking Code & QR Code
        content.append("<div style=\"text-align:center;\">")
                .append("<div style=\"font-size:14px;font-weight:600;color:#475569;\">")
                .append(isEn ? "Booking Code" : "Mã đặt vé")
                .append("</div>")
                .append("<div style=\"font-size:24px;font-weight:800;color:#0d9488;letter-spacing:1.2px;margin:6px 0 16px 0;\">")
                .append(escapeHtml(view.orderCode()))
                .append("</div>")
                .append("<div style=\"display:inline-block;padding:12px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;\">")
                .append("<img src=\"").append(qrImageUrl).append("\" width=\"200\" height=\"200\" alt=\"QR Check-in\" style=\"display:block;margin:0 auto;\"/>")
                .append("</div>")
                .append("<div style=\"font-size:13px;color:#64748b;margin-top:12px;\">")
                .append(isEn
                        ? "Present this QR code at the meeting point or to our staff for check-in"
                        : "Đem mã QR này đến điểm tập kết hoặc nhân viên soát vé để nhận vé")
                .append("</div>")
                .append("</div>");

        // Primary Departure Time
        if (view.primaryDepartureTime() != null && !view.primaryDepartureTime().isBlank()) {
            content.append("<div style=\"border-top:1px solid #e2e8f0;margin:18px 0;\"></div>")
                    .append("<div style=\"text-align:center;\">")
                    .append("<div style=\"font-size:13px;color:#64748b;\">")
                    .append(isEn ? "Departure Time" : "Thời gian khởi hành")
                    .append("</div>")
                    .append("<div style=\"font-size:16px;font-weight:700;color:#0f172a;margin-top:4px;\">")
                    .append(escapeHtml(view.primaryDepartureTime()))
                    .append("</div>")
                    .append("</div>");
        }

        // Service Items
        if (view.items() != null) {
            for (TicketItemView item : view.items()) {
                content.append("<div style=\"border-top:1px solid #e2e8f0;margin:18px 0;\"></div>")
                        // Service title
                        .append("<div style=\"font-size:13px;color:#64748b;\">")
                        .append(isEn ? "Experience / Tour" : "Dịch vụ / Tour")
                        .append("</div>")
                        .append("<div style=\"font-size:17px;font-weight:700;color:#0f172a;margin:4px 0 14px 0;\">")
                        .append(escapeHtml(item.serviceName()))
                        .append("</div>")
                        // 3-Column Row: Sub-order Code | Quantity | Status
                        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:14px;\">")
                        .append("<tr>")
                        .append("<td width=\"34%\" valign=\"top\" style=\"padding-right:8px;\">")
                        .append("<div style=\"font-size:12px;color:#64748b;\">").append(isEn ? "Ticket ID" : "Mã vé").append("</div>")
                        .append("<div style=\"font-size:14px;font-weight:600;color:#0f172a;margin-top:3px;\">").append(escapeHtml(item.subOrderCode())).append("</div>")
                        .append("</td>")
                        .append("<td width=\"33%\" valign=\"top\" style=\"padding-right:8px;\">")
                        .append("<div style=\"font-size:12px;color:#64748b;\">").append(isEn ? "Quantity" : "Số lượng").append("</div>")
                        .append("<div style=\"font-size:14px;font-weight:600;color:#0f172a;margin-top:3px;\">").append(escapeHtml(item.quantityLabel())).append("</div>")
                        .append("</td>")
                        .append("<td width=\"33%\" valign=\"top\" align=\"right\">")
                        .append("<div style=\"font-size:12px;color:#64748b;\">").append(isEn ? "Status" : "Trạng thái").append("</div>")
                        .append("<div style=\"font-size:13px;font-weight:700;color:").append(item.isConfirmed() ? "#059669" : "#dc2626").append(";margin-top:3px;\">")
                        .append(escapeHtml(item.statusLabel()))
                        .append("</div>")
                        .append("</td>")
                        .append("</tr></table>");

                // Departure time per item if multiple items
                if (view.items().size() > 1 && item.departureTime() != null) {
                    content.append("<div style=\"margin-bottom:12px;\">")
                            .append("<span style=\"font-size:12px;color:#64748b;\">").append(isEn ? "Slot time: " : "Ca khởi hành: ").append("</span>")
                            .append("<strong style=\"font-size:13px;color:#0f172a;\">").append(escapeHtml(item.departureTime())).append("</strong>")
                            .append("</div>");
                }

                // Meeting Point + Visual Map Card with Location Dot
                content.append("<div style=\"border-top:1px dashed #e2e8f0;padding-top:12px;\">")
                        .append("<div style=\"font-size:12px;color:#64748b;\">")
                        .append(isEn ? "Meeting Point" : "Điểm tập kết / Bến đón")
                        .append("</div>")
                        .append("<div style=\"font-size:15px;font-weight:700;color:#0f172a;margin-top:3px;\">")
                        .append(escapeHtml(item.locationName() != null && !item.locationName().isBlank() ? item.locationName() : "Đà Nẵng"))
                        .append("</div>");
                if (item.address() != null && !item.address().isBlank()
                        && !item.address().equalsIgnoreCase(item.locationName())) {
                    content.append("<div style=\"font-size:13px;color:#475569;margin-top:2px;\">")
                            .append(escapeHtml(item.address()))
                            .append("</div>");
                }
                if (item.googleMapsUrl() != null && !item.googleMapsUrl().isBlank()) {
                    String mapCid = resolveMapCidFromUrl(item.googleMapsUrl());
                    content.append("<div style=\"margin-top:10px;\">")
                            .append("<a href=\"").append(escapeHtml(item.googleMapsUrl())).append("\" target=\"_blank\" rel=\"noopener\" style=\"display:block;text-decoration:none;\">")
                            .append("<img src=\"cid:").append(mapCid).append("\" width=\"480\" alt=\"")
                            .append(isEn ? "Location Map" : "Bản đồ vị trí điểm tập kết")
                            .append("\" style=\"display:block;width:100%;max-width:480px;height:auto;border-radius:12px;border:1px solid #cbd5e1;\"/>")
                            .append("</a></div>");
                }
                content.append("</div>");
            }
        }

        // Payment Breakdown & Total Row
        content.append("<div style=\"border-top:1px solid #e2e8f0;margin:18px 0 14px 0;\"></div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");

        if (view.discountFormatted() != null && !view.discountFormatted().isBlank()) {
            content.append("<tr>")
                    .append("<td style=\"font-size:13px;color:#64748b;padding-bottom:6px;\">").append(isEn ? "Subtotal" : "Tạm tính").append("</td>")
                    .append("<td align=\"right\" style=\"font-size:14px;color:#334155;padding-bottom:6px;\">").append(escapeHtml(view.subtotalFormatted())).append("</td>")
                    .append("</tr>")
                    .append("<tr>")
                    .append("<td style=\"font-size:13px;color:#64748b;padding-bottom:10px;\">").append(isEn ? "Voucher Discount" : "Giảm giá ưu đãi").append("</td>")
                    .append("<td align=\"right\" style=\"font-size:14px;font-weight:600;color:#059669;padding-bottom:10px;\">-").append(escapeHtml(view.discountFormatted())).append("</td>")
                    .append("</tr>");
        }

        content.append("<tr>")
                .append("<td valign=\"middle\" style=\"font-size:15px;font-weight:600;color:#334155;\">")
                .append(isEn ? "Total Paid" : "Tổng tiền")
                .append("</td>")
                .append("<td valign=\"middle\" align=\"right\" style=\"font-size:21px;font-weight:800;color:#0f172a;\">")
                .append(escapeHtml(view.totalPaidFormatted()))
                .append("</td>")
                .append("</tr>");

        if (view.refundPendingFormatted() != null && !view.refundPendingFormatted().isBlank()) {
            content.append("<tr>")
                    .append("<td style=\"font-size:13px;color:#b45309;padding-top:8px;\">").append(isEn ? "Refund Processing" : "Đang xử lý hoàn tiền").append("</td>")
                    .append("<td align=\"right\" style=\"font-size:14px;font-weight:700;color:#b45309;padding-top:8px;\">").append(escapeHtml(view.refundPendingFormatted())).append("</td>")
                    .append("</tr>");
        }
        if (view.refundProcessedFormatted() != null && !view.refundProcessedFormatted().isBlank()) {
            content.append("<tr>")
                    .append("<td style=\"font-size:13px;color:#059669;padding-top:8px;\">").append(isEn ? "Refunded" : "Đã hoàn tiền").append("</td>")
                    .append("<td align=\"right\" style=\"font-size:14px;font-weight:700;color:#059669;padding-top:8px;\">").append(escapeHtml(view.refundProcessedFormatted())).append("</td>")
                    .append("</tr>");
        }

        content.append("</table></td></tr></table>");

        // 3. CARD 2: CUSTOMER INFORMATION CARD ("Thông tin người nhận vé")
        content.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background-color:#ffffff;border:1px solid #cbd5e1;border-radius:16px;overflow:hidden;margin-bottom:18px;\">")
                .append("<tr><td style=\"padding:20px;\">")
                .append("<div style=\"font-size:16px;font-weight:700;color:#0f172a;margin-bottom:14px;\">")
                .append(isEn ? "Recipient Information" : "Thông tin người nhận vé")
                .append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">")
                .append("<tr>")
                .append("<td style=\"padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:14px;color:#64748b;\">")
                .append(isEn ? "Full name" : "Họ và tên")
                .append("</td>")
                .append("<td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:14px;font-weight:700;color:#0f172a;\">")
                .append(escapeHtml(view.customerName()))
                .append("</td></tr>");

        if (view.customerPhone() != null && !view.customerPhone().isBlank()) {
            content.append("<tr>")
                    .append("<td style=\"padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:14px;color:#64748b;\">")
                    .append(isEn ? "Phone number" : "Số điện thoại")
                    .append("</td>")
                    .append("<td align=\"right\" style=\"padding:10px 0;border-bottom:1px solid #f1f5f9;font-size:14px;font-weight:700;color:#0f172a;\">")
                    .append(escapeHtml(view.customerPhone()))
                    .append("</td></tr>");
        }

        content.append("<tr>")
                .append("<td style=\"padding:10px 0 2px 0;font-size:14px;color:#64748b;\">Email</td>")
                .append("<td align=\"right\" style=\"padding:10px 0 2px 0;font-size:14px;font-weight:600;color:#0284c7;\">")
                .append(escapeHtml(view.customerEmail()))
                .append("</td></tr>")
                .append("</table></td></tr></table>");

        // 4. MANAGE TICKET LINK ON A SINGLE ROW (Ảnh 1)
        if (view.manageTicketUrl() != null && !view.manageTicketUrl().isBlank()) {
            content.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:2px 4px 16px 4px;\">")
                    .append("<tr>")
                    .append("<td valign=\"middle\" style=\"font-size:13px;color:#475569;\">")
                    .append(isEn ? "View and manage this ticket on your DANASEA account" : "Xem vé này trên tài khoản DANASEA của bạn")
                    .append("</td>")
                    .append("<td valign=\"middle\" align=\"right\" style=\"white-space:nowrap;padding-left:12px;\">")
                    .append("<a href=\"").append(escapeHtml(view.manageTicketUrl())).append("\" target=\"_blank\" rel=\"noopener\" ")
                    .append("style=\"font-size:15px;font-weight:800;color:#0d9488;text-decoration:none;\">")
                    .append(isEn ? "Manage Ticket &rarr;" : "Quản lý vé &rarr;")
                    .append("</a></td>")
                    .append("</tr></table>");
        }

        // 5. POLICY & NOTES SECTION
        content.append(buildPolicyAndNotesSection(view.supportPhone(), view.manageTicketUrl(), isEn));

        return wrapMasterShell(
                isEn ? "DANASEA E-Ticket Confirmation" : "Vé điện tử DANASEA",
                view.isPartial()
                        ? (isEn ? "Partially Confirmed" : "Xác nhận một phần")
                        : (isEn ? "Booking Confirmed" : "Đặt vé thành công"),
                theme,
                content.toString(),
                view.supportPhone(),
                view.customerEmail(),
                view.lang()
        );
    }

    /**
     * Builds a dedicated, clean HTML email for 6-digit OTP verification codes.
     */
    public static String buildOtpEmailHtml(String otpCode, SupportedLanguage lang, String supportPhone) {
        boolean isEn = lang == SupportedLanguage.EN;
        StringBuilder card = new StringBuilder();
        card.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background-color:#ffffff;border:1px solid #cbd5e1;border-radius:16px;overflow:hidden;margin-bottom:16px;\">")
                .append("<tr><td align=\"center\" style=\"padding:26px 22px;\">")
                .append("<div style=\"font-size:15px;color:#334155;line-height:1.6;margin-bottom:18px;text-align:center;\">")
                .append(isEn
                        ? "Please use the verification code below to complete your authentication on <strong>DANASEA</strong>:"
                        : "Vui lòng sử dụng mã xác thực dưới đây để hoàn tất xác minh tài khoản <strong>DANASEA</strong> của bạn:")
                .append("</div>")
                .append("<div style=\"display:inline-block;background-color:#f0fdfa;border:2px dashed #0d9488;border-radius:14px;padding:16px 32px;margin:4px 0 18px 0;\">")
                .append("<div style=\"font-size:34px;font-weight:800;letter-spacing:8px;color:#0d525c;font-family:monospace;\">")
                .append(escapeHtml(otpCode))
                .append("</div>")
                .append("</div>")
                .append("<div style=\"font-size:13px;color:#64748b;line-height:1.5;text-align:center;\">")
                .append(isEn
                        ? "This code is valid for <strong>5 minutes</strong>.<br/>For your security, please do not share this code with anyone."
                        : "Mã xác thực có hiệu lực trong vòng <strong>5 phút</strong>.<br/>Vì lý do bảo mật, vui lòng tuyệt đối không chia sẻ mã này cho bất kỳ ai.")
                .append("</div>")
                .append("</td></tr></table>");

        card.append(buildCompactSupportNote(supportPhone, isEn));

        return wrapMasterShell(
                isEn ? "OTP Verification Code" : "Mã xác thực OTP",
                isEn ? "OTP Verification Code" : "Mã xác thực OTP",
                Theme.OCEAN,
                card.toString(),
                supportPhone,
                null,
                lang
        );
    }

    /**
     * Master outer shell with DANASEA Logo (cid:danasea-logo), minimal icon+status line (Ảnh 5),
     * Content, and centered Brand Footer block without trailing email address (Ảnh 3).
     */
    public static String wrapMasterShell(
            String heroTitle,
            String badgeLabel,
            Theme theme,
            String innerContentHtml,
            String supportPhone,
            String recipientEmail,
            SupportedLanguage lang
    ) {
        Theme activeTheme = theme != null ? theme : Theme.OCEAN;
        boolean isEn = lang == SupportedLanguage.EN;
        String statusText = (badgeLabel != null && !badgeLabel.isBlank()) ? badgeLabel : heroTitle;
        String statusIconCid = switch (activeTheme) {
            case SUCCESS -> EmailInlineAssetHelper.CID_ICON_SUCCESS;
            case WARNING -> EmailInlineAssetHelper.CID_ICON_WARNING;
            case DANGER -> EmailInlineAssetHelper.CID_ICON_DANGER;
            case OCEAN -> EmailInlineAssetHelper.CID_ICON_OCEAN;
        };
        String statusTextColor = switch (activeTheme) {
            case SUCCESS -> "#059669";
            case WARNING -> "#d97706";
            case DANGER -> "#dc2626";
            case OCEAN -> "#0d9488";
        };

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"").append(isEn ? "en" : "vi").append("\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
                .append("<title>").append(escapeHtml(heroTitle)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background-color:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f1f5f9;padding:16px 8px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden;\">")
                // Top Accent Line
                .append("<tr><td style=\"height:4px;background-color:").append(activeTheme.accentColor()).append(";font-size:0;line-height:0;\">&nbsp;</td></tr>")
                // Centered DANASEA Logo
                .append("<tr><td align=\"center\" style=\"padding:22px 20px 10px 20px;\">")
                .append("<div style=\"display:inline-block;background-color:#ffffff;padding:8px 14px;border-radius:14px;border:1px solid #e2e8f0;\">")
                .append("<img src=\"cid:danasea-logo\" width=\"38\" height=\"38\" alt=\"DANASEA\" style=\"display:block;margin:0 auto;\"/>")
                .append("<div style=\"font-size:11px;font-weight:800;letter-spacing:1.5px;color:#0d525c;margin-top:3px;text-align:center;\">DANASEA</div>")
                .append("</div>")
                .append("</td></tr>")
                // Minimal Status Icon + Status Text without background box (Ảnh 5)
                .append("<tr><td align=\"center\" style=\"padding:2px 18px 16px 18px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td valign=\"middle\" style=\"padding-right:8px;\">")
                .append("<img src=\"cid:").append(statusIconCid).append("\" width=\"22\" height=\"22\" alt=\"\" style=\"display:block;\"/>")
                .append("</td>")
                .append("<td valign=\"middle\" style=\"font-size:16px;font-weight:800;letter-spacing:0.8px;text-transform:uppercase;color:")
                .append(statusTextColor).append(";\">")
                .append(escapeHtml(statusText))
                .append("</td>")
                .append("</tr></table>")
                .append("</td></tr>")
                // Inner Content
                .append("<tr><td style=\"padding:0 18px 20px 18px;\">")
                .append(innerContentHtml)
                .append("</td></tr>")
                // Centered Brand Footer Banner without trailing recipient email (Ảnh 3)
                .append("<tr><td align=\"center\" style=\"background-color:#0d525c;padding:24px 22px;color:#e0f2fe;text-align:center;\">")
                .append("<div style=\"font-size:14px;font-weight:800;color:#ffffff;margin-bottom:6px;text-align:center;\">")
                .append(isEn ? "DANASEA Marine Tourism Platform" : "Hệ thống Đặt dịch vụ Du lịch Biển DANASEA")
                .append("</div>")
                .append("<div style=\"font-size:12px;color:#bae6fd;line-height:1.6;text-align:center;\">")
                .append(isEn
                        ? "Coastal Tourism Hub, Son Tra & My Khe, Da Nang City<br/>Hotline: "
                        : "Trung tâm Du lịch Biển Sơn Trà & Mỹ Khê, TP. Đà Nẵng<br/>Hotline hỗ trợ: ")
                .append("<strong>").append(escapeHtml(supportPhone != null && !supportPhone.isBlank() ? supportPhone : "1900-6868")).append("</strong>")
                .append(" &middot; Email: <a href=\"mailto:cskh@danasea.vn\" style=\"color:#ffffff;text-decoration:underline;\">cskh@danasea.vn</a>")
                .append("</div>")
                .append("<div style=\"border-top:1px dashed #27a3b1;margin:16px auto 12px auto;max-width:420px;\"></div>")
                .append("<div style=\"font-size:11px;color:#bae6fd;text-align:center;line-height:1.5;\">")
                .append(isEn
                        ? "You are receiving this automated transactional email from DANASEA."
                        : "Bạn đang nhận được email giao dịch tự động từ hệ thống DANASEA.")
                .append("</div>")
                .append("</td></tr>")
                .append("</table>")
                .append("</td></tr></table></body></html>");

        return html.toString();
    }

    /**
     * Wraps arbitrary HTML body inside a clean white card in the DANASEA Master Shell.
     */
    public static String wrapDocument(
            String title,
            String badgeLabel,
            Theme theme,
            String bodyHtml,
            String ctaLabel,
            String ctaUrl,
            String supportPhone,
            SupportedLanguage lang
    ) {
        Theme activeTheme = theme != null ? theme : Theme.OCEAN;
        boolean isEn = lang == SupportedLanguage.EN;
        String cleanTitle = stripDanaseaPrefix(title);

        StringBuilder card = new StringBuilder();
        card.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background-color:#ffffff;border:1px solid #cbd5e1;border-radius:16px;overflow:hidden;margin-bottom:16px;\">")
                .append("<tr><td style=\"padding:22px 20px;font-size:15px;line-height:1.65;color:#334155;\">")
                .append(bodyHtml);

        if (ctaUrl != null && !ctaUrl.isBlank() && ctaLabel != null && !ctaLabel.isBlank()) {
            card.append("<div style=\"margin-top:22px;padding-top:16px;border-top:1px solid #f1f5f9;text-align:center;\">")
                    .append("<a href=\"").append(escapeHtml(ctaUrl)).append("\" target=\"_blank\" rel=\"noopener\" ")
                    .append("style=\"display:inline-block;background-color:").append(activeTheme.accentColor())
                    .append(";color:#ffffff !important;font-size:15px;font-weight:700;text-decoration:none;padding:12px 26px;border-radius:10px;\">")
                    .append(escapeHtml(ctaLabel)).append(" →")
                    .append("</a></div>");
        }

        card.append("</td></tr></table>");
        card.append(buildCompactSupportNote(supportPhone, isEn));

        return wrapMasterShell(cleanTitle, badgeLabel, activeTheme, card.toString(), supportPhone, null, lang);
    }

    /**
     * Converts structured plain text into clean cards, key-value rows, and action links.
     */
    public static String fromPlainText(
            String subject,
            String badgeLabel,
            Theme theme,
            String plainText,
            String ctaLabel,
            String ctaUrl,
            String supportPhone,
            SupportedLanguage lang
    ) {
        String extractedUrl = ctaUrl;
        StringBuilder bodyHtml = new StringBuilder();

        if (plainText != null && !plainText.isBlank()) {
            String[] lines = plainText.split("\\r?\\n");
            boolean inTable = false;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    if (inTable) {
                        bodyHtml.append("</table>");
                        inTable = false;
                    }
                    continue;
                }
                // Skip redundant plain-text dividers or footer signature lines
                if (line.matches("^[-=]{3,}$")
                        || line.startsWith("Trân trọng,")
                        || line.startsWith("Best regards,")
                        || line.equalsIgnoreCase("Đội ngũ DANASEA")
                        || line.equalsIgnoreCase("The DANASEA Team")
                        || line.contains("Email này được gửi tự động")) {
                    continue;
                }
                // Skip raw URL lines if we already render a CTA button
                if (line.matches("^https?://\\S+$")) {
                    if (extractedUrl == null || extractedUrl.isBlank()) {
                        extractedUrl = line;
                    }
                    continue;
                }

                // Section headers === ... ===
                if (line.startsWith("===") && line.endsWith("===")) {
                    if (inTable) {
                        bodyHtml.append("</table>");
                        inTable = false;
                    }
                    String heading = line.replace("===", "").trim();
                    bodyHtml.append("<div style=\"font-size:14px;font-weight:700;color:#0f172a;text-transform:uppercase;letter-spacing:0.5px;margin:18px 0 8px 0;padding-bottom:6px;border-bottom:2px solid #e2e8f0;\">")
                            .append(escapeHtml(heading))
                            .append("</div>");
                    continue;
                }

                // Bullet or Key-Value row (- Key: Value or • Key: Value)
                if ((line.startsWith("•") || line.startsWith("- ")) && line.contains(":")) {
                    String contentLine = line.substring(1).trim();
                    int colonIdx = contentLine.indexOf(':');
                    String key = contentLine.substring(0, colonIdx).trim();
                    String val = contentLine.substring(colonIdx + 1).trim();
                    if (!val.isEmpty() && key.length() <= 40) {
                        if (!inTable) {
                            bodyHtml.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 14px 0;background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:4px 14px;\">");
                            inTable = true;
                        }
                        bodyHtml.append("<tr>")
                                .append("<td style=\"padding:9px 0;border-bottom:1px solid #f1f5f9;font-size:13px;color:#64748b;\">")
                                .append(escapeHtml(key))
                                .append("</td>")
                                .append("<td align=\"right\" style=\"padding:9px 0;border-bottom:1px solid #f1f5f9;font-size:14px;font-weight:700;color:#0f172a;\">")
                                .append(linkifyAndHighlight(val))
                                .append("</td></tr>");
                        continue;
                    }
                }

                if (inTable) {
                    bodyHtml.append("</table>");
                    inTable = false;
                }

                if (extractedUrl == null || extractedUrl.isBlank()) {
                    Matcher m = URL_PATTERN.matcher(line);
                    if (m.find()) {
                        extractedUrl = m.group(1);
                    }
                }

                if (line.contains("LƯU Ý QUAN TRỌNG") || line.contains("IMPORTANT") || line.contains("CẢNH BÁO") || line.contains("KHÔNG di chuyển")) {
                    bodyHtml.append("<div style=\"margin:14px 0;padding:12px 16px;background-color:#fef2f2;border:1px solid #fecaca;border-left:4px solid #ef4444;border-radius:8px;color:#991b1b;font-size:14px;font-weight:600;\">")
                            .append(linkifyAndHighlight(line))
                            .append("</div>");
                } else {
                    bodyHtml.append("<p style=\"margin:0 0 10px 0;\">")
                            .append(linkifyAndHighlight(line))
                            .append("</p>");
                }
            }
            if (inTable) {
                bodyHtml.append("</table>");
            }
        }

        String defaultCtaLabel = ctaLabel;
        if ((defaultCtaLabel == null || defaultCtaLabel.isBlank()) && extractedUrl != null && !extractedUrl.isBlank()) {
            defaultCtaLabel = lang == SupportedLanguage.EN ? "Manage on DANASEA" : "Xem chi tiết trên DANASEA";
        }

        return wrapDocument(subject, badgeLabel, theme, bodyHtml.toString(), defaultCtaLabel, extractedUrl, supportPhone, lang);
    }

    /**
     * Renders a highlighted voucher code box for PERSONAL_VOUCHER_ISSUED emails.
     */
    public static String buildVoucherBox(String code, String discountValue, String minOrder, String validTo, SupportedLanguage lang) {
        boolean isEn = lang == SupportedLanguage.EN;
        return "<div style=\"margin:18px 0;padding:20px;background-color:#f0fdfa;border:2px dashed #0d9488;border-radius:14px;text-align:center;\">"
                + "<div style=\"font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#0f766e;font-weight:700;margin-bottom:6px;\">"
                + (isEn ? "Personal Voucher Code" : "Mã Ưu Đãi Cá Nhân")
                + "</div>"
                + "<div style=\"font-size:24px;font-weight:800;letter-spacing:2px;color:#0f172a;margin-bottom:10px;font-family:monospace;background-color:#ffffff;display:inline-block;padding:8px 18px;border-radius:8px;border:1px solid #99f6e4;\">"
                + escapeHtml(code)
                + "</div>"
                + "<div style=\"font-size:14px;color:#0f766e;font-weight:700;\">"
                + (isEn ? "Discount: " : "Mức giảm: ") + escapeHtml(discountValue)
                + " · " + (isEn ? "Min order: " : "Đơn tối thiểu: ") + escapeHtml(minOrder)
                + "</div>"
                + "<div style=\"font-size:12px;color:#64748b;margin-top:4px;\">"
                + (isEn ? "Valid until: " : "Hạn sử dụng: ") + escapeHtml(validTo)
                + "</div>"
                + "</div>";
    }

    private static String buildPolicyAndNotesSection(String supportPhone, String helpUrl, boolean isEn) {
        String phone = supportPhone != null && !supportPhone.isBlank() ? supportPhone : "1900-6868";
        String link = helpUrl != null && !helpUrl.isBlank() ? helpUrl : "https://danasea.vn";

        return "<div style=\"border-top:1px solid #e2e8f0;padding-top:18px;margin-top:8px;font-size:13px;line-height:1.6;color:#475569;\">"
                // 1. Chính sách hoàn / hủy
                + "<div style=\"margin-bottom:14px;\">"
                + buildSectionHeadingWithIcon(
                        EmailInlineAssetHelper.CID_ICON_POLICY,
                        isEn ? "Cancellation & Weather Safety Policy" : "Chính sách hoàn / hủy & Bảo vệ thời tiết"
                )
                + "<div>"
                + (isEn
                        ? "DANASEA supports rescheduling or 100% automatic refund if a Red Weather Alert prevents departure."
                        : "DANASEA hỗ trợ đổi ca miễn phí hoặc hoàn tiền 100% tự động nếu ca khởi hành bị tạm dừng do Cảnh báo Thời tiết Đỏ.")
                + "</div></div>"
                // 2. Lưu ý
                + "<div style=\"margin-bottom:14px;\">"
                + buildSectionHeadingWithIcon(
                        EmailInlineAssetHelper.CID_ICON_INFO,
                        isEn ? "Check-in Notes" : "Lưu ý khi nhận dịch vụ"
                )
                + "<ul style=\"margin:4px 0 0 0;padding-left:18px;\">"
                + "<li>" + (isEn ? "Please arrive at the meeting point 15–20 minutes before departure." : "Vui lòng có mặt tại điểm tập kết trước giờ khởi hành 15–20 phút.") + "</li>"
                + "<li>" + (isEn ? "Present the QR code above or in your DANASEA Orders page for verification." : "Xuất trình mã QR phía trên cho nhân viên đối tác để quét xác thực check-in.") + "</li>"
                + "</ul></div>"
                // 3. Liên hệ hỗ trợ
                + "<div style=\"margin-bottom:14px;\">"
                + buildSectionHeadingWithIcon(
                        EmailInlineAssetHelper.CID_ICON_PHONE,
                        isEn ? "Customer Support" : "Liên hệ hỗ trợ"
                )
                + "<div>"
                + (isEn
                        ? "For immediate assistance, please contact DANASEA Customer Care at "
                        : "Trong trường hợp cần hỗ trợ khẩn cấp tại bến hoặc thay đổi lịch trình, vui lòng liên hệ tổng đài DANASEA tại số ")
                + "<strong>" + escapeHtml(phone) + "</strong>."
                + "</div></div>"
                // 4. Trung tâm trợ giúp
                + "<div>"
                + buildSectionHeadingWithIcon(
                        EmailInlineAssetHelper.CID_ICON_HELP,
                        isEn ? "Help Center" : "Trung tâm trợ giúp"
                )
                + "<div>"
                + (isEn ? "Click " : "Bấm vào ")
                + "<a href=\"" + escapeHtml(link) + "\" target=\"_blank\" rel=\"noopener\" style=\"color:#0d9488;font-weight:700;text-decoration:underline;\">"
                + (isEn ? "here" : "đây")
                + "</a>"
                + (isEn ? " to manage your booking on DANASEA." : " để truy cập trang quản lý đơn hàng trên DANASEA.")
                + "</div></div>"
                + "</div>";
    }

    private static String buildSectionHeadingWithIcon(String iconCid, String title) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:5px;\"><tr>"
                + "<td valign=\"middle\" style=\"padding-right:8px;\">"
                + "<img src=\"cid:" + iconCid + "\" width=\"22\" height=\"22\" alt=\"\" style=\"display:block;\"/>"
                + "</td>"
                + "<td valign=\"middle\" style=\"font-size:14px;font-weight:700;color:#0f172a;\">"
                + escapeHtml(title)
                + "</td></tr></table>";
    }

    private static String buildCompactSupportNote(String supportPhone, boolean isEn) {
        String phone = supportPhone != null && !supportPhone.isBlank() ? supportPhone : "1900-6868";
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:4px 6px;\"><tr>"
                + "<td valign=\"middle\" style=\"padding-right:6px;\">"
                + "<img src=\"cid:" + EmailInlineAssetHelper.CID_ICON_PHONE + "\" width=\"18\" height=\"18\" alt=\"\" style=\"display:block;\"/>"
                + "</td>"
                + "<td valign=\"middle\" style=\"font-size:12px;color:#64748b;line-height:1.5;\">"
                + (isEn ? "Support Hotline: " : "Tổng đài hỗ trợ DANASEA: ")
                + "<strong style=\"color:#334155;\">" + escapeHtml(phone) + "</strong>"
                + " &middot; Email: <a href=\"mailto:cskh@danasea.vn\" style=\"color:#0d9488;font-weight:600;text-decoration:none;\">cskh@danasea.vn</a>"
                + "</td></tr></table>";
    }

    private static String resolveMapCidFromUrl(String googleMapsUrl) {
        if (googleMapsUrl != null) {
            Matcher matcher = MAP_COORDS_IN_URL.matcher(googleMapsUrl);
            if (matcher.find()) {
                return "danasea-map-" + matcher.group(1) + "_" + matcher.group(2);
            }
        }
        // Default Da Nang Marina coordinates if only text address was provided
        return "danasea-map-16.0662_108.2315";
    }

    private static String stripDanaseaPrefix(String subject) {
        if (subject == null) {
            return "DANASEA";
        }
        return subject.replaceFirst("^\\[DANASEA[^\\]]*\\]\\s*", "").trim();
    }

    private static String linkifyAndHighlight(String text) {
        String escaped = escapeHtml(text);
        Matcher matcher = URL_PATTERN.matcher(escaped);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(escaped, lastEnd, matcher.start());
            String url = matcher.group(1);
            String label = url.contains("google.com/maps")
                    ? "Xem vị trí trên Google Maps &rarr;"
                    : url;
            sb.append("<a href=\"").append(url).append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#0d9488;font-weight:700;text-decoration:underline;word-break:break-all;\">")
                    .append(label)
                    .append("</a>");
            lastEnd = matcher.end();
        }
        sb.append(escaped.substring(lastEnd));
        return sb.toString();
    }

    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
