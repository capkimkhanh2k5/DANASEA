package com.danasea.backend.modules.communication.infrastructure.email;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;

/**
 * Generates and attaches inline CID image assets (brand logo, crisp vector-rendered status/section icons,
 * and static map previews with a location marker dot) so Gmail renders them reliably without emoji ugliness.
 */
public final class EmailInlineAssetHelper {

    public static final String CID_LOGO = "danasea-logo";
    public static final String CID_ICON_SUCCESS = "danasea-icon-success";
    public static final String CID_ICON_OCEAN = "danasea-icon-ocean";
    public static final String CID_ICON_WARNING = "danasea-icon-warning";
    public static final String CID_ICON_DANGER = "danasea-icon-danger";
    public static final String CID_ICON_POLICY = "danasea-icon-policy";
    public static final String CID_ICON_INFO = "danasea-icon-info";
    public static final String CID_ICON_PHONE = "danasea-icon-phone";
    public static final String CID_ICON_HELP = "danasea-icon-help";

    private static final String LOGO_RESOURCE_PATH = "email/danasea-logo-sm.png";
    private static final Pattern MAP_CID_PATTERN = Pattern.compile("cid:(danasea-map-(-?\\d+(?:\\.\\d+)?)_(-?\\d+(?:\\.\\d+)?))");
    private static final Map<String, byte[]> ICON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> MAP_CACHE = new ConcurrentHashMap<>();

    private EmailInlineAssetHelper() {
    }

    /**
     * Attaches all referenced cid:* inline images found in htmlBody to the MimeMessageHelper.
     */
    public static void attachInlineAssets(MimeMessageHelper helper, String htmlBody) throws MessagingException {
        if (htmlBody == null || htmlBody.isBlank()) {
            return;
        }

        if (htmlBody.contains("cid:" + CID_LOGO)) {
            ClassPathResource logoResource = new ClassPathResource(LOGO_RESOURCE_PATH);
            if (logoResource.exists()) {
                helper.addInline(CID_LOGO, logoResource, "image/png");
            }
        }

        String[] iconCids = {
            CID_ICON_SUCCESS,
            CID_ICON_OCEAN,
            CID_ICON_WARNING,
            CID_ICON_DANGER,
            CID_ICON_POLICY,
            CID_ICON_INFO,
            CID_ICON_PHONE,
            CID_ICON_HELP
        };

        for (String cid : iconCids) {
            if (htmlBody.contains("cid:" + cid)) {
                byte[] bytes = ICON_CACHE.computeIfAbsent(cid, EmailInlineAssetHelper::renderIconPng);
                if (bytes != null && bytes.length > 0) {
                    helper.addInline(cid, new ByteArrayResource(bytes), "image/png");
                }
            }
        }

        Matcher mapMatcher = MAP_CID_PATTERN.matcher(htmlBody);
        while (mapMatcher.find()) {
            String fullCid = mapMatcher.group(1);
            double lat = Double.parseDouble(mapMatcher.group(2));
            double lng = Double.parseDouble(mapMatcher.group(3));
            byte[] mapBytes = MAP_CACHE.computeIfAbsent(fullCid, k -> renderStaticMapWithPin(lat, lng));
            if (mapBytes != null && mapBytes.length > 0) {
                helper.addInline(fullCid, new ByteArrayResource(mapBytes), "image/png");
            }
        }
    }

    private static byte[] renderIconPng(String cid) {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (cid) {
            case CID_ICON_SUCCESS -> {
                g2.setColor(new Color(16, 185, 129)); // #10b981
                g2.fill(new Ellipse2D.Double(2, 2, size - 4, size - 4));
                drawCheckmark(g2, Color.WHITE, 5.5f);
            }
            case CID_ICON_OCEAN -> {
                g2.setColor(new Color(13, 148, 136)); // #0d9488
                g2.fill(new Ellipse2D.Double(2, 2, size - 4, size - 4));
                drawCheckmark(g2, Color.WHITE, 5.5f);
            }
            case CID_ICON_WARNING -> {
                g2.setColor(new Color(245, 158, 11)); // #f59e0b
                g2.fill(new Ellipse2D.Double(2, 2, size - 4, size - 4));
                drawExclamation(g2, Color.WHITE);
            }
            case CID_ICON_DANGER -> {
                g2.setColor(new Color(239, 68, 68)); // #ef4444
                g2.fill(new Ellipse2D.Double(2, 2, size - 4, size - 4));
                drawExclamation(g2, Color.WHITE);
            }
            case CID_ICON_POLICY -> {
                drawSubtleCircleBackground(g2, size);
                g2.setColor(new Color(13, 148, 136));
                g2.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new RoundRectangle2D.Double(20, 15, 24, 34, 6, 6));
                g2.drawLine(26, 24, 38, 24);
                g2.drawLine(26, 32, 38, 32);
                g2.drawLine(26, 40, 33, 40);
            }
            case CID_ICON_INFO -> {
                drawSubtleCircleBackground(g2, size);
                g2.setColor(new Color(13, 148, 136));
                g2.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(32, 29, 32, 43);
                g2.fill(new Ellipse2D.Double(29.5, 19, 5, 5));
            }
            case CID_ICON_PHONE -> {
                drawSubtleCircleBackground(g2, size);
                g2.setColor(new Color(13, 148, 136));
                g2.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D phone = new Path2D.Double();
                phone.moveTo(23, 20);
                phone.lineTo(28, 20);
                phone.lineTo(30, 26);
                phone.lineTo(27, 29);
                phone.curveTo(29, 34, 31, 36, 35, 38);
                phone.lineTo(38, 35);
                phone.lineTo(44, 37);
                phone.lineTo(44, 42);
                phone.curveTo(44, 45, 40, 46, 36, 45);
                phone.curveTo(27, 42, 21, 36, 19, 27);
                phone.curveTo(18, 23, 20, 20, 23, 20);
                g2.draw(phone);
            }
            case CID_ICON_HELP -> {
                drawSubtleCircleBackground(g2, size);
                g2.setColor(new Color(13, 148, 136));
                g2.setFont(new Font("SansSerif", Font.BOLD, 30));
                FontMetrics fm = g2.getFontMetrics();
                String q = "?";
                int x = (size - fm.stringWidth(q)) / 2;
                int y = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(q, x, y);
            }
            default -> {
            }
        }

        g2.dispose();
        return toPngBytes(img);
    }

    private static void drawSubtleCircleBackground(Graphics2D g2, int size) {
        g2.setColor(new Color(240, 253, 250)); // #f0fdfa
        g2.fill(new Ellipse2D.Double(2, 2, size - 4, size - 4));
        g2.setColor(new Color(153, 246, 228)); // #99f6e4
        g2.setStroke(new BasicStroke(2.5f));
        g2.draw(new Ellipse2D.Double(2, 2, size - 4, size - 4));
    }

    private static void drawCheckmark(Graphics2D g2, Color color, float strokeWidth) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Double();
        check.moveTo(19, 33);
        check.lineTo(28, 42);
        check.lineTo(46, 22);
        g2.draw(check);
    }

    private static void drawExclamation(Graphics2D g2, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(32, 18, 32, 35);
        g2.fill(new Ellipse2D.Double(29, 42, 6, 6));
    }

    /**
     * Renders a 520x185 map preview centered at (lat, lng) using Google Maps road tiles at zoom 14
     * (showing surrounding streets, coastline, and landmarks) and overlays a clean web-style location dot.
     */
    private static byte[] renderStaticMapWithPin(double lat, double lng) {
        int width = 520;
        int height = 185;
        int zoom = 14;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        boolean tilesLoaded = drawMapTiles(g2, lat, lng, zoom, width, height);
        if (!tilesLoaded) {
            drawStylizedMapFallback(g2, width, height);
        }

        int cx = width / 2;
        int cy = height / 2;

        // Outer translucent pulse ring (like web map location dot)
        g2.setColor(new Color(239, 68, 68, 50));
        g2.fill(new Ellipse2D.Double(cx - 20, cy - 20, 40, 40));
        g2.setColor(new Color(239, 68, 68, 95));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new Ellipse2D.Double(cx - 20, cy - 20, 40, 40));

        // Drop shadow under pin dot
        g2.setColor(new Color(15, 23, 42, 70));
        g2.fill(new Ellipse2D.Double(cx - 9, cy - 7, 18, 18));

        // Crisp white ring + vibrant red marker dot
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
        g2.setColor(new Color(239, 68, 68)); // #ef4444
        g2.fill(new Ellipse2D.Double(cx - 6, cy - 6, 12, 12));

        // Bottom-right "Xem trên Google Maps ↗" floating pill badge on the map
        String badgeText = "Xem trên Google Maps ↗";
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(badgeText) + 22;
        int bh = 26;
        int bx = width - bw - 10;
        int by = height - bh - 10;

        g2.setColor(new Color(255, 255, 255, 240));
        g2.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 13, 13));
        g2.setColor(new Color(203, 213, 225));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 13, 13));

        g2.setColor(new Color(13, 82, 92)); // #0d525c
        g2.drawString(badgeText, bx + 11, by + 17);

        g2.dispose();
        return toPngBytes(canvas);
    }

    private static boolean drawMapTiles(Graphics2D g2, double lat, double lng, int zoom, int width, int height) {
        try {
            double n = Math.pow(2.0, zoom);
            double exactTileX = (lng + 180.0) / 360.0 * n;
            double latRad = Math.toRadians(lat);
            double exactTileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

            int centerTileX = (int) Math.floor(exactTileX);
            int centerTileY = (int) Math.floor(exactTileY);

            int offsetX = (int) Math.round((exactTileX - centerTileX) * 256.0);
            int offsetY = (int) Math.round((exactTileY - centerTileY) * 256.0);

            int startScreenX = (width / 2) - offsetX;
            int startScreenY = (height / 2) - offsetY;

            boolean anySuccess = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int tx = centerTileX + dx;
                    int ty = centerTileY + dy;
                    int drawX = startScreenX + dx * 256;
                    int drawY = startScreenY + dy * 256;

                    if (drawX + 256 < 0 || drawX > width || drawY + 256 < 0 || drawY > height) {
                        continue;
                    }

                    BufferedImage tile = fetchTile(zoom, tx, ty);
                    if (tile != null) {
                        g2.drawImage(tile, drawX, drawY, null);
                        anySuccess = true;
                    }
                }
            }
            return anySuccess;
        } catch (Exception e) {
            return false;
        }
    }

    private static BufferedImage fetchTile(int z, int x, int y) {
        String url = "https://mt1.google.com/vt/lyrs=m&x=" + x + "&y=" + y + "&z=" + z + "&hl=vi";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception ignored) {
            // Fallback handled by caller
        }
        return null;
    }

    private static void drawStylizedMapFallback(Graphics2D g2, int width, int height) {
        // Land background + coastal water gradient
        g2.setColor(new Color(241, 245, 239));
        g2.fillRect(0, 0, width, height);

        GradientPaint seaPaint = new GradientPaint(width * 0.65f, 0, new Color(186, 230, 253), width, height, new Color(125, 211, 252));
        g2.setPaint(seaPaint);
        Path2D coast = new Path2D.Double();
        coast.moveTo(width * 0.68, 0);
        coast.curveTo(width * 0.62, height * 0.4, width * 0.74, height * 0.7, width * 0.65, height);
        coast.lineTo(width, height);
        coast.lineTo(width, 0);
        coast.closePath();
        g2.fill(coast);

        // Stylized street grid
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(5f));
        g2.drawLine(0, height / 3, (int) (width * 0.7), height / 2);
        g2.drawLine(0, (height * 2) / 3, (int) (width * 0.68), (height * 2) / 3);
        g2.drawLine(width / 4, 0, width / 3, height);
        g2.drawLine(width / 2, 0, (int) (width * 0.52), height);
    }

    private static byte[] toPngBytes(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
