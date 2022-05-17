# SpringBoot自动配置的条件注解原理

SpringBoot自动配置原理是基于大量的条件注解ConditionalOnXXX，因此首先来分析条件注解的源码



# 1 条件注解

SpringBoot的自动配置是要满足相应的条件才会进行自动配置，因此，SpringBoot的自动配置大量应用了条件注解，常用条件注解有：

- @ConditionalOnBean：容器内存在指定Bean
- @ConditionalOnClass：容器内存在指定Class
- @ConditionalOnMissingBean：容器内不存在指定Bean
- @ConditionalOnMissingClass：容器内不存在指定Class

...

查看一个派生注解的源码：

```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {

	Class<?>[] value() default {};

	/**
	 * The classes names that must be present.
	 * @return the class names that must be present.
	 */
	String[] name() default {};

}
```

可以看到 @ConditionalOnClass 注解上面又标注了 @Conditional(OnClassCondition.class)，因此 @ConditionalOnClass 是  @Conditional 的派生注解。

因此可以研究 Condition 接口的相关源码





# 2 Condition 接口

查看 Condition 接口的源码

```java
@FunctionalInterface
public interface Condition {
	boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);

}
```

`Condition` 接口主要有一个`matches`方法，该方法决定了是否要注册相应的`bean`对象（返回 true 才会创建 Bean）。其中`matches`方法中有两个参数，参数类型分别是`ConditionContext`和`AnnotatedTypeMetadata`，这两个参数非常重要。它们分别用来获取一些环境信息和注解元数据，从而用在`matches`方法中判断是否符合条件。



```java
public interface ConditionContext {
   //bean定义注册器
   BeanDefinitionRegistry getRegistry();
   //bean工厂
   @Nullable
   ConfigurableListableBeanFactory getBeanFactory();
   //容器环境信息
   Environment getEnvironment();
   //资源加载器
   ResourceLoader getResourceLoader();
   //类加载器
   @Nullable
   ClassLoader getClassLoader();

}

public interface AnnotatedTypeMetadata {
    boolean isAnnotated(String var1);

    @Nullable
    Map<String, Object> getAnnotationAttributes(String var1);

    @Nullable
    Map<String, Object> getAnnotationAttributes(String var1, boolean var2);

    @Nullable
    MultiValueMap<String, Object> getAllAnnotationAttributes(String var1);

    @Nullable
    MultiValueMap<String, Object> getAllAnnotationAttributes(String var1, boolean var2);
}
```



通过调试看调用的栈帧，发现matches方法是在 ConditionEvaluator 的 shouldSkip 方法中调用的

```java
// 这个方法主要是如果是解析阶段则跳过，如果是注册阶段则不跳过
public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
	// 若没有被@Conditional或其派生注解所标注，则不会跳过
	if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
		return false;
	}
	// 没有指定phase，注意phase可以分为PARSE_CONFIGURATION或REGISTER_BEAN类型
	if (phase == null) {
		// 若标有@Component，@Import，@Bean或@Configuration等注解的话，则说明是PARSE_CONFIGURATION类型
		if (metadata instanceof AnnotationMetadata &&
				ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
			return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
		}
		// 否则是REGISTER_BEAN类型
		return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
	}

	List<Condition> conditions = new ArrayList<>();
	// TODO 获得所有标有@Conditional注解或其派生注解里面的Condition接口实现类并实例化成对象。
	// 比如@Conditional(OnBeanCondition.class)则获得OnBeanCondition.class，OnBeanCondition.class往往实现了Condition接口
	for (String[] conditionClasses : getConditionClasses(metadata)) {
		// 将类实例化成对象
		for (String conditionClass : conditionClasses) {
			Condition condition = getCondition(conditionClass, this.context.getClassLoader());
			conditions.add(condition);
		}
	}
	// 排序，即按照Condition的优先级进行排序
	AnnotationAwareOrderComparator.sort(conditions);

	for (Condition condition : conditions) {
		ConfigurationPhase requiredPhase = null;
		if (condition instanceof ConfigurationCondition) {
			// 从condition中获得对bean是解析还是注册
			requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
		}
		// 若requiredPhase为null或获取的阶段类型正是当前阶段类型且不符合condition的matches条件，则跳过
		if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) 		{
			return true;
		}
	}

	return false;
}
```

上面代码最重要的逻辑是调用了`Condition`接口的具体实现类的`matches`方法，若`matches`返回`false`，则跳过，不进行注册`bean`的操作；若`matches`返回`true`，则不跳过，进行注册`bean`的操作



# 3 SpringBootCondition源码解析

SpringBootCondition的整体类图

![image-20220517204310391](C:/Users/27069/AppData/Roaming/Typora/typora-user-images/image-20220517204310391.png)

可以看到SpringBootCondition继承了Condition接口，然后又有很多具体实现类 OnXXXConditon，这些 OnXXXConditon 就是 @ConditionOnXXX 的条件类



查看SpringBootCondition源码,由于 SpringBootCondition 继承了Condition 接口，因此重点看 matches 方法。从源码可以看出，其主要作用就是打印一些条件注解的评估报告日志

```java
@Override
public final boolean matches(ConditionContext context,
      AnnotatedTypeMetadata metadata) {
   String classOrMethodName = getClassOrMethodName(metadata);
   try {
      // 判断每个配置类的每个条件注解@ConditionalOnXXX是否满足条件，然后记录到ConditionOutcome结果中
			// 注意getMatchOutcome是一个抽象模板方法，交给OnXXXCondition子类去实现
			ConditionOutcome outcome = getMatchOutcome(context, metadata);
			// 打印condition评估的日志，哪些条件注解@ConditionalOnXXX是满足条件的，哪些是不满足条件的，这些日志都打印出来
			logOutcome(classOrMethodName, outcome);
			// 除了打印日志外，这些是否匹配的信息还要记录到ConditionEvaluationReport中
			recordEvaluation(context, classOrMethodName, outcome);
			// 最后返回@ConditionalOnXXX是否满足条件
			return outcome.isMatch();
   }
   catch (NoClassDefFoundError ex) {
      throw new IllegalStateException(
            "Could not evaluate condition on " + classOrMethodName + " due to "
                  + ex.getMessage() + " not "
                  + "found. Make sure your own configuration does not rely on "
                  + "that class. This can also happen if you are "
                  + "@ComponentScanning a springframework package (e.g. if you "
                  + "put a @ComponentScan in the default package by mistake)",
            ex);
   }
   catch (RuntimeException ex) {
      throw new IllegalStateException(
            "Error processing condition on " + getName(metadata), ex);
   }
}
```

重点关注 getMatchOutcome 这个模板方法，由其子类进行实现



分析子类的源码

```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnResourceCondition.class)
public @interface ConditionalOnResource {

	/**
	 * The resources that must be present.
	 * @return the resource paths that must be present.
	 */
	String[] resources() default {};

}
```

再看 OnResourceCondition 的源码

```java
class OnResourceCondition extends SpringBootCondition {

	private final ResourceLoader defaultResourceLoader = new DefaultResourceLoader();

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		// 获得@ConditionalOnResource注解的属性元数据
		MultiValueMap<String, Object> attributes = metadata
				.getAllAnnotationAttributes(ConditionalOnResource.class.getName(), true);
		// 获得资源加载器，若ConditionContext中有ResourceLoader则用ConditionContext中的，没有则用默认的
		ResourceLoader loader = (context.getResourceLoader() != null)
				? context.getResourceLoader() : this.defaultResourceLoader;
		List<String> locations = new ArrayList<>();
		// 将@ConditionalOnResource中定义的resources属性值取出来装进locations集合
		collectValues(locations, attributes.get("resources"));
		Assert.isTrue(!locations.isEmpty(),
				"@ConditionalOnResource annotations must specify at "
						+ "least one resource location");
		// missing集合是装不存在指定资源的资源路径的
		List<String> missing = new ArrayList<>();
		// 遍历所有的资源路径，若指定的路径的资源不存在则将其资源路径存进missing集合中
		for (String location : locations) {
			// 这里针对有些资源路径是Placeholders的情况，即处理 ${}
			String resource = context.getEnvironment().resolvePlaceholders(location);
			if (!loader.getResource(resource).exists()) {
				missing.add(location);
			}
		}
		// 如果存在某个资源不存在，那么则报错
		if (!missing.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage
					.forCondition(ConditionalOnResource.class)
					.didNotFind("resource", "resources").items(Style.QUOTE, missing));
		}
		// 所有资源都存在，那么则返回能找到就提的资源
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnResource.class)
						.found("location", "locations").items(locations));
	}

	// 将@ConditionalOnResource中定义的resources属性值取出来装进locations集合
	private void collectValues(List<String> names, List<Object> values) {
		for (Object value : values) {
			for (Object item : (Object[]) value) {
				names.add((String) item);
			}
		}
	}
}
```

