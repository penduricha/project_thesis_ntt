-- EXPERIMENTAL PRIMITIVE ONLY: not connected to any API.
-- Preconditions outside this script: password + TOTP if enabled, a short-lived
-- server-created challenge bound to the candidate session and client proof, and
-- explicit user confirmation. This script cannot establish those facts itself.
-- KEYS: challenge, old session, new session, mobile slot, mobile info.
-- ARGV: admin id, expected old session id, new session id, challenge proof hash, session TTL.
local admin, old, new, proof = ARGV[1], ARGV[2], ARGV[3], ARGV[4]
local ttl = tonumber(ARGV[5])
local function uuid(v)
  return v and #v == 36 and v:match('^[0-9a-f]+%-[0-9a-f]+%-4[0-9a-f]+%-[89ab][0-9a-f]+%-[0-9a-f]+$')
    and v:sub(9,9) == '-' and v:sub(14,14) == '-' and v:sub(19,19) == '-' and v:sub(24,24) == '-'
end
if not admin or not admin:match('^[1-9][0-9]*$') or #admin > 19
   or not uuid(old) or not uuid(new) or old == new
   or not proof or #proof ~= 64 or not proof:match('^[0-9a-f]+$')
   or not ttl or ttl ~= math.floor(ttl) or ttl < 1 or ttl > 2592000 then return -1 end
local tag = '{' .. admin .. '}'
local challengePrefix = 'auth:takeover:' .. tag .. ':'
if KEYS[1]:sub(1, #challengePrefix) ~= challengePrefix or #KEYS[1] ~= #challengePrefix + 64
   or not KEYS[1]:sub(#challengePrefix + 1):match('^[0-9a-f]+$')
   or KEYS[2] ~= 'auth:session:' .. tag .. ':' .. old
   or KEYS[3] ~= 'auth:session:' .. tag .. ':' .. new
   or KEYS[4] ~= 'auth:mobile:' .. tag
   or KEYS[5] ~= 'auth:mobile-info:' .. tag then return -1 end
local raw = redis.call('GET', KEYS[1])
if not raw or redis.call('PTTL', KEYS[1]) <= 0 then return 0 end
local ok, challenge = pcall(cjson.decode, raw)
if not ok or type(challenge) ~= 'table' or type(challenge.info) ~= 'string'
   or #challenge.info > 2048 or #challenge.info == 0 then return -1 end
if challenge.oldSessionId ~= old or challenge.newSessionId ~= new or challenge.proofHash ~= proof then return 0 end
if redis.call('GET', KEYS[4]) ~= old or redis.call('GET', KEYS[2]) ~= 'MOBILE'
   or redis.call('EXISTS', KEYS[3]) ~= 0 then return 0 end
-- Consume the challenge and revoke the old slot before creating a new one.
-- Publish the new slot last: partial script failure must not authorize the new session.
redis.call('DEL', KEYS[1], KEYS[4], KEYS[2])
redis.call('SET', KEYS[3], 'MOBILE', 'EX', ttl)
redis.call('SET', KEYS[5], challenge.info, 'EX', ttl)
redis.call('SET', KEYS[4], new, 'EX', ttl)
return 1
