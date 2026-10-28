local id = redis.call('GET', KEYS[1])
if not id then return {} end
local ttl = redis.call('PTTL', KEYS[1])
if ttl == 0 then return {} end
if id == '1' then
    id = ARGV[1]
    if ttl > 0 then
        redis.call('SET', KEYS[1], id, 'PX', ttl)
    else
        redis.call('SET', KEYS[1], id)
    end
end
if redis.call('HGET', KEYS[2], 'id') ~= id then
    redis.call('HSET', KEYS[2], 'id', id, 'userAgent', '', 'ipAddress', '', 'blockedAt', '')
    if ttl > 0 then redis.call('PEXPIRE', KEYS[2], ttl) end
end
return redis.call('HMGET', KEYS[2], 'id', 'userAgent', 'ipAddress', 'blockedAt')
