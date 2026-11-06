package com.danasea.backend.modules.order.infrastructure.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Tiện ích chuẩn hóa và xác thực URL chuyển hướng (return/cancel URL) cho các
 * cổng thanh toán (PayOS, ZaloPay).
 *
 * Nguyên tắc:
 * 1. Sử dụng trực tiếp cấu hình URL riêng của từng cổng (ví dụ:
 * PAYOS_RETURN_URL, ZALOPAY_RETURN_URL).
 * 2. Không phụ thuộc vào cấu hình VNPay hay header Origin/Referer.
 * 3. Trong môi trường production: Bắt buộc cấu hình URL phải là HTTPS công khai
 * hợp lệ;
 * tuyệt đối không chấp nhận localhost / 127.0.0.1 và phải Fail-Closed (ném
 * IllegalStateException).
 * 4. Trong môi trường development: Cho phép localhost và tự động chuẩn hóa các
 * path cũ (/payment/success) sang /checkout/result.
 */
@Component
public class PaymentUrlHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentUrlHelper.class);

    public static final String DEFAULT_CHECKOUT_RESULT_PATH = "/checkout/result";
    public static final String DEFAULT_DEV_BASE_URL = "http://localhost:3000";

    private static volatile PaymentUrlHelper instance;

    private final Environment environment;
    private Boolean productionOverride = null;

    @Autowired

    public PaymentUrlHelper(@Autowired(required = false) Environment environment) {
        this.environment = environment;
        PaymentUrlHelper.instance = this;
    }

    public PaymentUrlHelper() {
        this(null);
    }

    public static PaymentUrlHelper getInstance() {
        if (instance == null) {
            synchronized (PaymentUrlHelper.class) {
                if (instance == null) {
                    instance = new PaymentUrlHelper();
                }
            }
        }
        return instance;
    }

    public static void setGlobalInstance(PaymentUrlHelper helper) {
        PaymentUrlHelper.instance = helper;
    }

    public void setProductionOverride(Boolean isProd) {
        this.productionOverride = isProd;
    }

    /**
     * Xác thực và chuẩn hóa redirect URL cho cổng thanh toán.
     *
     * @param configuredUrl URL đã cấu hình (ví dụ: từ PAYOS_RETURN_URL,
     *                      ZALOPAY_RETURN_URL)
     * @param defaultPath   Đường dẫn mặc định (ví dụ: "/checkout/result")
     * @return URL redirect an toàn đã chuẩn hóa
     */
    public static String resolveRedirectUrl(String configuredUrl, String defaultPath) {
        return getInstance().resolveUrl(configuredUrl, defaultPath);
    }

    public static String resolveClientUrl(String configuredUrl) {
        return resolveRedirectUrl(configuredUrl, DEFAULT_CHECKOUT_RESULT_PATH);
    }

    public String resolveUrl(String configuredUrl, String defaultPath) {
        boolean isProduction = isProductionEnvironment();
        String path = (defaultPath != null && !defaultPath.isBlank()) ? defaultPath : DEFAULT_CHECKOUT_RESULT_PATH;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 1. Kiểm tra Fail-closed trong môi trường production
        if (isProduction) {
            if (configuredUrl == null || configuredUrl.isBlank()) {
                throw new IllegalStateException(
                        "Payment redirect URL is not configured. In production, a valid public HTTPS URL must be set via environment variables.");
            }
            if (isLocalhostUrl(configuredUrl)) {
                throw new IllegalStateException(
                        "Production payment redirect URL cannot point to localhost: '" + configuredUrl + "'. A valid public HTTPS URL must be configured.");
            }
            if (!isHttpsUrl(configuredUrl)) {
                throw new IllegalStateException(
                        "Production payment redirect URL must use HTTPS, but was: '" + configuredUrl + "'.");
            }
            return normalizeConfiguredPath(configuredUrl, path);
        }

        // 2. Môi trường development / local test
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return DEFAULT_DEV_BASE_URL + path;
        }
        return normalizeConfiguredPath(configuredUrl, path);
    }


    /**
     * Bổ sung query parameters an toàn vào URL mà không gây xung đột tham số đã có.
     */
    public static String appendQueryParams(String baseUrl, Map<String, String> params) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }


    StringBuilder sb = new StringBuilder(baseUrl);
    boolean hasQuery = baseUrl.contains("?");

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            sb.append(hasQuery ? "&" : "?");
            hasQuery = true;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return sb.toString();
    }

    private String normalizeConfiguredPath(String configuredUrl, String fallbackPath) {
        try {
            URI uri = URI.create(configuredUrl.trim());
            String path = uri.getRawPath();
            // Nếu URL cấu hình trỏ về /payment/success hoặc /payment/cancel (không tồn tại), đổi thành /checkout/result
            if (path == null || path.isBlank() || path.equals("/")
                    || path.equals("/payment/success") || path.equals("/payment/cancel")) {
                path = fallbackPath;
            }
            String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) + "://" : "http://";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "localhost";
            int port = uri.getPort();
            String portPart = "";
            if (port != -1) {
                boolean isStandardHttp = "http".equalsIgnoreCase(uri.getScheme()) && port == 80;
                boolean isStandardHttps = "https".equalsIgnoreCase(uri.getScheme()) && port == 443;
                if (!isStandardHttp && !isStandardHttps) {
                    portPart = ":" + port;
                }
            }
            return scheme + host + portPart + path + query;
        } catch (Exception e) {
            return configuredUrl;
        }
    }

    public boolean isProductionEnvironment() {
        if (productionOverride != null) {
            return productionOverride;
        }
        if (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            return true;
        }
        String sysProp = System.getProperty("spring.profiles.active");
        if (sysProp != null && (sysProp.contains("prod") || sysProp.contains("production"))) {
            return true;
        }
        String envVar = System.getenv("SPRING_PROFILES_ACTIVE");
        return envVar != null && (envVar.contains("prod") || envVar.contains("production"));
    }

    public static boolean isLocalhostUrl(String url) {
        if (url == null || url.isBlank())
            return false;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host != null) {
                return isLocalhostHost(host);
            }
    }catch(

    Exception ignored)
    {
    }
    String lower = url.toLowerCase(Locale.ROOT);return lower.startsWith("http://localhost")||lower.startsWith("https://localhost")||lower.startsWith("http://127.0.0.1")||lower.startsWith("https://127.0.0.1");
    }

    private static boolean isLocalhostHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || host.endsWith(".localhost");
    }

    private static boolean isHttpsUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && !isLocalhostHost(uri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }
}
