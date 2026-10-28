-- KEYS: session, presented token hash, replacement token hash, grace response, user generation.
-- ARGV: session, user, now, new expiry, grace ms, replacement id, encrypted pair, new generation, generation expiry.
local now = tonumber(ARGV[3])
if redis.call('EXISTS', KEYS[2]) == 0 then
    return 'INVALID'
end
if redis.call('HGET', KEYS[2], 'userId') ~= ARGV[2]
    or redis.call('HGET', KEYS[2], 'sessionId') ~= ARGV[1]
    or redis.call('HGET', KEYS[2], 'generation') ~= redis.call('HGET', KEYS[5], 'generation') then
    return 'INVALID'
end
local absoluteExpiry = tonumber(redis.call('HGET', KEYS[2], 'absoluteExpiresAt'))
if now >= absoluteExpiry then
    return 'INVALID'
end
if redis.call('HGET', KEYS[2], 'used') == 'true' then
    local usedAt = tonumber(redis.call('HGET', KEYS[2], 'usedAt'))
    if now < usedAt + tonumber(ARGV[5]) then
        if redis.call('EXISTS', KEYS[1]) == 0
            or now >= tonumber(redis.call('HGET', KEYS[1], 'expiresAt')) then return 'INVALID' end
        local previousResponse = redis.call('GET', KEYS[4])
        if previousResponse then return 'OK:' .. previousResponse end
        return 'INVALID'
    end
    -- A generation change invalidates every device without enumerating sessions.
    redis.call('HSET', KEYS[5], 'generation', ARGV[8], 'revokedBefore', ARGV[3])
    redis.call('PEXPIREAT', KEYS[5], ARGV[9])
    return 'REUSED'
end
if redis.call('EXISTS', KEYS[1]) == 0
    or now >= tonumber(redis.call('HGET', KEYS[1], 'expiresAt')) then return 'INVALID' end
if now >= tonumber(redis.call('HGET', KEYS[2], 'expiresAt'))
    or tonumber(ARGV[4]) <= now or tonumber(ARGV[4]) > absoluteExpiry
    or redis.call('EXISTS', KEYS[3]) == 1 then
    return 'INVALID'
end
redis.call('HSET', KEYS[2], 'used', 'true', 'usedAt', ARGV[3], 'replacedByTokenId', ARGV[6])
redis.call('HSET', KEYS[3], 'sessionId', ARGV[1], 'userId', ARGV[2],
    'generation', redis.call('HGET', KEYS[2], 'generation'), 'absoluteExpiresAt', absoluteExpiry,
    'createdAt', ARGV[3], 'expiresAt', ARGV[4], 'used', 'false', 'usedAt', '', 'replacedByTokenId', '',
    'deviceId', redis.call('HGET', KEYS[1], 'deviceId'),
    'userAgent', redis.call('HGET', KEYS[1], 'userAgent'), 'ip', redis.call('HGET', KEYS[1], 'ip'))
redis.call('PEXPIREAT', KEYS[3], absoluteExpiry)
redis.call('HSET', KEYS[1], 'currentTokenId', ARGV[6], 'expiresAt', ARGV[4])
redis.call('PEXPIREAT', KEYS[1], ARGV[4])
redis.call('SET', KEYS[4], ARGV[7], 'PX', math.min(tonumber(ARGV[5]), tonumber(ARGV[4]) - now))
return 'OK:' .. ARGV[7]
