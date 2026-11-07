-- ==============================================================================
-- extend_hold.lua
-- Mục đích: Gia hạn thời gian giữ chỗ trong Redis Hash khi chuyển sang PENDING_PAYMENT.
--           Tương thích 100% với acquire_hold.lua và release_hold.lua.
-- KEYS: Danh sách các slot hold keys, vd: KEYS[1] = "inventory:slot:" .. slotId .. ":holds"
-- ARGV[1]: nowMillis (chuỗi số System.currentTimeMillis)
-- ARGV[2]: holdId (chuỗi UUID bookingId)
-- ARGV[3]: newExpireAtMillis (chuỗi số timestamp hết hạn mới = nowMillis + 360000)
-- ==============================================================================

local now = tonumber(ARGV[1])
local holdId = ARGV[2]
local newExpireAt = tonumber(ARGV[3])

-- BƯỚC 1: Kiểm tra tính hợp lệ trên TOÀN BỘ các Keys trước khi sửa đổi bất kỳ key nào
local entriesToUpdate = {}

for i, key in ipairs(KEYS) do
    local entry = redis.call('HGET', key, holdId)
    if not entry then
        return { 'ERR_NOT_FOUND', key }
    end

    local bar = string.find(entry, '|')
    local colon = string.find(entry, ':')
    local oldExpiry = bar and tonumber(string.sub(entry, 1, bar - 1))
        or (colon and tonumber(string.sub(entry, colon + 1)))

    if not oldExpiry or oldExpiry <= now then
        redis.call('HDEL', key, holdId)
        return { 'ERR_EXPIRED', key }
    end

    local detail = bar and string.sub(entry, bar + 1) or ''
    entriesToUpdate[key] = newExpireAt .. '|' .. detail
end

-- BƯỚC 2: Thực hiện cập nhật đồng loạt (All-or-Nothing)
for key, newEntry in pairs(entriesToUpdate) do
    redis.call('HSET', key, holdId, newEntry)
    local currentTtl = redis.call('TTL', key)
    if currentTtl < 86400 then
        redis.call('EXPIRE', key, 86400)
    end
end

return { 'OK', tostring(newExpireAt) }
