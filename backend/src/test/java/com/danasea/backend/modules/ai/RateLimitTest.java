package com.danasea.backend.modules.ai;

import com.danasea.backend.modules.ai.domain.TrustTier;
import com.danasea.backend.modules.ai.infrastructure.redis.RedisRateLimiter;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Acceptance Criteria & 4-Tier Test Suite for RateLimitTest.
 *
 * <p>Requirements:
 * - R3: Standardize Two-Layer Rate Limit.
 * - Layer 1: IP anomaly (soft-throttle) - degrades trust tier to UNVERIFIED for rapid requests (>100 req/min); do not hard 429 entire IP.
 * - Layer 2: Trust-tier token bucket: UNVERIFIED (5), VERIFIED (20), TRUSTED (50), RESTRICTED (0).
 * - Decoupled from Content Safety / Moderation Guard.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimit Acceptance Test Suite (Tiers 1-4)")
public class RateLimitTest {

    @Mock
    private LettuceBasedProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> proxyManagerBuilder;

    @Mock
    private BucketProxy ipBucket;

    @Mock
    private BucketProxy userBucket;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(proxyManager.builder()).thenReturn(proxyManagerBuilder);
        rateLimiter = new RedisRateLimiter(proxyManager);
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (>=5 test cases across R3 core requirements)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 - F3.1: Layer 1 IP Soft-Throttle - Degrades TRUSTED user to UNVERIFIED instead of hard 429")
    void testLayer1SoftThrottle_DegradesToUnverifiedTier() {
        // IP bucket fails (rate limit reached), User bucket succeeds
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(false);
        when(userBucket.tryConsume(1)).thenReturn(true);

        boolean allowed = rateLimiter.isAllowed("user-100", "192.168.1.50", TrustTier.TRUSTED);

        // Crucial verification: request is allowed because it was soft-throttled to UNVERIFIED rather than blocked
        assertTrue(allowed, "Soft throttle must allow request under degraded UNVERIFIED capacity");
        // Verify both IP bucket and User bucket were checked
        verify(ipBucket, times(1)).tryConsume(1);
        verify(userBucket, times(1)).tryConsume(1);
    }

    @Test
    @DisplayName("Tier 1 - F3.2: RESTRICTED tier is always blocked immediately without token consumption")
    void testRestrictedTier_AlwaysBlocked() {
        boolean allowed = rateLimiter.isAllowed("user-banned", "192.168.1.1", TrustTier.RESTRICTED);

        assertFalse(allowed, "RESTRICTED tier users must be blocked immediately");
        // Neither IP bucket nor User bucket is called for RESTRICTED tier
        verify(ipBucket, never()).tryConsume(anyLong());
        verify(userBucket, never()).tryConsume(anyLong());
    }

    @Test
    @DisplayName("Tier 1 - F3.3: UNVERIFIED tier enforces capacity 5 requests per minute")
    void testUnverifiedTier_ConfiguredCapacityFive() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<BucketConfiguration> configCaptor = ArgumentCaptor.forClass(BucketConfiguration.class);

        boolean allowed = rateLimiter.isAllowed("user-unverified", "192.168.1.10", TrustTier.UNVERIFIED);

        assertTrue(allowed);
        verify(proxyManagerBuilder, times(2)).build(any(), configCaptor.capture());

        BucketConfiguration userConfig = configCaptor.getAllValues().get(1);
        assertEquals(5, userConfig.getBandwidths()[0].getCapacity(), "UNVERIFIED capacity must be 5");
    }

    @Test
    @DisplayName("Tier 1 - F3.4: VERIFIED tier enforces capacity 20 requests per minute")
    void testVerifiedTier_ConfiguredCapacityTwenty() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<BucketConfiguration> configCaptor = ArgumentCaptor.forClass(BucketConfiguration.class);

        boolean allowed = rateLimiter.isAllowed("user-verified", "192.168.1.20", TrustTier.VERIFIED);

        assertTrue(allowed);
        verify(proxyManagerBuilder, times(2)).build(any(), configCaptor.capture());

        BucketConfiguration userConfig = configCaptor.getAllValues().get(1);
        assertEquals(20, userConfig.getBandwidths()[0].getCapacity(), "VERIFIED capacity must be 20");
    }

    @Test
    @DisplayName("Tier 1 - F3.5: TRUSTED tier enforces capacity 50 requests per minute")
    void testTrustedTier_ConfiguredCapacityFifty() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<BucketConfiguration> configCaptor = ArgumentCaptor.forClass(BucketConfiguration.class);

        boolean allowed = rateLimiter.isAllowed("user-trusted", "192.168.1.30", TrustTier.TRUSTED);

        assertTrue(allowed);
        verify(proxyManagerBuilder, times(2)).build(any(), configCaptor.capture());

        BucketConfiguration userConfig = configCaptor.getAllValues().get(1);
        assertEquals(50, userConfig.getBandwidths()[0].getCapacity(), "TRUSTED capacity must be 50");
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>=5 test cases)
    // =========================================================================

    @Test
    @DisplayName("Tier 2 - B3.1: Null or blank IP address skips Layer 1 IP check")
    void testNullOrBlankIp_SkipsLayer1() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class))).thenReturn(userBucket);
        when(userBucket.tryConsume(1)).thenReturn(true);

        boolean allowedNullIp = rateLimiter.isAllowed("user-1", null, TrustTier.VERIFIED);
        boolean allowedBlankIp = rateLimiter.isAllowed("user-2", "   ", TrustTier.VERIFIED);

        assertTrue(allowedNullIp);
        assertTrue(allowedBlankIp);
        // Only user bucket build called (1 for null, 1 for blank)
        verify(proxyManagerBuilder, times(2)).build(any(), any(BucketConfiguration.class));
    }

    @Test
    @DisplayName("Tier 2 - B3.2: User bucket exhaustion returns false when tokens exhausted")
    void testUserBucketExhausted_ReturnsFalse() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(false); // exhausted

        boolean allowed = rateLimiter.isAllowed("user-busy", "10.0.0.1", TrustTier.VERIFIED);

        assertFalse(allowed, "Exhausted user bucket must reject request");
    }

    @Test
    @DisplayName("Tier 2 - B3.3: IP Soft-throttle with exhausted degraded bucket returns false")
    void testIpSoftThrottle_DegradedBucketExhausted_ReturnsFalse() {
        // IP bucket fails -> degraded to UNVERIFIED (capacity 5) -> user bucket also exhausted
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(false);
        when(userBucket.tryConsume(1)).thenReturn(false);

        boolean allowed = rateLimiter.isAllowed("user-heavy", "10.0.0.2", TrustTier.TRUSTED);

        assertFalse(allowed);
    }

    @Test
    @DisplayName("Tier 2 - B3.4: Layer 1 IP bucket configuration has capacity 100 per minute")
    void testLayer1IpConfig_CapacityOneHundred() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<BucketConfiguration> configCaptor = ArgumentCaptor.forClass(BucketConfiguration.class);

        rateLimiter.isAllowed("user-test", "192.168.1.100", TrustTier.VERIFIED);

        verify(proxyManagerBuilder, atLeastOnce()).build(any(), configCaptor.capture());
        BucketConfiguration ipConfig = configCaptor.getAllValues().get(0);
        assertEquals(100, ipConfig.getBandwidths()[0].getCapacity(), "IP bucket capacity must be 100");
    }

    @Test
    @DisplayName("Tier 2 - B3.5: IP key format conforms to 'ai:ratelimit:ip:{ip}' prefix")
    void testIpKeyFormat_ConformsToPrefix() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(true);
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);

        rateLimiter.isAllowed("user-test", "203.0.113.195", TrustTier.VERIFIED);

        verify(proxyManagerBuilder, atLeastOnce()).build(keyCaptor.capture(), (BucketConfiguration) any());
        String ipKey = new String(keyCaptor.getAllValues().get(0));
        assertEquals("ai:ratelimit:ip:203.0.113.195", ipKey);
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    @DisplayName("Tier 3 - C3.1: Layer 1 degradation applied to VERIFIED tier")
    void testVerifiedTier_DegradesToUnverifiedOnIpAnomaly() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        when(ipBucket.tryConsume(1)).thenReturn(false); // IP spamming
        when(userBucket.tryConsume(1)).thenReturn(true);

        ArgumentCaptor<BucketConfiguration> configCaptor = ArgumentCaptor.forClass(BucketConfiguration.class);

        boolean allowed = rateLimiter.isAllowed("user-v1", "172.16.0.5", TrustTier.VERIFIED);

        assertTrue(allowed);
        verify(proxyManagerBuilder, times(2)).build(any(), configCaptor.capture());
        // Degraded from VERIFIED (20) to UNVERIFIED (5)
        assertEquals(5, configCaptor.getAllValues().get(1).getBandwidths()[0].getCapacity());
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Tier 4 - R3.1: Public Wi-Fi anomaly scenario: IP exceeds 100 req/min, authenticated users throttled fairly")
    void testPublicWifiScenario_LegitimateUsersSoftThrottledFairly() {
        when(proxyManagerBuilder.build(any(), any(BucketConfiguration.class)))
                .thenReturn(ipBucket)
                .thenReturn(userBucket);

        // Shared IP is overloaded by bot
        when(ipBucket.tryConsume(1)).thenReturn(false);

        // Legitimate user with low traffic still has tokens in their UNVERIFIED bucket
        when(userBucket.tryConsume(1)).thenReturn(true);

        boolean allowedUser1 = rateLimiter.isAllowed("legit-user-1", "118.69.100.1", TrustTier.TRUSTED);
        assertTrue(allowedUser1, "Legitimate user must not experience total outage due to noisy neighbor IP");
    }
}
