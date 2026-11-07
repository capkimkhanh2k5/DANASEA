-- ==============================================================================
-- mark_hold_closing.lua
-- Mục đích: Cấp thêm thời gian ân hạn trong Redis Hash cho đơn đang PAYMENT_CLOSING,
--           bảo đảm slot không bị khách khác cướp khi đang query/cancel gateway.
-- KEYS: Danh sách slot hold keys
-- ARGV[1]: holdId (chuỗi UUID bookingId)
-- ARGV[2]: closingGraceExpiry (timestamp ms)
-- ==============================================================================

local holdId = ARGV[1]
local closingGraceExpiry = tonumber(ARGV[2])

local updatedCount = 0

for i, key in ipairs(KEYS) do
    local entry = redis.call('HGET', key, holdId)
    if entry then
        local bar = string.find(entry, '|')
        local detail = bar and string.sub(entry, bar + 1) or ''
        redis.call('HSET', key, holdId, closingGraceExpiry .. '|' .. detail)
        local ttl = redis.call('TTL', key)
        if ttl < 86400 then redis.call('EXPIRE', key, 86400) end
        updatedCount = updatedCount + 1
    end
end

return { 'OK', tostring(updatedCount) }
