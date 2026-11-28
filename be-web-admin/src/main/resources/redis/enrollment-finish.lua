-- Only the worker that successfully claimed this exact generation can finish it.
local raw = redis.call('GET', KEYS[1])
if not raw then return 0 end
local ok, current = pcall(cjson.decode, raw)
if not ok or type(current) ~= 'table' then return redis.error_reply('Invalid enrollment state') end
if current.generation ~= ARGV[1] or current.claim ~= ARGV[2] or current.state ~= 'CLAIMED' then return 0 end
return redis.call('DEL', KEYS[1])
