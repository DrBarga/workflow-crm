redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[5])
redis.call('HSET', KEYS[2], 'id', ARGV[1], 'userAgent', ARGV[2], 'ipAddress', ARGV[3], 'blockedAt', ARGV[4])
redis.call('PEXPIRE', KEYS[2], ARGV[5])
redis.call('DEL', KEYS[3], KEYS[4])
return 1
