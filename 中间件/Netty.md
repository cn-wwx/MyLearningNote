# Netty

## BIO、NIO、AIO的区别

![image-20220423143928280](../../../AppData/Roaming/Typora/typora-user-images/image-20220423143928280.png)



## 什么是Netty

1. 是一个基于NIO的CS框架，使用它可以快速开发网络应用程序
2. 优化了TCP、UDP Socket网络编程
3. 支持多种协议，FTP，SMTP，HTTP等



## 为什么不用直接使用NIO

NIO的编程模型复杂，而且存在一些BUG。NIO在面对断连重连、包丢失、粘包等问题时处理过程非常复杂。Netty很好的解决了以上问题



## Netty应用场景

网络通信：

1. 作为RPC框架的网络通信工具
2. 实现HTTP服务器
3. 实现一个即时通信系统：类似微信
4. 实现消息推送系统



一些使用Netty的开源项目：

1. Dubbo
2. RocketMQ
3. Elasticsearch



## Netty的核心组件

![image-20220423144941377](../../../AppData/Roaming/Typora/typora-user-images/image-20220423144941377.png)

### Bytebuf 字节容器

- Netty提供的字节容器，其内部是一个字节数组。
- 可以将Bytebuf看作是NEtty对Java NIO的ByteBuffer的封装和抽象



### Bootstrap 和 ServerBootstrap 启动引导类

Bootstrap 客户端的启动引导类/辅助类

```java
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            //创建客户端启动引导/辅助类：Bootstrap
            Bootstrap b = new Bootstrap();
            //指定线程模型
            b.group(group).
                    ......
            // 尝试建立连接
            ChannelFuture f = b.connect(host, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            // 优雅关闭相关线程组资源
            group.shutdownGracefully();
        }
```

ServerBootstrap 服务端的启动引导类/辅助类

```java
        // 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            //2.创建服务端启动引导/辅助类：ServerBootstrap
            ServerBootstrap b = new ServerBootstrap();
            //3.给引导类配置两大线程组,确定了线程模型
            b.group(bossGroup, workerGroup).
                   ......
            // 6.绑定端口
            ChannelFuture f = b.bind(port).sync();
            // 等待连接关闭
            f.channel().closeFuture().sync();
        } finally {
            //7.优雅关闭相关线程组资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
```



### Channel 网络操作抽象类

通过Channel可以进行I/O操作

一旦客户端成功连接服务端，就会新建一个Channel同该用户端进行绑定

```java
   //  通过 Bootstrap 的 connect 方法连接到服务端
   public Channel doConnect(InetSocketAddress inetSocketAddress) {
        CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                completableFuture.complete(future.channel());
            } else {
                throw new IllegalStateException();
            }
        });
        return completableFuture.get();
    }
```

常用的实现类：

- NioServerSocketChannel
- NioSocketChannel

可以与BIO模型中的ServerSocket以及Socket对应上



### EventLoop 事件循环

负责监听网络事件，并调用事件处理器进行相关I/O操作（读写）的处理



#### channel与EventLoop的关系

Channel为Netty网络操作（读写等操作）抽象类，EventLoop负责处理注册到其上的Channel的I/O操作，两者配合进行I/O操作



#### EventLoopGroup和EventLoop的关系

EventLoopGroup包含多个EventLoop（每一个EventLoop内部通常包含一个线程），它管理着所有的EventLoop的生命周期。

EventLoop处理的I/O事件将在它专有的Thread上被处理，即Thread和EventLoop属于1：1关系，从而保证线程安全

![image-20220423150532666](../../../AppData/Roaming/Typora/typora-user-images/image-20220423150532666.png)



#### ChannelHandler（消息处理器）和ChannelPipeline（ChannelHandler对象链表）

```java
        b.group(eventLoopGroup)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcResponse.class));
                        ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcRequest.class));
                        ch.pipeline().addLast(new KryoClientHandler());
                    }
                });
```

ChannelHandler 消息的具体处理器，主要负责处理客户端/服务端接收和发送的数据

![image-20220423151210570](../../../AppData/Roaming/Typora/typora-user-images/image-20220423151210570.png)

![image-20220423151352137](../../../AppData/Roaming/Typora/typora-user-images/image-20220423151352137.png)



#### ChannelFuture 操作执行结果

```java
public interface ChannelFuture extends Future<Void> {
    Channel channel();

    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> var1);
     ......

    ChannelFuture sync() throws InterruptedException;
}
```

Netty中所有I/O操作都为异步，不能立刻得到操作是否执行成功

可以通过ChannelFuture接口的addListener方法注册一个ChannelFutrueListener，当操作执行成功或者失败时，监听器会自动触发返回结果

```java
ChannelFuture f = b.connect(host, port).addListener(future -> {
  if (future.isSuccess()) {
    System.out.println("连接成功!");
  } else {
    System.err.println("连接失败!");
  }
}).sync();
```

还可以通过ChannelFuture的channel方法获取连接相关联的Channel

```java
Channel channel = f.channel();
```

还可以通过ChannelFutrue接口的sync方法让异步操作变成同步的

```java
//bind()是异步的，但是，你可以通过 sync()方法将其变为同步。
ChannelFuture f = b.bind(port).sync();
```



NioEventLoopGroup默认的构造函数会起多少线程

默认会起的线程数为：CPU核心数 * 2

```java
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.netty.channel.nio;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SelectStrategyFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class NioEventLoopGroup extends MultithreadEventLoopGroup {
    public NioEventLoopGroup() {
        this(0);
    }

    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor)null);
    }

    public NioEventLoopGroup(ThreadFactory threadFactory) {
        this(0, (ThreadFactory)threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory, SelectorProvider selectorProvider) {
        this(nThreads, threadFactory, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory, SelectorProvider selectorProvider, SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, new Object[]{selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject()});
    }

    public NioEventLoopGroup(int nThreads, Executor executor, SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, SelectorProvider selectorProvider, SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, new Object[]{selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject()});
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, SelectorProvider selectorProvider, SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, new Object[]{selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject()});
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, SelectorProvider selectorProvider, SelectStrategyFactory selectStrategyFactory, RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, new Object[]{selectorProvider, selectStrategyFactory, rejectedExecutionHandler});
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, SelectorProvider selectorProvider, SelectStrategyFactory selectStrategyFactory, RejectedExecutionHandler rejectedExecutionHandler, EventLoopTaskQueueFactory taskQueueFactory) {
        super(nThreads, executor, chooserFactory, new Object[]{selectorProvider, selectStrategyFactory, rejectedExecutionHandler, taskQueueFactory});
    }
		...
}

//调用父类MultithreadEventLoopGroup的构造函数
public abstract class MultithreadEventLoopGroup extends MultithreadEventExecutorGroup implements EventLoopGroup {
    
     static {
         //关键代码！！！！！！！！！！！！！
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    /**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
    }   
    
    
    
}

```



## Reactor线程模型

Reactor是一种经典的线程模型，Reactor模式基于事件驱动，适合处理海量I/O事件



### 单线程 Reactor

![image-20220423160032993](../../../AppData/Roaming/Typora/typora-user-images/image-20220423160032993.png)

优点：对系统资源消耗特别小，但是没办法支撑大量请求的应用场景并且处理请求的时间可能非常慢



### 多线程Reactor

![image-20220423160513405](../../../AppData/Roaming/Typora/typora-user-images/image-20220423160513405.png)

在并发数比较多（百万并发）的场景下，一个线程负责接收客户端请求就存在性能问题



### 主从多线程Reactor

![image-20220423160632728](../../../AppData/Roaming/Typora/typora-user-images/image-20220423160632728.png)





## Netty线程模型

基于Reactor模式设计开发

在Netty主要靠NioEventLoopGroup线程池来实现具体的线程模型

实现服务端，一般会初始化两个线程组：

- bossGroup：接收连接
- workerGroup：负责具体处理，交由对应的Handler处理



### 单线程模型

![image-20220423161031310](../../../AppData/Roaming/Typora/typora-user-images/image-20220423161031310.png)



### 多线程模型

一个Acceptor线程只负责监听客户端连接，一个NIO线程池负责具体处理：accept、read、decode、process、encode、send事件

```java
// 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup();
try {
  //2.创建服务端启动引导/辅助类：ServerBootstrap
  ServerBootstrap b = new ServerBootstrap();
  //3.给引导类配置两大线程组,确定了线程模型
  b.group(bossGroup, workerGroup)
    //......
```

![image-20220423161238381](../../../AppData/Roaming/Typora/typora-user-images/image-20220423161238381.png)

### 主从线程模型

从一个主线程NIO线程池中选择一个线程作为Acceptor线程，绑定监听端口，接收客户端的连接，其他线程负责后续的接入认证工作。连接建立完成后，Sub NIO线程池负责处理具体的I/O读写

```java
// 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
EventLoopGroup bossGroup = new NioEventLoopGroup();
EventLoopGroup workerGroup = new NioEventLoopGroup();
try {
  //2.创建服务端启动引导/辅助类：ServerBootstrap
  ServerBootstrap b = new ServerBootstrap();
  //3.给引导类配置两大线程组,确定了线程模型
  b.group(bossGroup, workerGroup)
    //......
```

![image-20220423161618252](../../../AppData/Roaming/Typora/typora-user-images/image-20220423161618252.png)



## Netty服务端和客户端的启动过程

### 服务端

```java
        // 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            //2.创建服务端启动引导/辅助类：ServerBootstrap
            ServerBootstrap b = new ServerBootstrap();
            //3.给引导类配置两大线程组,确定了线程模型
            b.group(bossGroup, workerGroup)
                    // (非必备)打印日志
                    .handler(new LoggingHandler(LogLevel.INFO))
                    // 4.指定 IO 模型
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            //5.可以自定义客户端消息的业务处理逻辑
                            p.addLast(new HelloServerHandler());
                        }
                    });
            // 6.绑定端口,调用 sync 方法阻塞知道绑定完成
            ChannelFuture f = b.bind(port).sync();
            // 7.阻塞等待直到服务器Channel关闭(closeFuture()方法获取Channel 的CloseFuture对象,然后调用sync()方法)
            f.channel().closeFuture().sync();
        } finally {
            //8.优雅关闭相关线程组资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
```

![image-20220423161826545](../../../AppData/Roaming/Typora/typora-user-images/image-20220423161826545.png)

### 客户端

```java
        //1.创建一个 NioEventLoopGroup 对象实例
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            //2.创建客户端启动引导/辅助类：Bootstrap
            Bootstrap b = new Bootstrap();
            //3.指定线程组
            b.group(group)
                    //4.指定 IO 模型
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            // 5.这里可以自定义消息的业务处理逻辑
                            p.addLast(new HelloClientHandler(message));
                        }
                    });
            // 6.尝试建立连接
            ChannelFuture f = b.connect(host, port).sync();
            // 7.等待连接关闭（阻塞，直到Channel关闭）
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
```

![image-20220423162256866](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162256866.png)

![image-20220423162305655](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162305655.png)



## TCP粘包/拆包

TCP粘包/拆包就是基于TCP发送数据时，出现了多个字符串“粘”在了一起，或一个字符串被“拆”开的问题



解决方法：

1. 使用Netty自带的解码器
   ![image-20220423162546090](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162546090.png)
2. 自定义序列化解码器
   ![image-20220423162642510](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162642510.png)



## Netty长连接、心跳机制



### TCP长连接与短链接

![image-20220423162804153](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162804153.png)

![image-20220423162829930](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162829930.png)

## Netty零拷贝

![image-20220423162953224](../../../AppData/Roaming/Typora/typora-user-images/image-20220423162953224.png)

