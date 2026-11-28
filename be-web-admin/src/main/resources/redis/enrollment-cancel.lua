-- A stale cancellation must never delete another generation or an in-flight claim.
local raw = redis.call('GET', KEYS[1])
if not raw then return 0 end
local ok, current = pcall(cjson.decode, raw)
if not ok or type(current) ~= 'table' then return redis.error_reply('Invalid enrollment state') end
if current.generation ~= ARGV[1] then return 0 end
if current.state ~= 'READY' then return -1 end
return redis.call('DEL', KEYS[1])
