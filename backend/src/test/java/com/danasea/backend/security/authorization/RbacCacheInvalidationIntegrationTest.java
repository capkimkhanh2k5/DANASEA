package com.danasea.backend.security.authorization;
import com.danasea.backend.modules.account.infrastructure.mappers.UserMapper;
import org.assertj.core.api.Assertions;
import org.springframework.transaction.PlatformTransactionManager;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test chuyên biệt cho Cache Invalidation trên RBAC Security Layer.
 *
 * Kiểm tra các kịch bản theo yêu cầu R3:
 * 1. Đăng nhập / Lấy token của một user đang Active.
 * 2. Gọi API yêu cầu quyền để đảm bảo dữ liệu được nạp vào Redis Cache.
 * 3. Kích hoạt thao tác Lock account hoặc đổi Role (thông qua Use Case / Service của Account module).
 * 4. Ngay lập tức dùng lại token cũ gọi API một lần nữa.
 * 5. Yêu cầu hệ thống phải trả về 401 hoặc 403 (chứng minh cache đã bị evict, DB được query và phát hiện trạng thái mới).
 *
 * Không sử dụng Mock cho các tầng bảo mật, account API, adapter hay cache.
 * Sử dụng Testcontainers thật cho PostgreSQL và Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RbacCacheInvalidationIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountInternalApi accountInternalApi;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        tearDown();
    }

    @AfterEach
    void tearDown() {
        jpaUserRepository.deleteAll();
        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        if (usersByEmailCache != null) {
            usersByEmailCache.clear();
        }
        Cache usersByIdCache = cacheManager.getCache("usersById");
        if (usersByIdCache != null) {
            usersByIdCache.clear();
        }
    }

    /**
     * Kịch bản 1: Lock Account làm thu hồi quyền truy cập lập tức (trả về 401 Unauthorized).
     *
     * Các bước:
     * (1) Tạo user Active với Role ADMIN, lấy JWT access token hợp lệ.
     * (2) Gọi API /api/admin/dashboard yêu cầu quyền ADMIN -> Nhận 200 OK.
     *     Dữ liệu user lúc này được nạp vào Redis Cache ("usersByEmail").
     * (3) Kích hoạt thao tác Lock Account thông qua accountInternalApi.saveUser(user) (thuộc Account module).
     *     Kiểm tra cache "usersByEmail" đã bị evict.
     * (4) Dùng lại token cũ (vẫn còn hạn sử dụng) gọi lại /api/admin/dashboard.
     * (5) Hệ thống trả về 401 Unauthorized do JwtAuthenticationFilter query lại DB, phát hiện isLocked=true.
     */
    @Test
    @DisplayName("Kịch bản R3: Lock Account ngay lập tức thu hồi quyền truy cập (401 Unauthorized) dù JWT và Cache còn TTL")
    void lockAccount_evictsCache_immediatelyRevokesAccess_returns401() throws Exception {
        String email = "active_admin_lock@example.com";

        // (1) Tạo User đang Active có Role ADMIN
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_password_123");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Active Admin Lock Test");

        User savedUser = accountInternalApi.saveUser(user);
        assertThat(savedUser.getId()).isNotNull();

        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // (2) Gọi API yêu cầu quyền ADMIN để nạp dữ liệu vào Redis Cache
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ADMIN_ACCESS_GRANTED"));

        // Nạp thêm usersById vào cache để xác minh cả hai vùng cache
        accountInternalApi.findUserById(savedUser.getId());

        // Xác minh dữ liệu đã tồn tại trong Redis Cache
        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(email)).isNotNull();

        Cache usersByIdCache = cacheManager.getCache("usersById");
        assertThat(usersByIdCache).isNotNull();
        assertThat(usersByIdCache.get(savedUser.getId())).isNotNull();

        // (3) Kích hoạt thao tác Lock account thông qua Account Service (saveUser)
        Optional<User> fetchedUserOpt = accountInternalApi.findUserByEmail(email);
        assertThat(fetchedUserOpt).isPresent();
        User userToLock = fetchedUserOpt.get();
        userToLock.setIsLocked(true);
        accountInternalApi.saveUser(userToLock);

        // Xác minh cả hai Cache usersByEmail và usersById đều đã bị Evict thành công
        assertThat(usersByEmailCache.get(email)).isNull();
        assertThat(usersByIdCache.get(savedUser.getId())).isNull();

        // (4) Ngay lập tức dùng lại token cũ (vẫn còn hạn) gọi API một lần nữa
        // (5) Yêu cầu hệ thống phải trả về 401 Unauthorized
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
    }

    /**
     * Kịch bản 2: Đổi Role từ ADMIN xuống CUSTOMER làm thu hồi quyền truy cập lập tức (trả về 403 Forbidden).
     *
     * Các bước:
     * (1) Tạo user Active với Role ADMIN, lấy JWT access token hợp lệ.
     * (2) Gọi API /api/admin/dashboard yêu cầu quyền ADMIN -> Nhận 200 OK.
     *     Dữ liệu user lúc này được nạp vào Redis Cache ("usersByEmail" & "usersById").
     * (3) Kích hoạt thao tác đổi Role từ ADMIN sang CUSTOMER thông qua accountInternalApi.saveUser(user).
     *     Kiểm tra cả 2 cache "usersByEmail" và "usersById" đã bị evict.
     * (4) Dùng lại token cũ (vẫn mang claim ADMIN hoặc còn hạn) gọi lại /api/admin/dashboard.
     * (5) Hệ thống trả về 403 Forbidden do JwtAuthenticationFilter query lại DB, phát hiện Role mới là CUSTOMER.
     */
    @Test
    @DisplayName("Kịch bản R3: Đổi Role ngay lập tức thu hồi quyền truy cập (403 Forbidden) dù JWT và Cache còn TTL")
    void changeRole_evictsCache_immediatelyRevokesAdminAccess_returns403() throws Exception {
        String email = "demoted_admin_cache@example.com";

        // (1) Tạo User đang Active có Role ADMIN
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_password_456");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Demoted Admin Cache Test");

        User savedUser = accountInternalApi.saveUser(user);
        assertThat(savedUser.getId()).isNotNull();

        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // (2) Gọi API yêu cầu quyền ADMIN để nạp dữ liệu vào Redis Cache
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ADMIN_ACCESS_GRANTED"));

        // Nạp thêm usersById vào cache
        accountInternalApi.findUserById(savedUser.getId());

        // Xác minh dữ liệu đã tồn tại trong Redis Cache
        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(email)).isNotNull();

        Cache usersByIdCache = cacheManager.getCache("usersById");
        assertThat(usersByIdCache).isNotNull();
        assertThat(usersByIdCache.get(savedUser.getId())).isNotNull();

        // (3) Kích hoạt thao tác đổi Role sang CUSTOMER thông qua Account Service (saveUser)
        Optional<User> fetchedUserOpt = accountInternalApi.findUserByEmail(email);
        assertThat(fetchedUserOpt).isPresent();
        User userToDowngrade = fetchedUserOpt.get();
        userToDowngrade.setRole(Role.CUSTOMER);
        accountInternalApi.saveUser(userToDowngrade);

        // Xác minh cả hai Cache đã bị Evict thành công
        assertThat(usersByEmailCache.get(email)).isNull();
        assertThat(usersByIdCache.get(savedUser.getId())).isNull();

        // (4) Ngay lập tức dùng lại token cũ (vẫn còn hạn) gọi API một lần nữa
        // (5) Yêu cầu hệ thống phải trả về 403 Forbidden
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
    }
    /**
     * Authentication must read current account security state even if a direct DB
     * update leaves the general user cache stale. saveUser still evicts that cache.
     */
    @Test
    @DisplayName("Uncached authentication rejects a locked user even when the general cache is stale")
    void directDbLockRejectsAuthenticationDespiteStaleCacheAndSaveUserEvicts() throws Exception {
        String email = "contrast_admin@example.com";

        // (1) Tạo User đang Active có Role ADMIN
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_password_789");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Contrast Admin Test");

        User savedUser = accountInternalApi.saveUser(user);
        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // (2) Nạp user vào cache qua request hợp lệ
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(email)).isNotNull();

        // (3) Giả định bypass: Sửa trực tiếp trong DB qua JPA Entity mà không gọi saveUser()
        var entity = jpaUserRepository.findByEmail(email).orElseThrow();
        entity.setIsLocked(true);
        jpaUserRepository.saveAndFlush(entity);

        // Lúc này Cache vẫn còn do bị bypass, chưa evict!
        assertThat(usersByEmailCache.get(email)).isNotNull();

        // Authentication reads fresh security state and rejects the locked account.
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // (4) Bây giờ chuẩn hóa: Thực hiện thay đổi trạng thái thông qua accountInternalApi.saveUser()
        User domainUser = accountInternalApi.findUserByEmail(email).orElseThrow();
        // Domain user này lúc này được lấy từ cache cũ, nhưng khi ta gọi saveUser để áp dụng thay đổi chuẩn:
        domainUser.setIsLocked(true);
        accountInternalApi.saveUser(domainUser);

        // Cache lập tức bị Evict hoàn toàn!
        assertThat(usersByEmailCache.get(email)).isNull();

        // (5) Request dùng token cũ ngay lập tức bị từ chối 401 Unauthorized!
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Kịch bản Edge Case: Đổi Email phải evict cả email cũ trong cache, ngăn chặn truy cập bằng email cũ")
    void changeEmail_evictsOldEmailCache_preventsStaleAccess() throws Exception {
        String oldEmail = "old_admin_email@example.com";
        String newEmail = "new_admin_email@example.com";

        // (1) Tạo User đang Active có Role ADMIN với oldEmail
        User user = new User();
        user.setEmail(oldEmail);
        user.setPasswordHash("hashed_password_email_test");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Email Change Test Admin");

        User savedUser = accountInternalApi.saveUser(user);
        assertThat(savedUser.getId()).isNotNull();

        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String oldToken = jwtTokenProvider.generateAccessToken(auth);

        // (2) Gọi API bằng oldToken để nạp vào Redis cache
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());

        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(oldEmail)).isNotNull();

        // (3) Đổi email sang newEmail thông qua saveUser()
        savedUser.setEmail(newEmail);
        accountInternalApi.saveUser(savedUser);

        // (4) Kiểm tra cache của email cũ: Bắt buộc phải bị evict!
        for (int i = 0; i < 20 && usersByEmailCache.get(oldEmail) != null; i++) {
            Thread.sleep(50);
        }
        assertThat(usersByEmailCache.get(oldEmail)).isNull();

        // (5) Dùng lại oldToken để gọi API: Bắt buộc phải trả về 401 Unauthorized
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kịch bản R1 & R3: usersById Cache được evict ngay lập tức khi user được cập nhật qua saveUser()")
    void usersById_evictsCache_immediatelyOnUserUpdate() {
        String email = "userid_cache_test@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_pwd");
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("User ID Cache Test");

        User savedUser = accountInternalApi.saveUser(user);
        UUID userId = savedUser.getId();

        // Nạp usersById vào cache
        Optional<User> byId = accountInternalApi.findUserById(userId);
        assertThat(byId).isPresent();

        // Cập nhật user (đổi tên và khoá)
        User toUpdate = byId.get();
        toUpdate.setIsLocked(true);
        toUpdate.setFullName("Updated User Name");
        accountInternalApi.saveUser(toUpdate);

        // Đọc lại từ DB: xác nhận trả về dữ liệu mới đã được cập nhật
        Optional<User> reloaded = accountInternalApi.findUserById(userId);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getIsLocked()).isTrue();
        assertThat(reloaded.get().getFullName()).isEqualTo("Updated User Name");
    }

    @Test
    @DisplayName("Kịch bản Edge Case: Truy vấn user không tồn tại trả về empty và không lưu trữ negative cache vào Redis")
    void findUser_nonExistentUser_returnsEmptyWithoutCacheCrash() {
        String nonExistentEmail = "does_not_exist_ever@example.com";
        Optional<User> nonExistentByEmail = accountInternalApi.findUserByEmail(nonExistentEmail);
        assertThat(nonExistentByEmail).isEmpty();

        UUID nonExistentId = UUID.randomUUID();
        Optional<User> nonExistentById = accountInternalApi.findUserById(nonExistentId);
        assertThat(nonExistentById).isEmpty();

        // Xác nhận Redis Cache không lưu trữ giá trị rác hoặc empty (unless veto thành công)
        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(nonExistentEmail)).isNull();

        Cache usersByIdCache = cacheManager.getCache("usersById");
        assertThat(usersByIdCache).isNotNull();
        assertThat(usersByIdCache.get(nonExistentId)).isNull();
    }

    @Test
    @DisplayName("Kịch bản Edge Case: Xử lý phòng thủ Null và Blank input, bảo vệ SpEL và chống lỗi Cache")
    void nullAndBlankInputs_handledDefensively_preventingCacheCorruption() {
        // findUserByEmail với null và chuỗi khoảng trắng
        assertThat(accountInternalApi.findUserByEmail(null)).isEmpty();
        assertThat(accountInternalApi.findUserByEmail("   ")).isEmpty();
        assertThat(accountInternalApi.findUserByEmail("")).isEmpty();

        // findUserById với null
        assertThat(accountInternalApi.findUserById(null)).isEmpty();

        // existsByEmail với null và chuỗi khoảng trắng
        assertThat(accountInternalApi.existsByEmail(null)).isFalse();
        assertThat(accountInternalApi.existsByEmail("   ")).isFalse();

        // saveUser với null phải ném IllegalArgumentException
        Assertions.assertThatThrownBy(() -> accountInternalApi.saveUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User cannot be null");
    }

    @Test
    @DisplayName("Kịch bản Transaction: Cập nhật User trong Transaction commit thành công sẽ kích hoạt Evict Cache")
    void transactionalUpdate_commitsSuccessfully_evictsCacheImmediately() throws Exception {
        String email = "tx_admin_commit@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_pwd_tx");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("TX Admin Commit Test");

        User savedUser = accountInternalApi.saveUser(user);
        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // Nạp cache qua API
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Cache usersByEmailCache = cacheManager.getCache("usersByEmail");
        assertThat(usersByEmailCache).isNotNull();
        assertThat(usersByEmailCache.get(email)).isNotNull();

        // Thực thi cập nhật trong Transaction
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            User userInTx = accountInternalApi.findUserByEmail(email).orElseThrow();
            userInTx.setIsLocked(true);
            accountInternalApi.saveUser(userInTx);
        });

        // Sau khi Transaction commit: Cache bắt buộc phải bị evict
        assertThat(usersByEmailCache.get(email)).isNull();

        // Token cũ bị chặn lập tức (401)
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Kịch bản Transaction: Transaction rollback không làm thay đổi DB và request token cũ vẫn hợp lệ")
    void transactionalUpdate_whenRollbackOccurs_doesNotCorruptDatabaseOrCache() throws Exception {
        String email = "tx_admin_rollback@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_pwd_tx_rb");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("TX Admin Rollback Test");

        User savedUser = accountInternalApi.saveUser(user);
        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // Nạp cache qua API
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Mô phỏng nghiệp vụ bị lỗi và rollback
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        try {
            txTemplate.executeWithoutResult(status -> {
                User userInTx = accountInternalApi.findUserByEmail(email).orElseThrow();
                userInTx.setIsLocked(true);
                accountInternalApi.saveUser(userInTx);
                throw new RuntimeException("Business simulation error causing rollback");
            });
        } catch (RuntimeException ignored) {}

        // Kiểm tra trong DB: User vẫn chưa bị khoá do rollback
        User dbUser = jpaUserRepository.findByEmail(email).map(userMapper::toDomain).orElseThrow();
        assertThat(dbUser.getIsLocked()).isFalse();

        // Token cũ vẫn gọi API thành công 200 OK
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMIN_ACCESS_GRANTED"));
    }

    @Test
    @DisplayName("Kịch bản Cache Hit: Deserialize User từ Redis Cache toàn vẹn dữ liệu, không bị mất trường")
    void cacheHit_deserializesUserAccurately_withoutDataLoss() throws Exception {
        String email = "cache_hit_audit@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hashed_password_integrity");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Cache Hit Audit User");

        User savedUser = accountInternalApi.saveUser(user);
        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // Request 1: Cache miss, nạp vào Redis
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Đọc trực tiếp từ Redis Cache (Cache Hit)
        Optional<User> cachedUserOpt = accountInternalApi.findUserByEmail(email);
        assertThat(cachedUserOpt).isPresent();
        User cachedUser = cachedUserOpt.get();

        // Kiểm tra toàn vẹn các trường sau khi deserialize từ Redis JSON
        assertThat(cachedUser.getId()).isEqualTo(savedUser.getId());
        assertThat(cachedUser.getEmail()).isEqualTo(email);
        assertThat(cachedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(cachedUser.getIsLocked()).isFalse();
        assertThat(cachedUser.getIsEmailVerified()).isTrue();
        assertThat(cachedUser.getPasswordHash()).isEqualTo("hashed_password_integrity");
        assertThat(cachedUser.getFullName()).isEqualTo("Cache Hit Audit User");

        // Request 2: Gọi tiếp API để JwtAuthenticationFilter đọc Cache Hit từ Redis và xác thực thành công
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMIN_ACCESS_GRANTED"));
    }

    @Test
    @DisplayName("Kịch bản Robustness: Case-Insensitive Email được chuẩn hóa, tránh phân mảnh cache và stale cache")
    void caseInsensitiveEmail_normalizedSafely_preventsFragmentedStaleCache() throws Exception {
        String baseEmail = "mixed_case_admin@example.com";
        String upperEmail = "MIXED_CASE_ADMIN@EXAMPLE.COM";

        User user = new User();
        user.setEmail(upperEmail); // truyền email chữ hoa
        user.setPasswordHash("hashed_pwd_case");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setFullName("Mixed Case Admin");

        User savedUser = accountInternalApi.saveUser(user);
        // Xác nhận email trong entity đã được chuẩn hóa về lowercase
        assertThat(savedUser.getEmail()).isEqualTo(baseEmail);

        Authentication auth = new Authentication(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPasswordHash(),
                savedUser.getRole().name(),
                true,
                true
        );
        String token = jwtTokenProvider.generateAccessToken(auth);

        // Gọi API nạp cache
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Truy vấn với chữ hoa: vẫn lấy được từ cache (cache hit) mà không tạo key phân mảnh
        Optional<User> foundUpper = accountInternalApi.findUserByEmail(upperEmail);
        assertThat(foundUpper).isPresent();

        // Đổi trạng thái user sang locked
        Optional<User> fetchedUserOpt = accountInternalApi.findUserByEmail(baseEmail);
        assertThat(fetchedUserOpt).isPresent();
        User userToLock = fetchedUserOpt.get();
        userToLock.setIsLocked(true);
        accountInternalApi.saveUser(userToLock);

        // Xác nhận dữ liệu mới đã được cập nhật và cache cũ không còn giá trị stale
        Optional<User> reloaded = accountInternalApi.findUserByEmail(baseEmail);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getIsLocked()).isTrue();

        // Token cũ bị từ chối 401 ngay lập tức
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
