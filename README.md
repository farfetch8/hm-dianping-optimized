# hm-dianping-optimized

基于黑马点评项目的深度优化版本，专注于高并发场景下的性能提升与数据一致性保障。

## 🚀 核心优化 (Core Optimizations)

### 1. RabbitMQ 异步秒杀 (Asynchronous Seckill)
将原本的同步下单流程改造为基于 **RabbitMQ** 的异步消息处理链路，显著提升了高并发场景下的系统吞吐量。
- **流程优化**：用户请求不再直接操作数据库，而是先通过 Redis + Lua 脚本进行库存预扣减与资格校验，随后发送消息至 MQ，最后由消费者异步写入数据库。
- **削峰填谷**：利用 RabbitMQ 缓冲瞬时高并发流量，保护数据库不被压垮。

### 2. 消息可靠性保障 (Message Reliability)
为了解决异步架构下的消息丢失问题，实现了全链路的消息可靠性投递：
- **生产者端**：
  - 开启 **Publisher Confirm** (发布确认) 机制，确保消息成功到达 Exchange。
  - 开启 **Publisher Return** (发布回退) 机制，处理路由失败的消息。
  - 为每条消息附加全局唯一 ID (`CorrelationData`)，便于追踪。
- **消费者端**：
  - 开启 **Consumer Retry** (自动重试) 机制，消费失败时自动重试 3 次。
  - 配置 **Dead Letter Exchange (DLX)** (死信交换机) 与死信队列，兜底处理重试后依然失败的消息，防止数据丢失。
  - 消费者业务逻辑中显式捕获异常并抛出，触发重试机制。

### 3. Redis 高级应用
- **分布式锁**：使用 Redisson 实现分布式锁，配合 Lua 脚本保证“一人一单”校验的原子性。
- **缓存优化**：解决缓存穿透、缓存击穿与缓存雪崩问题。

## ✨ 功能完善 (Feature Improvements)

### 用户登出 (User Logout)
- 实现了完整的用户注销逻辑。
- 接口：`POST /user/logout`
- 逻辑：接收请求头中的 Token，主动从 Redis 中删除对应的登录凭证 (`login:token:{token}`)，实现服务端会话失效。

## 🛠 技术栈 (Tech Stack)
- **Framework**: Spring Boot 2.x
- **Database**: MySQL 5.7+, MyBatis-Plus
- **Cache & Lock**: Redis, Redisson
- **Message Queue**: RabbitMQ
- **Tools**: Hutool, Lombok

## 📝 部署说明
确保本地或服务器已安装并启动以下服务：
- **Redis**: 默认端口 6379
- **RabbitMQ**: 默认端口 5672 (管理面板 15672)
  - *注意：首次启动会自动创建所需的 Exchange 与 Queue (含死信队列配置)。*
