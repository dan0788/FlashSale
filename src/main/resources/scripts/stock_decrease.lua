local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

local currentStock = tonumber(redis.call('get', stockKey))

if not currentStock then
    return -2
end

if currentStock < quantity then
    return -1
end

return redis.call('decrby', stockKey, quantity)
