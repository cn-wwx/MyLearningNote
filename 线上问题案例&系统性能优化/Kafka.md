# 消息堆积排查

问题场景：

项目中某 kafka 消息组消费特别慢，有时候在 kafka-manager 控制台看到有些消费者已被踢出消费组。

从服务端日志看到如下信息：

![图片](Kafka.assets/640)

该消费组在短时间内重平衡了 600 多次。

从 cat 查看得知，每条消息处理都会有 4 次数据库的交互，经过一番沟通之后，发现每条消息的处理耗时大概率保持在 200ms 以上。

Kafka 发生重平衡的有以下几种情况：

1. 消费组成员发生变更，有新消费者加入或者离开，或者有消费者崩溃；
2. 消费组订阅的主题数量发生变更；
3. 消费组订阅的分区数发生变更。

在第 2、3 点都没有发生的情况下，那么就是由消费组成员发生了变化导致 Kafka 发生重平衡。

在查看 kafka 客户端日志，发现有很多如下日志：

![图片](Kafka.assets/640)

日志的描述得知，消费者被被剔除的原因是调用 poll() 方法消费耗时太久了，其中有提到 max.poll.interval.ms 和 max.poll.records 两个参数，而且还会导致提交

max.poll.interval.ms 表示消费者处理消息逻辑的最大时间，对于某些业务来说，处理消息可能需要很长时间，比如需要 1 分钟，那么该参数就需要设置成大于 1分钟的值，否则就会被 Coordinator 剔除消息组然后重平衡， 默认值为 300000；

max.poll.records 表示每次默认拉取消息条数，默认值为 500。

我们来计算一下：

200 * 500 = 100000 < max.poll.interval.ms =300000，

前面我也讲了，当每条消息处理时间大概率会超过 200ms。

结论：

本次出现的问题是由于客户端的消息消费逻辑耗时太长，如果生产端出现消息发送增多，消费端每次都拉取了 500 条消息进行消费，这时就很容易导致消费时间过长，如果超过了 max.poll.interval.ms 所设置的时间，就会被消费组所在的 coordinator 剔除掉，从而导致重平衡，Kafka 重平衡过程中是不能消费的，会导致消费组处于类似 stop the world 的状态下，重平衡过程中也不能提交位移，这会导致消息重复消费从而使得消费组的消费速度下降，导致消息堆积。

解决办法：

**根据业务逻辑调整 max.poll.records 与 max.poll.interval.ms 之间的平衡点，避免出现消费者被频繁踢出消费组导致重平衡。**



# Kafka 重启失败问题排查

问题场景：

日志 kafka 集群 A 主题 的 34 分区选举不了 leader，导致某些消息发送到该分区时，会报如下 no leader 的错误信息：

```java
In the middle of a leadership election, there is currently no leader for this partition and hence it is unavailable for writes.
```

接下来运维在 kafka-manager 查不到 broker0 节点了处于假死状态，但是进程依然还在，重启了好久没见反应，然后通过 kill -9 命令杀死节点进程后，接着重启失败了，导致了如下问题：

由于 A 主题 34 分区的 leader 副本在 broker0，另外一个副本由于速度跟不上 leader，已被踢出 ISR，0.11 版本的 kafka 的 unclean.leader.election.enable 参数默认为 false，表示分区不可在 ISR 以外的副本选举 leader，导致了 A 主题发送消息持续报 34 分区 leader 不存在的错误，且该分区还未消费的消息不能继续消费了。



后续集群的优化：

1. 制定一个升级方案，将集群升级到 2.x 版本；
2. 每个节点的服务器将 systemd 的默认超时值为 600 秒，因为我发现运维在故障当天关闭 33 节点时长时间没反应，才会使用 kill -9 命令强制关闭。但据我了解关闭一个 Kafka 服务器时，Kafka 需要做很多相关工作，这个过程可能会存在相当一段时间，而 systemd 的默认超时值为 90 秒即可让进程停止，那相当于非正常退出了；
3. 将 broker 参数 unclean.leader.election.enable 设置为 true（确保分区可从非 ISR 中选举 leader）；
4. 将 broker 参数 default.replication.factor 设置为 3（提高高可用，但会增大集群的存储压力，可后续讨论）；
5. 将 broker 参数 min.insync.replicas 设置为 2（这么做可确保 ISR 同时有两个，但是这么做会造成性能损失，是否有必要？因为我们已经将 unclean.leader.election.enable 设置为 true 了）；
6. 发送端发送 acks=1（确保发送时有一个副本是同步成功的，但这个是否有必要，因为可能会造成性能损失）。