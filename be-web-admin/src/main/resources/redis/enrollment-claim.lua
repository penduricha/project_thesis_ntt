-- Claim only the generation displayed to this caller. Persist the claim before DB work;
-- after a crash, an ambiguous claim must not expire and be silently reused.
local raw = redis.call('GET', KEYS[1])
if not raw then return nil end
local ok, current = pcall(cjson.decode, raw)
if not ok or type(current) ~= 'table' then return redis.error_reply('Invalid enrollment state') end
if current.state ~= 'READY' or current.generation ~= ARGV[1] then return nil end
if ARGV[3] ~= '' and tonumber(ARGV[3]) ~= current.periodSeconds then return nil end
if redis.call('PTTL', KEYS[1]) <= 0 then return nil end
current.state = 'CLAIMED'
current.claim = ARGV[2]
redis.call('SET', KEYS[1], cjson.encode(current))
return raw
