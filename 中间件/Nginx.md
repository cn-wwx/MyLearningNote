# Nginx

### 概述

*Nginx* (engine x) 是一个高性能、轻量级的[HTTP](https://baike.baidu.com/item/HTTP)和[反向代理](https://baike.baidu.com/item/反向代理/7793488)web服务器。其特点是占有内存少，[并发](https://baike.baidu.com/item/并发/11024806)能力强



特点：

1. 内存占用非常少
2. 高并发
3. 跨平台
4. 扩展性好
5. 安装使用简单
6. 稳定性好







### 功能

#### 静态资源服务器

可以将服务器上的静态文件通过HTTP协议展现给客户端

```nginx
server {
	// 监听的端口号
	listen       80;
	// server 名称
	server_name  localhost;

	// 匹配 api，将所有 :80/api 的请求指到指定文件夹
	location /api {
		root   /mnt/web/;
		// 默认打开 index.html
		index  index.html index.htm;
	}
}
```



#### 反向代理

客户端将请求发送至反向代理服务器，由反向代理服务器选择目标服务器，获取数据后返回给客户端。对外暴露的是反向代理服务器，隐藏了真实服务器IP地址。

Nginx通过反向代理实现网站的负载均衡

![image-20220426095734336](../../../AppData/Roaming/Typora/typora-user-images/image-20220426095734336.png)



#### 正向代理

客户端通过正向代理服务器访问目标服务器。正向代理“代理”的是客户端，客户端对目标服务器的访问是透明的。

如VPN

![image-20220426100030768](../../../AppData/Roaming/Typora/typora-user-images/image-20220426100030768.png)



#### 负载均衡

将接收到的客户端请求以一定的规则分配到服务器集群中的所有服务器。



策略：轮询、最少连接策略、IP绑定策略、权重策略、fair（第三方）、url_hash（第三方）

心跳检查机制



```nginx
轮询：每个请求按时间顺序逐一分配到每一台服务器；适用于性能相近的集群
upstream backserver {
  server 172.27.26.174:8099;
  server 172.27.26.175:8099;
  server 172.27.26.176:8099;
}

加权随机：适用于性能不等的集群中
upstream backserver {
  server 172.27.26.174:8099 weight=6;
  server 172.27.26.175:8099 weight=2;
  server 172.27.26.176:8099 weight=3;
}

IP哈希
upstream backserver {
  ip_hash;
  server 172.27.26.174:8099;
  server 172.27.26.175:8099;
  server 172.27.26.176:8099;
}

最小连接数
upstream backserver {
  least_conn;
  server 172.27.26.174:8099;
  server 172.27.26.175:8099;
  server 172.27.26.176:8099;
}
```







### 常用命令

````shell
./nginx 启动
./nginx -s stop停止
./nginx -s quit 安全推出
./nginx -s reload 重新加载配置文件
ps aux | grep nginx 查看进程
````



### 性能优化的常见方式

- 设置Nginx运行工作进程个数：一般设置为CPU核心数或核心数X2
- 开启Gzip压缩：可以使网站的静态文件在传输时进行压缩
- 设置单个worker进程允许客户端最大连接数
- 连接超时时间
- 设置缓存



### Nginx总体架构



### Nginx进程模型



### Nginx如何处理HTTP请求

