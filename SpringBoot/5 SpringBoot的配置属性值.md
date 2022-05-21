# SpringBoot的配置属性值是如何绑定的

在配置Web服务器端口时，通过在applicatonContext.properties配置文件配置

```yam
server.port = 8081
```

即可将服务器端口设置为8081。

在SpringBoot中，配置外部属性值主要是通过 @EnableConfigurationProperties 和 @ConfigurationProperties 两个注解来完成的，因此从这个两个注解的源码进行分析



# 1 @EnableConfigurationProperties

先来看看ServerProperties的源码，可以看到ServerProperties上标注了@ConfigurationProperties注解，prefix属性为“server”

```java
@ConfigurationProperties(prefix = "server", ignoreUnknownFields = true)
public class ServerProperties {

   /**
    * Server HTTP port.
    */
   private Integer port;
}
```

再来看看 @ConfigurationProperties的源码

```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigurationProperties {

   @AliasFor("prefix")
   String value() default "";

   @AliasFor("value")
   String prefix() default "";

   boolean ignoreInvalidFields() default false;

   boolean ignoreUnknownFields() default true;

}
```

此注解的作用就是将外部配置的配置值绑定到其注解的类的属性上，这个注解没有其他任何的处理逻辑，因此这是一个标志性注解。

由于 ServerProperties 是位于 org.springframework.boot.autoconfigure.web 包下的，在此包下找一下需要通过 ServerProperties 绑定的配置类 ServletWebServerFactoryAutoConfiguration 的源码

```java
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(ServletRequest.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(ServerProperties.class)
@Import({ ServletWebServerFactoryAutoConfiguration.BeanPostProcessorsRegistrar.class,
		ServletWebServerFactoryConfiguration.EmbeddedTomcat.class,
		ServletWebServerFactoryConfiguration.EmbeddedJetty.class,
		ServletWebServerFactoryConfiguration.EmbeddedUndertow.class })
public class ServletWebServerFactoryAutoConfiguration {
 	...   
}
```

可以看到 @EnableConfigurationProperties 注解引入了 ServerProperties 类，因此可以看看这个注解的源码

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EnableConfigurationPropertiesImportSelector.class)
public @interface EnableConfigurationProperties {

   /**
    * Convenient way to quickly register {@link ConfigurationProperties} annotated beans
    * with Spring. Standard Spring Beans will also be scanned regardless of this value.
    * @return {@link ConfigurationProperties} annotated beans to register
    */
   Class<?>[] value() default {};

}
```

 注释说的很清楚，@EnableConfigurationProperties 主要作用就是为标注了 @ConfigurationProperties 的类提供支持，将外部配置属性值（配置文件）绑定到 @ConfigurationProperties 标注的类的属性中

还可以发现 @EnableConfigurationProperties 引入了 EnableConfigurationPropertiesImportSelector 这个类，说明这个类应该对属性绑定提供了支持，因此接下来重点分析这个类



# 2 EnableConfigurationPropertiesImportSelector

先来分析 EnableConfigurationPropertiesImportSelector 的源码

```java
class EnableConfigurationPropertiesImportSelector implements ImportSelector {

   private static final String[] IMPORTS = {
         ConfigurationPropertiesBeanRegistrar.class.getName(),
         ConfigurationPropertiesBindingPostProcessorRegistrar.class.getName() };

   @Override
   public String[] selectImports(AnnotationMetadata metadata) {
      return IMPORTS;
   }

	@Override
	public String[] selectImports(AnnotationMetadata metadata) {
		return IMPORTS;
	}
}
```

EnableConfigurationPropertiesImportSelector 实现了 ImportSelector 的接口，此接口中只有一个 selectImports方法，此方法可以向容器中注册Bean。

EnableConfigurationPropertiesImportSelector  覆写的 selectImports方法，导入了两个类：ConfigurationPropertiesBeanRegistrar 和 ConfigurationPropertiesBindingPostProcessorRegistrar。由于没有在 EnableConfigurationPropertiesImportSelector 中找到关于配置属性绑定的逻辑，因此可以分析一下上述两个类



#  3 ConfigurationPropertiesBeanRegistrar

ConfigurationPropertiesBeanRegistrar 是 EnableConfigurationPropertiesImportSelector 的内部类，并实现了 ImportBeanDefinitionRegistrar 接口，覆写了 registerBeanDefinitions 方法。那么 ConfigurationPropertiesBeanRegistrar 这个类就是向容器中注册一些bean。

```java
// ConfigurationPropertiesBeanRegistrar$ConfigurationPropertiesBeanRegistrar.java

public static class ConfigurationPropertiesBeanRegistrar
			implements ImportBeanDefinitionRegistrar {
	@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata,
			BeanDefinitionRegistry registry) {
		// （1）getTypes 方法得到@EnableConfigurationProperties注解的所有属性值,返回的是一个Class集合
		// 比如@EnableConfigurationProperties(ServerProperties.class),那么得到的值是ServerProperties.class
		// （2）然后再将得到的@EnableConfigurationProperties注解的所有属性值注册到容器中
		getTypes(metadata).forEach((type) -> register(registry,
				(ConfigurableListableBeanFactory) registry, type));
	}
}
```

registerBeanDefinitions 方法做了两件事：

1、获取@EnableConfigurationProperties(ServerProperties.class)注解的属性值；

2、调用register将属性值xxxProperties注册到Spring容器中



# 4 ConfigurationPropertiesBindingPostProcessorRegistrar

顾名思义，这个类应该也是向Spring注册一些bean，直接看源码

```java
// ConfigurationPropertiesBindingPostProcessorRegistrar.java

public class ConfigurationPropertiesBindingPostProcessorRegistrar
		implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {
		// 若容器中没有注册ConfigurationPropertiesBindingPostProcessor这个处理属性绑定的后置处理器，
		// 那么将注册ConfigurationPropertiesBindingPostProcessor和ConfigurationBeanFactoryMetadata这两个bean
		// 注意onApplicationEnvironmentPreparedEvent事件加载配置属性在先，然后再注册一些后置处理器用来处理这些配置属性
		if (!registry.containsBeanDefinition(
				ConfigurationPropertiesBindingPostProcessor.BEAN_NAME)) {
			// (1)注册ConfigurationPropertiesBindingPostProcessor后置处理器，用来对配置属性进行后置处理
			registerConfigurationPropertiesBindingPostProcessor(registry);
			// (2)注册一个ConfigurationBeanFactoryMetadata类型的bean，
			// 注意ConfigurationBeanFactoryMetadata实现了BeanFactoryPostProcessor，然后其会在postProcessBeanFactory中注册一些元数据
			registerConfigurationBeanFactoryMetadata(registry);
		}
	}
	// 注册ConfigurationPropertiesBindingPostProcessor后置处理器
	private void registerConfigurationPropertiesBindingPostProcessor(
			BeanDefinitionRegistry registry) {
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(ConfigurationPropertiesBindingPostProcessor.class);
		definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(
				ConfigurationPropertiesBindingPostProcessor.BEAN_NAME, definition);

	}
	// 注册ConfigurationBeanFactoryMetadata后置处理器
	private void registerConfigurationBeanFactoryMetadata(
			BeanDefinitionRegistry registry) {
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(ConfigurationBeanFactoryMetadata.class);
		definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(ConfigurationBeanFactoryMetadata.BEAN_NAME,
				definition);
	}

}
```

从源码可以看出，ConfigurationPropertiesBindingPostProcessorRegistrar 这个类主要是注册 ConfigurationPropertiesBindingPostProcessor 和 ConfigurationBeanFactoryMetadata 两个类，那么可以接着对这两个类进行分析



# 5 ConfigurationBeanFactoryMetadata

此类实现了 BeanFactoryPostProcessor 接口，此接口提供了 postProcessBeanFactory 方法，用于对BeanFactory进行业务扩展，因此这个方法应该需要重点分析

```java
// ConfigurationBeanFactoryMetadata

public class ConfigurationBeanFactoryMetadata implements BeanFactoryPostProcessor {

	/**
	 * The bean name that this class is registered with.
	 */
	public static final String BEAN_NAME = ConfigurationBeanFactoryMetadata.class
			.getName();

	private ConfigurableListableBeanFactory beanFactory;
	/**
	 * beansFactoryMetadata集合存储beansFactory的元数据
	 * key:某个bean的名字  value：FactoryMetadata对象（封装了工厂bean名和工厂方法名）
	 * 比如下面这个配置类：
	 *
	 * @Configuration
	 * public class ConfigA {
	 *      @Bean
	 *      public BeanXXX methodB（configA, ） {
	 *          return new BeanXXX();
	 *      }
	 * }
	 *
	 * 那么：key值为"methodB"，value为FactoryMetadata（configA, methodB）对象，其bean属性值为"configA",method属性值为"methodB"
	 */
	private final Map<String, FactoryMetadata> beansFactoryMetadata = new HashMap<>();

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
			throws BeansException {
		this.beanFactory = beanFactory;
		// 遍历beanFactory的beanDefinitionName，即每个bean的名字（比如工厂方法对应的bean名字）
		for (String name : beanFactory.getBeanDefinitionNames()) {
			// 根据name得到beanDefinition
			BeanDefinition definition = beanFactory.getBeanDefinition(name);
			// 工厂方法名：一般是注解@Bean的方法名
			String method = definition.getFactoryMethodName();
			// 工厂bean名：一般是注解@Configuration的类名
			String bean = definition.getFactoryBeanName();
			if (method != null && bean != null) {
				// 将beanDefinitionName作为Key，封装了工厂bean名和工厂方法名的FactoryMetadata对象作为value装入beansFactoryMetadata中
				this.beansFactoryMetadata.put(name, new FactoryMetadata(bean, method));
			}
		}
	}
}
```

从代码可以看出，postProcessBeanFactory 就是将工厂Bean和@Bean注解的方法封装缓存到beansFactoryMetadata中，以便后续使用



# 6 ConfigurationPropertiesBindingPostProcessor

这个后置处理器比较重要，主要承担着将外部配置属性绑定到 xxxProperties 类的属性中

```java
// ConfigurationPropertiesBindingPostProcessor.java

public class ConfigurationPropertiesBindingPostProcessor implements BeanPostProcessor,
	PriorityOrdered, ApplicationContextAware, InitializingBean {
	@Override
	public void afterPropertiesSet() throws Exception {
	    // ...这里省略实现代码先
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
	    // ...这里省略实现代码先
	}

	// ...省略非关键代码
}
```

可以看到 ConfigurationPropertiesBindingPostProcessor 后置处理器实现了两个重要的接口 InitializingBean 和 BeanPostProcessor



 InitializingBean 接口的 afterPropertiesSet 方法会在 bean 属性赋值后调用，用来执行一些自定义的初始化逻辑

 BeanPostProcessor 接口是 bean 的后置处理器，其有 postProcessBeforeInitialization 和  postProcessAfterInitialization 两个方法，分别会在 bean 初始化前后被调用来执行一些后置处理逻辑。



因此，接下来分析两个被覆写的方法



afterPropertiesSet方法

```java
// ConfigurationPropertiesBindingPostProcessor.java

        /**
	 * 配置属性校验器名字
	 */
	public static final String VALIDATOR_BEAN_NAME = "configurationPropertiesValidator";
	/**
	 * 工厂bean相关元数据
	 */
	private ConfigurationBeanFactoryMetadata beanFactoryMetadata;
	/**
	 * 上下文
	 */
	private ApplicationContext applicationContext;
	/**
	 * 配置属性绑定器
	 */
	private ConfigurationPropertiesBinder configurationPropertiesBinder;


    // 这里主要是给beanFactoryMetadata和configurationPropertiesBinder的属性赋值，用于后面的后置处理器方法处理属性绑定的时候用
	@Override
	public void afterPropertiesSet() throws Exception {
		// We can't use constructor injection of the application context because
		// it causes eager factory bean initialization
		// 【1】利用afterPropertiesSet这个从容器中获取之前注册的ConfigurationBeanFactoryMetadata对象赋给beanFactoryMetadata属性
		// （问1）beanFactoryMetadata这个bean是什么时候注册到容器中的？
		// （答1）在ConfigurationPropertiesBindingPostProcessorRegistrar类的registerBeanDefinitions方法中将beanFactoryMetadata这个bean注册到容器中
		// （问2）从容器中获取beanFactoryMetadata对象后，什么时候会被用到？
		// （答2）beanFactoryMetadata对象的beansFactoryMetadata集合保存的工厂bean相关的元数据，在ConfigurationPropertiesBindingPostProcessor类
		//        要判断某个bean是否有FactoryAnnotation或FactoryMethod时会根据这个beanFactoryMetadata对象的beansFactoryMetadata集合的元数据来查找
		this.beanFactoryMetadata = this.applicationContext.getBean(
				ConfigurationBeanFactoryMetadata.BEAN_NAME,
				ConfigurationBeanFactoryMetadata.class);
		// 【2】new一个ConfigurationPropertiesBinder，用于后面的外部属性绑定时使用
		this.configurationPropertiesBinder = new ConfigurationPropertiesBinder(
				this.applicationContext, VALIDATOR_BEAN_NAME); // VALIDATOR_BEAN_NAME="configurationPropertiesValidator"
	}
```

此方法的主要逻辑是：在进行外部配置属性绑定前，准备好相关的数据和数据绑定器

看看数据绑定器的源码

```java
// ConfigurationPropertiesBinder.java

ConfigurationPropertiesBinder(ApplicationContext applicationContext,
		String validatorBeanName) {
	this.applicationContext = applicationContext;
	// 将applicationContext封装到PropertySourcesDeducer对象中并返回
	this.propertySources = new PropertySourcesDeducer(applicationContext)
			.getPropertySources(); // 获取属性源
	// 如果没有配置validator的话，这里一般返回的是null
	this.configurationPropertiesValidator = getConfigurationPropertiesValidator(
			applicationContext, validatorBeanName);
	// 检查实现JSR-303规范的bean校验器相关类在classpath中是否存在
	this.jsr303Present = ConfigurationPropertiesJsr303Validator
			.isJsr303Present(applicationContext);
}
```



在看看另一个被覆写的方法

```java
// ConfigurationPropertiesBindingPostProcessor.java

// 因为是外部配置属性后置处理器，因此这里对@ConfigurationProperties注解标注的XxxProperties类进行后置处理完成属性绑定
@Override
public Object postProcessBeforeInitialization(Object bean, String beanName)
		throws BeansException {
	// 【1】从bean上获取@ConfigurationProperties注解,若bean有标注，那么返回该注解；若没有，则返回Null。比如ServerProperty上标注了@ConfigurationProperties注解
	ConfigurationProperties annotation = getAnnotation(bean, beanName,
			ConfigurationProperties.class);
	// 【2】若标注有@ConfigurationProperties注解的bean，那么则进行进一步处理：将配置文件的配置注入到bean的属性值中
	if (annotation != null) {
		bind(bean, beanName, annotation);
	}
	// 【3】返回外部配置属性值绑定后的bean（一般是XxxProperties对象）
	return bean;
}
```

进入bind方法

```java
// ConfigurationPropertiesBindingPostProcessor.java

private void bind(Object bean, String beanName, ConfigurationProperties annotation) {
	// 【1】得到bean的类型，比如ServerPropertie这个bean得到的类型是：org.springframework.boot.autoconfigure.web.ServerProperties
	ResolvableType type = getBeanType(bean, beanName);
	// 【2】获取bean上标注的@Validated注解
	Validated validated = getAnnotation(bean, beanName, Validated.class);
	// 若标注有@Validated注解的话则跟@ConfigurationProperties注解一起组成一个Annotation数组
	Annotation[] annotations = (validated != null)
			? new Annotation[] { annotation, validated }
			: new Annotation[] { annotation };
	// 【3】返回一个绑定了XxxProperties类的Bindable对象target，这个target对象即被外部属性值注入的目标对象
	// （比如封装了标注有@ConfigurationProperties注解的ServerProperties对象的Bindable对象）
	Bindable<?> target = Bindable.of(type).withExistingValue(bean)
			.withAnnotations(annotations); // 设置annotations属性数组
	try {
		// 【4】执行外部配置属性绑定逻辑
		this.configurationPropertiesBinder.bind(target);
	}
	catch (Exception ex) {
		throw new ConfigurationPropertiesBindException(beanName, bean, annotation,
				ex);
	}
}
```

继续进入bind方法

```java
// ConfigurationPropertiesBinder.java

public void bind(Bindable<?> target) {
	//【1】得到@ConfigurationProperties注解
	ConfigurationProperties annotation = target
			.getAnnotation(ConfigurationProperties.class);
	Assert.state(annotation != null,
			() -> "Missing @ConfigurationProperties on " + target);
	// 【2】得到Validator对象集合，用于属性校验
	List<Validator> validators = getValidators(target);
	// 【3】得到BindHandler对象（默认是IgnoreTopLevelConverterNotFoundBindHandler对象），
	// 用于对ConfigurationProperties注解的ignoreUnknownFields等属性的处理
	BindHandler bindHandler = getBindHandler(annotation, validators);
	// 【4】得到一个Binder对象，并利用其bind方法执行外部属性绑定逻辑
	getBinder().bind(annotation.prefix(), target, bindHandler);
}
```

从源码中可以看到，在分析bind方法前，看看传入的bindHandler是干啥的

BindHandler 是一个父接口，主要用于绑定属性时处理一些逻辑；BindHandler 接口定义了 onStart、onSuccess、onFailure、onFinish四个方法，这个四个方法分别在执行属性绑定时的不同时机被调用。

先来看看getBindHandler方法

```器
// ConfigurationPropertiesBinder.java

// 注意BindHandler的设计技巧，应该是责任链模式，非常巧妙，值得借鉴
private BindHandler getBindHandler(ConfigurationProperties annotation,
		List<Validator> validators) {
	// 新建一个IgnoreTopLevelConverterNotFoundBindHandler对象，这是个默认的BindHandler对象
	BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();
	// 若注解@ConfigurationProperties的ignoreInvalidFields属性设置为true，
	// 则说明可以忽略无效的配置属性例如类型错误，此时新建一个IgnoreErrorsBindHandler对象
	if (annotation.ignoreInvalidFields()) {
		handler = new IgnoreErrorsBindHandler(handler);
	}
	// 若注解@ConfigurationProperties的ignoreUnknownFields属性设置为true，
	// 则说明配置文件配置了一些未知的属性配置，此时新建一个ignoreUnknownFields对象
	if (!annotation.ignoreUnknownFields()) {
		UnboundElementsSourceFilter filter = new UnboundElementsSourceFilter();
		handler = new NoUnboundElementsBindHandler(handler, filter);
	}
	// 如果@Valid注解不为空，则创建一个ValidationBindHandler对象
	if (!validators.isEmpty()) {
		handler = new ValidationBindHandler(handler,
				validators.toArray(new Validator[0]));
	}
	// 遍历获取的ConfigurationPropertiesBindHandlerAdvisor集合，
	// ConfigurationPropertiesBindHandlerAdvisor目前只在测试类中有用到
	for (ConfigurationPropertiesBindHandlerAdvisor advisor : getBindHandlerAdvisors()) {
		// 对handler进一步处理
		handler = advisor.apply(handler);
	}
	// 返回handler
	return handler;
}
```

其逻辑主要是：根据传入 @ConfigurationProperties 注解和 validator 校验器来创建不同的 BindHandler 具体实现类。



返回getBinder().bind(annotation.prefix(), target, bindHandler)这句代码，这句代码主要做了两件事：

1、调用getBinder获取绑定属性的Binder对象

2、调用Binder对象的bind方法进行外部属性绑定，绑定到被@ConfigurationProperties注解的xxxProperties类的属性中

分析一下getBinder方法源码

```java
// ConfigurationPropertiesBinder.java

private Binder getBinder() {
	// Binder是一个能绑定ConfigurationPropertySource的容器对象
	if (this.binder == null) {
		// 新建一个Binder对象，这个binder对象封装了ConfigurationPropertySources，
		// PropertySourcesPlaceholdersResolver，ConversionService和PropertyEditorInitializer对象
		this.binder = new Binder(getConfigurationPropertySources(), // 将PropertySources对象封装成SpringConfigurationPropertySources对象并返回
				getPropertySourcesPlaceholdersResolver(), getConversionService(), // 将PropertySources对象封装成PropertySourcesPlaceholdersResolver对象并返回，从容器中获取到ConversionService对象
				getPropertyEditorInitializer()); // 得到Consumer<PropertyEditorRegistry>对象，这些初始化器用来配置property editors，property editors通常可以用来转换值
	}
	// 返回binder
	return this.binder;
}
```

binder对象封装了4个对象：

ConfigurationPropertySources：外部配置文件的属性源

PropertySourcesPlaceholdersResolver：解析配置文件中的占位符 ${}

ConversionService：类型转换

PropertyEditorInitializer：初始化属性编辑工作



获得Binder后，再看看bind方法

```java
// Binder.java

public <T> BindResult<T> bind(String name, Bindable<T> target, BindHandler handler) {
	// ConfigurationPropertyName.of(name)：将name（这里指属性前缀名）封装到ConfigurationPropertyName对象中
	// 将外部配置属性绑定到目标对象target中
	return bind(ConfigurationPropertyName.of(name), target, handler);
}

public <T> BindResult<T> bind(ConfigurationPropertyName name, Bindable<T> target,
		BindHandler handler) {
	Assert.notNull(name, "Name must not be null");
	Assert.notNull(target, "Target must not be null");
	handler = (handler != null) ? handler : BindHandler.DEFAULT;
	// Context是Binder的内部类，实现了BindContext，Context可以理解为Binder的上下文，可以用来获取binder的属性比如Binder的sources属性
	Context context = new Context();
	// 进行属性绑定，并返回绑定属性后的对象bound，注意bound的对象类型是T，T就是@ConfigurationProperties注解的类比如ServerProperties
	T bound = bind(name, target, handler, context, false);
	// 将刚才返回的bound对象封装到BindResult对象中并返回
	return BindResult.of(bound);
}


protected final <T> T bind(ConfigurationPropertyName name, Bindable<T> target,
		BindHandler handler, Context context, boolean allowRecursiveBinding) {
	// 清空Binder的configurationProperty属性值
	context.clearConfigurationProperty();
	try {
		// 【1】调用BindHandler的onStart方法，执行一系列的责任链对象的该方法
		target = handler.onStart(name, target, context);
		if (target == null) {
			return null;
		}// 【2】调用bindObject方法对Bindable对象target的属性进行绑定外部配置的值，并返回赋值给bound对象。
		// 举个栗子：比如设置了server.port=8888,那么该方法最终会调用Binder.bindProperty方法，最终返回的bound的value值为8888
		/************【主线：重点关注】***********/
		Object bound = bindObject(name, target, handler, context,
				allowRecursiveBinding);
		// 【3】封装handleBindResult对象并返回，注意在handleBindResult的构造函数中会调用BindHandler的onSucess，onFinish方法
		return handleBindResult(name, target, handler, context, bound);
	}
	catch (Exception ex) {
		return handleBindError(name, target, handler, context, ex);
	}
}
```

分bindObject源码

```java
// Binder.java

private <T> Object bindObject(ConfigurationPropertyName name, Bindable<T> target,
		BindHandler handler, Context context, boolean allowRecursiveBinding) {
	// 从propertySource中的配置属性，获取ConfigurationProperty对象property即application.properties配置文件中若有相关的配置的话，
	// 那么property将不会为null。举个栗子：假如你在配置文件中配置了spring.profiles.active=dev，那么相应property值为dev；否则为null
	ConfigurationProperty property = findProperty(name, context);
	// 若property为null，则不会执行后续的属性绑定相关逻辑
	if (property == null && containsNoDescendantOf(context.getSources(), name)) {
		// 如果property == null，则返回null
		return null;
	}
	// 根据target类型获取不同的Binder，可以是null（普通的类型一般是Null）,MapBinder,CollectionBinder或ArrayBinder
	AggregateBinder<?> aggregateBinder = getAggregateBinder(target, context);
	// 若aggregateBinder不为null比如配置了spring.profiles属性（当然包括其子属性比如spring.profiles.active等）
	if (aggregateBinder != null) {
		// 若aggregateBinder不为null，则调用bindAggregate并返回绑定后的对象
		return bindAggregate(name, target, handler, context, aggregateBinder);
	}
	// 若property不为null
	if (property != null) {
		try {
			// 绑定属性到对象中，比如配置文件中设置了server.port=8888，那么将会最终调用bindProperty方法进行属性设置
			return bindProperty(target, context, property);
		}
		catch (ConverterNotFoundException ex) {
			// We might still be able to bind it as a bean
			Object bean = bindBean(name, target, handler, context,
					allowRecursiveBinding);
			if (bean != null) {
				return bean;
			}
			throw ex;
		}
	}
	// 只有@ConfigurationProperties注解的类进行外部属性绑定才会走这里
	return bindBean(name, target, handler, context, allowRecursiveBinding);
}
```

由以上代码可看出bindObject执行属性绑定的逻辑会根据不同的属性类型进入不同的绑定逻辑



进入bindBean的源码

```java
// Binder.java

private Object bindBean(ConfigurationPropertyName name, Bindable<?> target, // name指的是ConfigurationProperties的前缀名
		BindHandler handler, Context context, boolean allowRecursiveBinding) {
	// 这里做一些ConfigurationPropertyState的相关检查
	if (containsNoDescendantOf(context.getSources(), name)
			|| isUnbindableBean(name, target, context)) {
		return null;
	}
    // 这里新建一个BeanPropertyBinder的实现类对象，注意这个对象实现了bindProperty方法
	BeanPropertyBinder propertyBinder = (propertyName, propertyTarget) -> bind(
			name.append(propertyName), propertyTarget, handler, context, false);
	/**
	 * (propertyName, propertyTarget) -> bind(
	 * 				name.append(propertyName), propertyTarget, handler, context, false);
	 * 	等价于
	 * 	new BeanPropertyBinder() {
	 *		Object bindProperty(String propertyName, Bindable<?> target){
	 *			bind(name.append(propertyName), propertyTarget, handler, context, false);
	 *		}
	 * 	}
	 */
	// type类型即@ConfigurationProperties注解标注的XxxProperties类
	Class<?> type = target.getType().resolve(Object.class);
	if (!allowRecursiveBinding && context.hasBoundBean(type)) {
		return null;
	}
	// 这里应用了java8的lambda语法，作为没怎么学习java8的lambda语法的我，不怎么好理解下面的逻辑
	// 真正实现将外部配置属性绑定到@ConfigurationProperties注解的XxxProperties类的属性中的逻辑应该就是在这句lambda代码了
	return context.withBean(type, () -> {
		Stream<?> boundBeans = BEAN_BINDERS.stream()
				.map((b) -> b.bind(name, target, context, propertyBinder));
		return boundBeans.filter(Objects::nonNull).findFirst().orElse(null);
	});
	// 根据上面的lambda语句翻译如下：
	/** 这里的T指的是各种属性绑定对象，比如ServerProperties
	 * return context.withBean(type, new Supplier<T>() {
	 * 	T get() {
	 * 		Stream<?> boundBeans = BEAN_BINDERS.stream()
	 * 					.map((b) -> b.bind(name, target, context, propertyBinder));
	 * 			return boundBeans.filter(Objects::nonNull).findFirst().orElse(null);
	 *        }
	 *  });
	 */
}
```

上面的代码应该是外部配置属性绑定到xxxProperties类属性中比较底层的代码了



# 7 小结

外部配置属性值绑定到xxxProperties中的步骤如下：

1. 首先是`@EnableConfigurationProperties`注解`import`了`EnableConfigurationPropertiesImportSelector`后置处理器；
2. `EnableConfigurationPropertiesImportSelector`后置处理器又向`Spring`容器中注册了`ConfigurationPropertiesBeanRegistrar`和`ConfigurationPropertiesBindingPostProcessorRegistrar`这两个`bean`；
3. 其中`ConfigurationPropertiesBeanRegistrar`向`Spring`容器中注册了`XxxProperties`类型的`bean`；`ConfigurationPropertiesBindingPostProcessorRegistrar`向`Spring`容器中注册了`ConfigurationBeanFactoryMetadata`和`ConfigurationPropertiesBindingPostProcessor`两个后置处理器；
4. `ConfigurationBeanFactoryMetadata`后置处理器在初始化`bean` `factory`时将`@Bean`注解的元数据存储起来，以便在后续的外部配置属性绑定的相关逻辑中使用；
5. `ConfigurationPropertiesBindingPostProcessor`后置处理器将外部配置属性值绑定到`XxxProperties`类属性的逻辑委托给`ConfigurationPropertiesBinder`对象，然后`ConfigurationPropertiesBinder`对象又最终将属性绑定的逻辑委托给`Binder`对象来完成。
