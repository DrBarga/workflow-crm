-- KEYS: session, token hash, user generation.
-- ARGV: user, session, token id, created at, expiry, absolute expiry, generation, device, agent, ip.
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
    return 'COLLISION'
end
local generation = redis.call('HGET', KEYS[3], 'generation')
if not generation then
    generation = ARGV[7]
    redis.call('HSET', KEYS[3], 'generation', generation)
end
if redis.call('PEXPIRETIME', KEYS[3]) < tonumber(ARGV[6]) then
    redis.call('PEXPIREAT', KEYS[3], ARGV[6])
end
redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'generation', generation,
    'createdAt', ARGV[4], 'expiresAt', ARGV[5], 'absoluteExpiresAt', ARGV[6],
    'currentTokenId', ARGV[3], 'deviceId', ARGV[8], 'userAgent', ARGV[9], 'ip', ARGV[10])
redis.call('PEXPIREAT', KEYS[1], ARGV[5])
redis.call('HSET', KEYS[2], 'sessionId', ARGV[2], 'userId', ARGV[1],
    'generation', generation, 'absoluteExpiresAt', ARGV[6],
    'createdAt', ARGV[4], 'expiresAt', ARGV[5], 'used', 'false', 'usedAt', '',
    'replacedByTokenId', '', 'deviceId', ARGV[8], 'userAgent', ARGV[9], 'ip', ARGV[10])
-- Retain consumed token hashes until the absolute ceiling for reuse detection.
redis.call('PEXPIREAT', KEYS[2], ARGV[6])
return 'OK'
