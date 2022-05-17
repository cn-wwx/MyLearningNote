# 搭建SpringBoot源码调试环境



# 1 从github上将SpirngBoot源码下载下来

这里选用Spring Boot v2.1.0.RELEASE 地址：https://github.com/spring-projects/spring-boot/tree/v2.1.0.RELEASE



# 2 使用IDEA打开源码

注意选择JDK版本为1.8，不然Maven加载失败



# 3 编译构建SpringBoot

导入项目后，在构建之前做这个配置：

禁用maven的代码检查，在跟 pom.xml 中增加一下配置

```xml
<properties>
    <revision>2.1.0.RELEASE</revision>
    <main.basedir>${basedir}</main.basedir>
    /* 新增下面这行*/
    <disable.checks>true</disable.checks>
</properties>
```



执行以下maven命令编译构建源码项目

```bash
mvn clean install -DskipTests -Pfast
```

![image-20220517184415265](C:/Users/27069/AppData/Roaming/Typora/typora-user-images/image-20220517184415265.png)



# 4 运行自带的sample

spring-boot-samples模块自带了很多demo样例，可以运行相应的sample来进行调试。

![image-20220517184546698](C:/Users/27069/AppData/Roaming/Typora/typora-user-images/image-20220517184546698.png)

发现spring-boot-samples是灰色的，原因是该模块没有添加到根 pom.xml中，增加如下配置

```xml
<modules>
    <module>spring-boot-project</module>
    <!-- Samples are built via the invoker plugin -->
    <module>spring-boot-samples-invoker</module>
    <module>spring-boot-samples</module>
    <module>spring-boot-tests</module>
</modules>
```

