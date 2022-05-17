# 分析SpringBoot源码模块及结构



# 1 SpringBoot源码模块

主要有以下四个模块：

- spring-boot-project：实现了SpringBoot框架所有功能
- spring-boot-samples：提供了使用SpringBoot的各种demo，可以直接运行调试阅读源码
- spring-boot-sample-invoker：应该和samples模块有关，在根 pom.xml有如下注释：Samples are built via the invoker plugin
- spring-boot-test：测试模块



# 2 spring-boot-parent模块

- spring-boot-parent：这个模块没有代码，是spring-boot模块的夫项目，被其他子模块继承
- spring-boot：核心模块，实现了以下核心功能
  - SpringApplication类，提供 run 方法来启动程序，用来创建并刷新Spring容器
  - 内置了一些生命周期事件和容器初始化器，来执行一些SpringBoot启动时的初始化逻辑
  - 外部配置
- spring-boot-autoconfigure：与SpringBoot的自动配置有关，关键注解有 @EnableAutoConfiguration 、 @Conditional
- spirng-boot-starters：SpringBoot的启动器，通过提供启动器降低项目依赖的复杂度，利用maven项目模型将某个组件用到的依赖聚合起来，避免用户在引入依赖时出现各种版本冲突

...





