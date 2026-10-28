-- KEYS: session.
-- ARGV: expected user (empty for internal revocation), session id, device index prefix.
local userId = redis.call('HGET', KEYS[1], 'userId')
if not userId or (ARGV[1] ~= '' and userId ~= ARGV[1]) then
    return 0
end

local deviceId = redis.call('HGET', KEYS[1], 'deviceId')
redis.call('DEL', KEYS[1])
if deviceId and deviceId ~= '' then
    redis.call('SREM', ARGV[3] .. userId .. ':' .. deviceId, ARGV[2])
end
return 1
