# redis-examples

- [redis, Github](https://github.com/antirez/redis)
- [redis.io](http://redis.io/)
- [redis.cn](http://www.redis.cn/): 中文，相对文档较老。

- [redisson](https://github.com/redisson/redisson)

2020-01-14:
1. redisson 迁移到 [Vergilyn Github, redisson-fork](https://github.com/vergilyn/redisson-fork)

## 2019-05-09
### redis版本特性
Redis 版本控制：major.minor.patchlevel。偶数的版本号表示稳定的版本， 
例如 1.2，2.0，2.2，2.4，2.6，2.8，奇数的版本号用来表示非标准版本,例如2.9.x是非稳定版本，它的稳定版本是3.0。

[Redis重大版本（2.6、2.8、3.0、3.2、4.0）](https://www.cnblogs.com/xingzc/p/9546849.html)
[Redis公布了5.0版本12项新特性](https://www.sohu.com/a/233717966_268033)

### redis中的事务

- [redis中的事务](https://www.cnblogs.com/huxinga/p/6502118.html)
- [redis.io transactions](https://redis.io/topics/transactions)
- [redis.cn transactions](http://www.redis.cn/topics/transactions.html)

Redis 2.6.5+开始，服务器会对命令入队失败的情况进行记录，并在客户端调用 EXEC 命令时，拒绝执行并自动放弃这个事务。

在Redis 2.6.5以前，Redis只执行事务中那些入队成功的命令，而忽略那些入队失败的命令。 
而新的处理方式则使得在流水线（pipeline）中包含事务变得简单，因为发送事务和读取事务的回复都只需要和服务器进行一次通讯。
```
# windows10 redis-server 3.2.100

127.0.0.1:6379> multi
OK
127.0.0.1:6379> set a 1
QUEUED
127.0.0.1:6379> hget a f
QUEUED
127.0.0.1:6379> set a 2
QUEUED
127.0.0.1:6379> exec
1) OK
2) (error) WRONGTYPE Operation against a key holding the wrong kind of value
3) OK
127.0.0.1:6379> get a
"2"
```

> It's important to note that **even when a command fails, all the other commands in the queue are processed** – 
> Redis will not stop the processing of commands.
> 最重要的是记住这样一条， 即使事务中有某条/某些命令执行失败了， 事务队列中的其他命令仍然会继续执行 —— Redis 不会停止执行事务中的命令。

通过在3.2.100中的测试，只要命令成功加入queue，就会被执行。
之前成功执行的命令并不会回滚，**Redis does not support roll backs（redis不支持回滚）**



