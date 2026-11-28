-- Never replace a ready generation in another tab, nor an ambiguous in-flight claim.
local raw = redis.call('GET', KEYS[1])
if raw then
  local ok, current = pcall(cjson.decode, raw)
  if not ok or type(current) ~= 'table' or (current.state ~= 'READY' and current.state ~= 'CLAIMED') then return -1 end
  return 0
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
return 1
