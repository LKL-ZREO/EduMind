-- Redis Lua 滑动窗口限流
-- KEYS[1]: 限流Key, 如 "ratelimit:{ChatController:chat}:ip:10.0.0.1"
-- ARGV[1]: 当前时间戳 (ms)
-- ARGV[2]: 申请令牌数
-- ARGV[3]: 时间窗口 (ms)
-- ARGV[4]: 最大令牌数
-- ARGV[5]: 请求唯一标识 (UUID)
-- 返回 1=通过, 0=拒绝

local key = KEYS[1]
local now = tonumber(ARGV[1])
local tokens = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local limit = tonumber(ARGV[4])
local member = ARGV[5]

-- 清理过期记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 统计当前窗口请求数
local count = redis.call('ZCARD', key)

if count + tokens <= limit then
    redis.call('ZADD', key, now, member)
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return 1
end

return 0
