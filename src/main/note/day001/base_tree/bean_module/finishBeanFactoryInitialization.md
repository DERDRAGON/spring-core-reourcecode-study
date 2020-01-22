- [finishBeanFactoryInitialization](#finishbeanfactoryinitialization)
  - [ConversionService](#conversionservice)
  - [StringValueResolver](#stringvalueresolver)
  - [LoadTimeWeaverAware](#loadtimeweaveraware)
  - [初始化](#%e5%88%9d%e5%a7%8b%e5%8c%96)
    - [getBean](#getbean)
      - [beanName转化](#beanname%e8%bd%ac%e5%8c%96)
      - [手动注册bean检测](#%e6%89%8b%e5%8a%a8%e6%b3%a8%e5%86%8cbean%e6%a3%80%e6%b5%8b)
      - [检查父容器](#%e6%a3%80%e6%9f%a5%e7%88%b6%e5%ae%b9%e5%99%a8)
      - [依赖初始化](#%e4%be%9d%e8%b5%96%e5%88%9d%e5%a7%8b%e5%8c%96)
      - [Singleton初始化](#singleton%e5%88%9d%e5%a7%8b%e5%8c%96)
      - [Prototype初始化](#prototype%e5%88%9d%e5%a7%8b%e5%8c%96)
      - [其它Scope初始化](#%e5%85%b6%e5%ae%83scope%e5%88%9d%e5%a7%8b%e5%8c%96)

# finishBeanFactoryInitialization

```
org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // Initialize conversion service for this context.
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
            beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
                beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }
    // Register a default embedded value resolver if no bean post-processor
    // (such as a PropertyPlaceholderConfigurer bean) registered any before:
    // at this point, primarily for resolution in annotation attribute values.
    if (!beanFactory.hasEmbeddedValueResolver()) {
        //StringValueResolver
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }
    // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }
    // Stop using the temporary ClassLoader for type matching.
    beanFactory.setTempClassLoader(null);
    // Allow for caching all bean definition metadata, not expecting further changes.
    beanFactory.freezeConfiguration();
    // Instantiate all remaining (non-lazy-init) singletons.
    beanFactory.preInstantiateSingletons();
}
```

## ConversionService
此接口用于类型之间的转换，在Spring里其实就是把配置文件中的String转为其它类型，从3.0开始出现，目的和jdk的PropertyEditor接口是一样的，参考ConfigurableBeanFactory.setConversionService注释:
`Specify a Spring 3.0 ConversionService to use for converting property values, as an alternative to JavaBeans PropertyEditors. @since 3.0`

## StringValueResolver
用于解析注解的值。接口只定义了一个方法:
`String resolveStringValue(String strVal);`

## LoadTimeWeaverAware
实现了此接口的bean可以得到LoadTimeWeaver，此处仅仅初始化。

## 初始化
```
DefaultListableBeanFactory.preInstantiateSingletons:
public void preInstantiateSingletons() throws BeansException {
    if (logger.isTraceEnabled()) {
        logger.trace("Pre-instantiating singletons in " + this);
    }
    // Iterate over a copy to allow for init methods which in turn register new bean definitions.
    // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);
    // Trigger initialization of all non-lazy singleton beans...
    // 首先进行Singleton的初始化
    for (String beanName : beanNames) {
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            if (isFactoryBean(beanName)) {
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                // 如果bean是FactoryBean类型(注意，只定义了factory-method属性的普通bean并不是FactoryBean)，并且还是SmartFactoryBean类型，
                // 那么需要判断是否需要eagerInit(isEagerInit是此接口定义的方法)。
                if (bean instanceof FactoryBean) {
                    final FactoryBean<?> factory = (FactoryBean<?>) bean;
                    boolean isEagerInit;
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
                                        ((SmartFactoryBean<?>) factory)::isEagerInit,
                                getAccessControlContext());
                    }
                    else {
                        isEagerInit = (factory instanceof SmartFactoryBean &&
                                ((SmartFactoryBean<?>) factory).isEagerInit());
                    }
                    if (isEagerInit) {
                        // getBean
                        // 这里便是bean初始化的核心逻辑。源码比较复杂，分开说。以getBean(String name)为例。AbstractBeanFactory.getBean:
                        getBean(beanName); ⬇️
                    }
                }
            }
            else {
                getBean(beanName); ⬇️
            }
        }
    }
    // Trigger post-initialization callback for all applicable beans...
    for (String beanName : beanNames) {
        Object singletonInstance = getSingleton(beanName);
        if (singletonInstance instanceof SmartInitializingSingleton) {
            final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    smartSingleton.afterSingletonsInstantiated();
                    return null;
                }, getAccessControlContext());
            }
            else {
                smartSingleton.afterSingletonsInstantiated();
            }
        }
    }
}
```

### getBean
这里便是bean初始化的核心逻辑。源码比较复杂，分开说。以getBean(String name)为例。AbstractBeanFactory.getBean:
```
@Override
public Object getBean(String name) throws BeansException {
    // 第二个参数表示bean的Class类型，第三个表示创建bean需要的参数，最后一个表示不需要进行类型检查。
    return doGetBean(name, null, null, false);
}
org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
    final String beanName = transformedBeanName(name); ⬇️
    Object bean;
    // Eagerly check singleton cache for manually registered singletons.
    Object sharedInstance = getSingleton(beanName);
    if (sharedInstance != null && args == null) {
        if (logger.isTraceEnabled()) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
                        "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }
    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }
        // Check if bean definition exists in this factory.
        // 检查父容器 ⬇️
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                        nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else if (requiredType != null) {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
            else {
                return (T) parentBeanFactory.getBean(nameToLookup);
            }
        }
        if (!typeCheckOnly) {
            markBeanAsCreated(beanName);
        }
        try {
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            checkMergedBeanDefinition(mbd, beanName, args);
            // Guarantee initialization of beans that the current bean depends on.
            // 依赖初始化 ⬇️
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dep : dependsOn) {
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    registerDependentBean(dep, beanName);
                    try {
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }
            // Create bean instance.
            // Singleton初始化 ⬇️
            if (mbd.isSingleton()) {
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }
            else if (mbd.isPrototype()) {
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    beforePrototypeCreation(beanName);
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }
            else {
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                            "Scope '" + scopeName + "' is not active for the current thread; consider " +
                            "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                            ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }
    // Check if required type matches the type of the actual bean instance.
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to convert bean '" + name + "' to required type '" +
                        ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}
```
#### beanName转化
`final String beanName = transformedBeanName(name);`
这里是将FactoryBean的前缀去掉以及将别名转为真实的名字。

#### 手动注册bean检测
Spring其实手动注册了一些单例bean。这一步就是检测是不是这些bean。如果是，那么再检测是不是工厂bean，如果是返回其工厂方法返回的实例，如果不是返回bean本身。
```
Object sharedInstance = getSingleton(beanName);
if (sharedInstance != null && args == null) {
    bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
}
```
#### 检查父容器
如果父容器存在并且存在此bean定义，那么交由其父容器初始化:
```
BeanFactory parentBeanFactory = getParentBeanFactory();
if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
    // Not found -> check parent.
    //此方法其实是做了前面beanName转化的逆操作，因为父容器同样会进行转化操作
    String nameToLookup = originalBeanName(name);
    if (args != null) {
        // Delegation to parent with explicit args.
        return (T) parentBeanFactory.getBean(nameToLookup, args);
    } else {
        // No args -> delegate to standard getBean method.
        return parentBeanFactory.getBean(nameToLookup, requiredType);
    }
}
```

#### 依赖初始化
bean可以由depends-on属性配置依赖的bean。Spring会首先初始化依赖的bean。
```
String[] dependsOn = mbd.getDependsOn();
if (dependsOn != null) {
    for (String dependsOnBean : dependsOn) {
         //检测是否存在循环依赖
        if (isDependent(beanName, dependsOnBean)) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName,
            "Circular depends-on relationship between '" + beanName + "' and '" + dependsOnBean + "'");
        }
        registerDependentBean(dependsOnBean, beanName);
        getBean(dependsOnBean);
    }
}
```
registerDependentBean进行了依赖关系的注册，这么做的原因是Spring在即进行bean销毁的时候会首先销毁被依赖的bean。
依赖关系的保存是通过一个ConcurrentHashMap<String, Set>完成的，key是bean的真实名字。

#### Singleton初始化
这里大纲是Singleton初始化，但是getBean方法本身是包括所有scope的初始化，在这里一次说明了。
```
if (mbd.isSingleton()) {
    sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
        @Override
        public Object getObject() throws BeansException {
            return createBean(beanName, mbd, args);
        }
    });
    bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
}
```
**getSingleton方法**
1、首先会检测是否已经存在，如果存在，直接返回:
```
synchronized (this.singletonObjects) {
    Object singletonObject = this.singletonObjects.get(beanName);
}
```
所有的单例bean都保存在这样的数据结构中: ConcurrentHashMap<String, Object>。
2、bean创建
```
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {
    if (logger.isTraceEnabled()) {
        logger.trace("Creating instance of bean '" + beanName + "'");
    }
    RootBeanDefinition mbdToUse = mbd;
    // Make sure bean class is actually resolved at this point, and
    // clone the bean definition in case of a dynamically resolved Class
    // which cannot be stored in the shared merged bean definition.
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }
    // Prepare method overrides.
    // lookup-method检测 此部分用于检测lookup-method标签配置的方法是否存在:
    try {
        mbdToUse.prepareMethodOverrides(); ⬇️
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                beanName, "Validation of method overrides failed", ex);
    }
    try {
        // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    }
    catch (Throwable ex) {
        throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                "BeanPostProcessor before instantiation of bean failed", ex);
    }
    try {
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        if (logger.isTraceEnabled()) {
            logger.trace("Finished creating instance of bean '" + beanName + "'");
        }
        return beanInstance;
    }
    catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
        // A previously detected exception with proper bean creation context already,
        // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
        throw ex;
    }
    catch (Throwable ex) {
        throw new BeanCreationException(
                mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
    }
}
```
_lookup-method检测_ 此部分用于检测lookup-method标签配置的方法是否存在:
```
public void prepareMethodOverrides() throws BeanDefinitionValidationException {
    // Check that lookup methods exists.
    if (hasMethodOverrides()) {
        Set<MethodOverride> overrides = getMethodOverrides().getOverrides();
        synchronized (overrides) {
            for (MethodOverride mo : overrides) {
                prepareMethodOverride(mo);
            }
        }
    }
}
protected void prepareMethodOverride(MethodOverride mo) throws BeanDefinitionValidationException {
    int count = ClassUtils.getMethodCountForName(getBeanClass(), mo.getMethodName());
    if (count == 0) {
        throw new BeanDefinitionValidationException(
                "Invalid method override: no method with name '" + mo.getMethodName() +
                "' on class [" + getBeanClassName() + "]");
    }
    else if (count == 1) {
        // Mark override as not overloaded, to avoid the overhead of arg type checking.
        mo.setOverloaded(false);
    }
}

InstantiationAwareBeanPostProcessor触发
在这里触发的是其postProcessBeforeInitialization和postProcessAfterInstantiation方法。
Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
if (bean != null) {
    return bean;
}
Object beanInstance = doCreateBean(beanName, mbdToUse, args);
return beanInstance;

protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
    Object bean = null;
    if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
        // Make sure bean class is actually resolved at this point.
        if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
            Class<?> targetType = determineTargetType(beanName, mbd);
            if (targetType != null) {
                bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
                if (bean != null) {
                    bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
                }
            }
        }
        mbd.beforeInstantiationResolved = (bean != null);
    }
    return bean;
}
```
**如果InstantiationAwareBeanPostProcessor返回的不是空，那么将不会继续执行剩下的Spring初始化流程，此接口用于初始化自定义的bean，主要是在Spring内部使用。**

_doCreateBean_
```
创建(createBeanInstance)
BeanWrapper instanceWrapper = null;
if (instanceWrapper == null) {
    instanceWrapper = createBeanInstance(beanName, mbd, args);
}
```
createBeanInstance的创建过程又分为以下几种情况:
- 工厂bean: 调用instantiateUsingFactoryMethod方法:
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod
protected BeanWrapper instantiateUsingFactoryMethod(
  String beanName, RootBeanDefinition mbd, Object[] explicitArgs) {
  return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
}
```
注意，此处的工厂bean指的是配置了factory-bean/factory-method属性的bean，不是实现了FactoryBean接口的bean。
如果没有配置factory-bean属性，那么factory-method指向的方法必须是静态的。此方法主要做了这么几件事:<br/>
 ~ 初始化一个BeanWrapperImpl对象。
 ~ 根据设置的参数列表使用反射的方法寻找相应的方法对象。
 ~ InstantiationStrategy: bean的初始化在此处又抽成了策略模式，类图:
![InstantiationStrategy](../../image/InstantiationStrategy.jpg)

instantiateUsingFactoryMethod部分源码:
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod
Assert.state(argsToUse != null, "Unresolved factory method arguments");
bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
return bw;
--> org.springframework.beans.factory.support.ConstructorResolver.instantiate(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {
    try {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
                    this.beanFactory.getInstantiationStrategy().instantiate(
                            mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
                    this.beanFactory.getAccessControlContext());
        }
        else {
            *******return this.beanFactory.getInstantiationStrategy().instantiate(
                    mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);******
        }
    }
    catch (Throwable ex) {
        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "Bean instantiation via factory method failed", ex);
    }
}
```
getInstantiationStrategy返回的是CglibSubclassingInstantiationStrategy对象。此处instantiate实现也很简单，就是调用工厂方法的Method对象反射调用其invoke即可得到对象，SimpleInstantiationStrategy.
instantiate核心源码:
```
org.springframework.beans.factory.support.SimpleInstantiationStrategy.instantiate(org.springframework.beans.factory.support.RootBeanDefinition, java.lang.String, org.springframework.beans.factory.BeanFactory, java.lang.Object, java.lang.reflect.Method, java.lang.Object...)
@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			@Nullable Object factoryBean, final Method factoryMethod, Object... args) {
    try {
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                ReflectionUtils.makeAccessible(factoryMethod);
                return null;
            });
        }
        else {
            ReflectionUtils.makeAccessible(factoryMethod);
        }
        Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
        try {
            currentlyInvokedFactoryMethod.set(factoryMethod);
            *******Object result = factoryMethod.invoke(factoryBean, args);******
            if (result == null) {
                result = new NullBean();
            }
            return result;
        }
        finally {
            if (priorInvokedFactoryMethod != null) {
                currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
            }
            else {
                currentlyInvokedFactoryMethod.remove();
            }
        }
    }
    catch (IllegalArgumentException ex) {
        throw new BeanInstantiationException(factoryMethod,
                "Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
                "args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
    }
    catch (IllegalAccessException ex) {
        throw new BeanInstantiationException(factoryMethod,
                "Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
    }
    catch (InvocationTargetException ex) {
        String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
        if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
                ((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
            msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
                    "declaring the factory method as static for independence from its containing instance. " + msg;
        }
        throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
    }
}
```
- 构造器自动装配
createBeanInstance部分源码:
```
// Need to determine the constructor...
Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
if (ctors != null ||
  mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_CONSTRUCTOR ||
    //配置了<constructor-arg>子元素
  mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args))  {
  return autowireConstructor(beanName, mbd, ctors, args);
}
```
determineConstructorsFromBeanPostProcessors源码:
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance
protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(Class<?> beanClass, String beanName) {
  if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
      for (BeanPostProcessor bp : getBeanPostProcessors()) {
          if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
              SmartInstantiationAwareBeanPostProcessor ibp = 
                  (SmartInstantiationAwareBeanPostProcessor) bp;
              Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
              if (ctors != null) {
                  return ctors;
              }
          }
      }
  }
  return null;
}
```
可见是由SmartInstantiationAwareBeanPostProcessor决定的，默认是没有配置这种东西的。<br/>
之后就是判断bean的自动装配模式，可以通过如下方式配置:<br/>
`<bean id="student" class="base.Student" primary="true" autowire="default" />`<br/>
autowire共有以下几种选项:<br/>
~ no: 默认的，不进行自动装配。在这种情况下，只能通过ref方式引用其它bean。<br/>
~ byName: 根据bean里面属性的名字在BeanFactory中进行查找并装配。<br/>
~ byType: 按类型。<br/>
~ constructor: 以byType的方式查找bean的构造参数列表。<br/>
~ default: 由父bean决定。<br/>
[Spring - bean的autowire属性(自动装配)](../bean_module/Spring%20-%20bean的autowire属性(自动装配).md)

autowireConstructor调用的是ConstructorResolver.autowireConstructor，此方法主要做了两件事:<br/>
~ 得到合适的构造器对象。<br/>
~ 根据构造器参数的类型去BeanFactory查找相应的bean:<br/>
入口方法在ConstructorResolver.resolveAutowiredArgument:
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.autowireConstructor
protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {
    return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
}
-->
org.springframework.beans.factory.support.ConstructorResolver.autowireConstructor
public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {
    BeanWrapperImpl bw = new BeanWrapperImpl();
    this.beanFactory.initBeanWrapper(bw);

    Constructor<?> constructorToUse = null;
    ArgumentsHolder argsHolderToUse = null;
    Object[] argsToUse = null;

    if (explicitArgs != null) {
        argsToUse = explicitArgs;
    }
    else {
        Object[] argsToResolve = null;
        synchronized (mbd.constructorArgumentLock) {
            constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
            if (constructorToUse != null && mbd.constructorArgumentsResolved) {
                // Found a cached constructor...
                argsToUse = mbd.resolvedConstructorArguments;
                if (argsToUse == null) {
                    argsToResolve = mbd.preparedConstructorArguments;
                }
            }
        }
        if (argsToResolve != null) {
            ****argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);****
        }
    }

    if (constructorToUse == null || argsToUse == null) {
        // Take specified constructors, if any.
        Constructor<?>[] candidates = chosenCtors;
        if (candidates == null) {
            Class<?> beanClass = mbd.getBeanClass();
            try {
                candidates = (mbd.isNonPublicAccessAllowed() ?
                        beanClass.getDeclaredConstructors() : beanClass.getConstructors());
            }
            catch (Throwable ex) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                        "Resolution of declared constructors on bean Class [" + beanClass.getName() +
                        "] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
            }
        }

        if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
            Constructor<?> uniqueCandidate = candidates[0];
            if (uniqueCandidate.getParameterCount() == 0) {
                synchronized (mbd.constructorArgumentLock) {
                    mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
                    mbd.constructorArgumentsResolved = true;
                    mbd.resolvedConstructorArguments = EMPTY_ARGS;
                }
                bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
                return bw;
            }
        }

        // Need to resolve the constructor.
        boolean autowiring = (chosenCtors != null ||
                mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        ConstructorArgumentValues resolvedValues = null;

        int minNrOfArgs;
        if (explicitArgs != null) {
            minNrOfArgs = explicitArgs.length;
        }
        else {
            ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
            resolvedValues = new ConstructorArgumentValues();
            minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
        }

        AutowireUtils.sortConstructors(candidates);
        int minTypeDiffWeight = Integer.MAX_VALUE;
        Set<Constructor<?>> ambiguousConstructors = null;
        LinkedList<UnsatisfiedDependencyException> causes = null;

        for (Constructor<?> candidate : candidates) {
            Class<?>[] paramTypes = candidate.getParameterTypes();

            if (constructorToUse != null && argsToUse != null && argsToUse.length > paramTypes.length) {
                // Already found greedy constructor that can be satisfied ->
                // do not look any further, there are only less greedy constructors left.
                break;
            }
            if (paramTypes.length < minNrOfArgs) {
                continue;
            }

            ArgumentsHolder argsHolder;
            if (resolvedValues != null) {
                try {
                    String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);
                    if (paramNames == null) {
                        ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
                        if (pnd != null) {
                            paramNames = pnd.getParameterNames(candidate);
                        }
                    }
                    argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
                            getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
                }
                catch (UnsatisfiedDependencyException ex) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
                    }
                    // Swallow and try next constructor.
                    if (causes == null) {
                        causes = new LinkedList<>();
                    }
                    causes.add(ex);
                    continue;
                }
            }
            else {
                // Explicit arguments given -> arguments length must match exactly.
                if (paramTypes.length != explicitArgs.length) {
                    continue;
                }
                argsHolder = new ArgumentsHolder(explicitArgs);
            }

            int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
                    argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
            // Choose this constructor if it represents the closest match.
            if (typeDiffWeight < minTypeDiffWeight) {
                constructorToUse = candidate;
                argsHolderToUse = argsHolder;
                argsToUse = argsHolder.arguments;
                minTypeDiffWeight = typeDiffWeight;
                ambiguousConstructors = null;
            }
            else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
                if (ambiguousConstructors == null) {
                    ambiguousConstructors = new LinkedHashSet<>();
                    ambiguousConstructors.add(constructorToUse);
                }
                ambiguousConstructors.add(candidate);
            }
        }

        if (constructorToUse == null) {
            if (causes != null) {
                UnsatisfiedDependencyException ex = causes.removeLast();
                for (Exception cause : causes) {
                    this.beanFactory.onSuppressedException(cause);
                }
                throw ex;
            }
            throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                    "Could not resolve matching constructor " +
                    "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
        }
        else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                    "Ambiguous constructor matches found in bean '" + beanName + "' " +
                    "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
                    ambiguousConstructors);
        }

        if (explicitArgs == null && argsHolderToUse != null) {
            argsHolderToUse.storeCache(mbd, constructorToUse);
        }
    }

    Assert.state(argsToUse != null, "Unresolved constructor arguments");
    bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
    return bw;
}
org.springframework.beans.factory.support.ConstructorResolver.resolvePreparedArguments
protected Object resolveAutowiredArgument(
        MethodParameter param, String beanName, Set<String> autowiredBeanNames, 
        TypeConverter typeConverter) {
    return this.beanFactory.resolveDependency(
            new DependencyDescriptor(param, true), beanName, 
            autowiredBeanNames, typeConverter);
}
```
最终调用的还是CglibSubclassingInstantiationStrategy.instantiate方法，关键源码:
```
@Override
public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
      final Constructor<?> ctor, Object... args) {
  if (bd.getMethodOverrides().isEmpty()) {
           //反射调用
      return BeanUtils.instantiateClass(ctor, args);
  } else {
      return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
  }
}
```
可以看出，如果配置了lookup-method标签，**得到的实际上是用Cglib生成的目标类的代理子类**。<br/>
CglibSubclassingInstantiationStrategy.instantiateWithMethodInjection:<br/>
```
@Override
protected Object instantiateWithMethodInjection(RootBeanDefinition bd, String beanName, BeanFactory 	owner,Constructor<?> ctor, Object... args) {
  // Must generate CGLIB subclass...
  return new CglibSubclassCreator(bd, owner).instantiate(ctor, args);
}
```
- 默认构造器<br/>
`return instantiateBean(beanName, mbd);`<br/>
_MergedBeanDefinitionPostProcessor_<br/>
```
触发源码
synchronized (mbd.postProcessingLock) {
    if (!mbd.postProcessed) {
        applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
        mbd.postProcessed = true;
    }
}
```
- 属性解析<br/>
入口方法: AbstractAutowireCapableBeanFactory.populateBean，它的作用是: 根据autowire类型进行autowire by name，by type 或者是直接进行设置，源码:
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.populateBean
@SuppressWarnings("deprecation")  // for postProcessPropertyValues
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
    if (bw == null) {
        if (mbd.hasPropertyValues()) {
            throw new BeanCreationException(
                    mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
        }
        else {
            // Skip property population phase for null instance.
            return;
        }
    }

    // Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
    // state of the bean before properties are set. This can be used, for example,
    // to support styles of field injection.
    boolean continueWithPropertyPopulation = true;

    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                    continueWithPropertyPopulation = false;
                    break;
                }
            }
        }
    }

    if (!continueWithPropertyPopulation) {
        return;
    }

    PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

    if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME || mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
        MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
        // Add property values based on autowire by name if applicable.
        if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME) {
            autowireByName(beanName, mbd, bw, newPvs); ⬇️
        }
        // Add property values based on autowire by type if applicable.
        if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
            autowireByType(beanName, mbd, bw, newPvs);
        }
        pvs = newPvs;
    }

    boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
    boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

    PropertyDescriptor[] filteredPds = null;
    if (hasInstAwareBpps) {
        if (pvs == null) {
            pvs = mbd.getPropertyValues();
        }
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
                if (pvsToUse == null) {
                    if (filteredPds == null) {
                        filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
                    }
                    pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
                    if (pvsToUse == null) {
                        return;
                    }
                }
                pvs = pvsToUse;
            }
        }
    }
    if (needsDepCheck) {
        if (filteredPds == null) {
            filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
        }
        checkDependencies(beanName, mbd, filteredPds, pvs);
    }

    if (pvs != null) {
        applyPropertyValues(beanName, mbd, bw, pvs);
    }
}
```
autowireByName
```
protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
    String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
    for (String propertyName : propertyNames) {
        if (containsBean(propertyName)) {
            Object bean = getBean(propertyName);
            pvs.add(propertyName, bean);
            registerDependentBean(propertyName, beanName);
            if (logger.isTraceEnabled()) {
                logger.trace("Added autowiring by name from bean name '" + beanName +
                        "' via property '" + propertyName + "' to bean named '" + propertyName + "'");
            }
        }
        else {
            if (logger.isTraceEnabled()) {
                logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
                        "' by name: no matching bean found");
            }
        }
    }
}
```
autowireByType也是同样的设计，所以可以得出结论: autowireByName和autowireByType方法只是先获取到引用的bean，真正的设值是在上面的applyPropertyValues中进行的。（org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.applyPropertyValues）<br/>
属性设置<br/>
Spring判断一个属性可不可以被设置(存不存在)是通过java bean的内省操作来完成的，也就是说，属性可以被设置的条件是**此属性拥有public的setter方法，并且注入时的属性名应该是setter的名字**。<br/>

- 初始化<br/>
此处的初始化指的是bean已经构造完成，执行诸如调用其init方法的操作。相关源码:<br/>
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean{
    // Initialize the bean instance.
    Object exposedObject = bean;
    try {
        populateBean(beanName, mbd, instanceWrapper);
        if (exposedObject != null) {
            exposedObject = initializeBean(beanName, exposedObject, mbd);
        }
    }
}
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(java.lang.String, java.lang.Object, org.springframework.beans.factory.support.RootBeanDefinition):
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
    if (System.getSecurityManager() != null) {
        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            invokeAwareMethods(beanName, bean);
            return null;
        }, getAccessControlContext());
    }
    else {
        invokeAwareMethods(beanName, bean);
    }

    Object wrappedBean = bean;
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }

    try {
        invokeInitMethods(beanName, wrappedBean, mbd);
    }
    catch (Throwable ex) {
        throw new BeanCreationException(
                (mbd != null ? mbd.getResourceDescription() : null),
                beanName, "Invocation of init method failed", ex);
    }
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }

    return wrappedBean;
}
```
[bean初始化](./bean初始化-initializeBean.md)

- getObjectForBeanInstance<br/>
位于AbstractBeanFactory，此方法的目的在于如果bean是FactoryBean，那么返回其工厂方法创建的bean，而不是自身。<br/>

#### Prototype初始化
AbstractBeanFactory.doGetBean相关源码:
org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean --> mbd.isPrototype()
```
else if (mbd.isPrototype()) {
    // It's a prototype -> create a new instance.
    Object prototypeInstance = null;
    try {
        // 此方法用于确保在同一时刻只能有一个此bean在初始化。
        beforePrototypeCreation(beanName);
        // 同上createBean
        prototypeInstance = createBean(beanName, mbd, args);
    }
    finally {
        // 与beforePrototypeCreation对应的
        afterPrototypeCreation(beanName);
    }
    bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
}
```
**初始化其实和单例是一样的，只不过单例多了一个是否已经存在的检查。**

#### 其它Scope初始化
指的是request、session。此部分源码:
```
else {
    String scopeName = mbd.getScope();
    final Scope scope = this.scopes.get(scopeName);
    if (scope == null) {
        throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
    }
    try {
        Object scopedInstance = scope.get(beanName, () -> {
            beforePrototypeCreation(beanName);
            try {
                return createBean(beanName, mbd, args);
            }
            finally {
                afterPrototypeCreation(beanName);
            }
        });
        bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
    }
    catch (IllegalStateException ex) {
        throw new BeanCreationException(beanName,
                "Scope '" + scopeName + "' is not active for the current thread; consider " +
                "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                ex);
    }
}
```
scopes是一个LinkedHashMap<String, Scope>，可以调用 ConfigurableBeanFactory定义的registerScope方法注册其值。<br/>
Scope接口继承体系:<br/>
![Scope接口继承体系](../../image/Scope接口继承体系.jpg)<br/>
根据scope.get的注释，此方法如果找到了叫做beanName的bean，那么返回，如果没有，将调用ObjectFactory创建。









