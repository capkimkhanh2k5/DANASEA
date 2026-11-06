-- ==============================================================================
-- revert_hold_expiry.lua
-- Mục đích: Hoàn nguyên thời gian hết hạn hold về mốc ban đầu nếu DB rollback.
--           Có Version Guard chống việc compensation cũ xóa đè phiên mới.
-- KEYS: Danh sách slot hold keys (vd: inventory:slot:<slotId>:holds)
-- ARGV[1]: holdId (chuỗi UUID bookingId)
-- ARGV[2]: expectedExtendedExpiry (timestamp đã gia hạn cần revert, ms)
-- ARGV[3]: originalHoldExpiry (timestamp gốc ban đầu cần khôi phục, ms)
-- ==============================================================================

local holdId = ARGV[1]
local expectedExtendedExpiry = tonumber(ARGV[2])
local originalHoldExpiry = tonumber(ARGV[3])

-- BƯỚC 1: Kiểm tra tính toàn vẹn trên TOÀN BỘ KEYS
for i, key in ipairs(KEYS) do
    local entry = redis.call('HGET', key, holdId)
    if not entry then
        return { 'ERR_KEY_NOT_FOUND', key }
    end
    local bar = string.find(entry, '|')
    local colon = string.find(entry, ':')
    local currentExpiry = bar and tonumber(string.sub(entry, 1, bar - 1))
        or (colon and tonumber(string.sub(entry, colon + 1)))

    if not currentExpiry or currentExpiry ~= expectedExtendedExpiry then
        return { 'ERR_EXPIRY_MISMATCH', key, tostring(currentExpiry) }
    end
end

-- BƯỚC 2: Cập nhật an toàn khi toàn bộ keys đều hợp lệ
for i, key in ipairs(KEYS) do
    local entry = redis.call('HGET', key, holdId)
    local bar = string.find(entry, '|')
    local detail = bar and string.sub(entry, bar + 1) or ''
    redis.call('HSET', key, holdId, originalHoldExpiry .. '|' .. detail)
end

return { 'OK', tostring(originalHoldExpiry) }
