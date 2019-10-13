# ConfigBeanDefinitionParser.parse解析

```
org.springframework.aop.config.ConfigBeanDefinitionParser.parse
@Override
@Nullable
public BeanDefinition parse(Element element, ParserContext parserContext) {
    CompositeComponentDefinition compositeDef =
            new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
    parserContext.pushContainingComponent(compositeDef);

    configureAutoProxyCreator(parserContext, element); ⬇️ -- proxy-target-class & expose-proxy

    List<Element> childElts = DomUtils.getChildElements(element);
    for (Element elt: childElts) {
        String localName = parserContext.getDelegate().getLocalName(elt);
        if (POINTCUT.equals(localName)) {
            parsePointcut(elt, parserContext); ⬇️ -- aop:pointcut
        }
        else if (ADVISOR.equals(localName)) {
            parseAdvisor(elt, parserContext); ⬇️ -- aop:advisor
        }
        else if (ASPECT.equals(localName)) {
            parseAspect(elt, parserContext);
        }
    }

    parserContext.popAndRegisterContainingComponent();
    return null;
}
```

## proxy-target-class & expose-proxy
对应着aop:config的两个属性，前者代表是否为被代理这生成CGLIB子类，默认false，只为接口生成代理子类(话说如果不生成子类那么怎么拦截?)。
后者代表是否将代理bean暴露给用户，如果暴露，可以通过Spring AopContext类获得，默认不暴露。
```
org.springframework.aop.config.ConfigBeanDefinitionParser.configureAutoProxyCreator
private void configureAutoProxyCreator(ParserContext parserContext, Element element) {
    AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary(parserContext, element);
}
org.springframework.aop.config.AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary
public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
    BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
            parserContext.getRegistry(), parserContext.extractSource(sourceElement));
    useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
    registerComponentIfNecessary(beanDefinition, parserContext);
}
org.springframework.aop.config.AopNamespaceUtils.useClassProxyingIfNecessary
private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
    if (sourceElement != null) {
        // public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";
        boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
        if (proxyTargetClass) {
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
        }
        // private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";
        boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
        if (exposeProxy) {
            AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
        }
    }
}
private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
    if (beanDefinition != null) {
        // public static final String AUTO_PROXY_CREATOR_BEAN_NAME = "org.springframework.aop.config.internalAutoProxyCreator";
        parserContext.registerComponent(
                new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
    }
}
```

## aop:pointcut
```
private AbstractBeanDefinition parsePointcut(Element pointcutElement, ParserContext parserContext) {
    String id = pointcutElement.getAttribute(ID);
    String expression = pointcutElement.getAttribute(EXPRESSION);
    AbstractBeanDefinition pointcutDefinition = null;
    try {
        this.parseState.push(new PointcutEntry(id));
        pointcutDefinition = createPointcutDefinition(expression);
        pointcutDefinition.setSource(parserContext.extractSource(pointcutElement));
        String pointcutBeanName = id;
        if (StringUtils.hasText(pointcutBeanName)) {
            parserContext.getRegistry().registerBeanDefinition(pointcutBeanName, pointcutDefinition);
        }
        else {
            pointcutBeanName = parserContext.getReaderContext().registerWithGeneratedName(pointcutDefinition);
        }
        parserContext.registerComponent(
                new PointcutComponentDefinition(pointcutBeanName, pointcutDefinition, expression));
    }
    finally {
        this.parseState.pop();
    }
    return pointcutDefinition;
}
org.springframework.aop.config.ConfigBeanDefinitionParser.createPointcutDefinition
protected AbstractBeanDefinition createPointcutDefinition(String expression) {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(AspectJExpressionPointcut.class);
    beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
    beanDefinition.setSynthetic(true);
    beanDefinition.getPropertyValues().add(EXPRESSION, expression);
    return beanDefinition;
}
```
pointcut的解析是一个生成一个BeanDefinition并将其id, expression等属性保存在BeanDefinition中。注意以下几点:<br/>
 - BeanDefinition的ID来自于id属性，如果没有，那么自动生成。<br/>
 - BeanDefinition的class是AspectJExpressionPointcut。<br/>
 - BeanDefinition的scope为prototype。<br/>
 AspectJExpressionPointcut类图:<br/>
 ![AspectJExpressionPointcut类图](../image/AspectJExpressionPointcut.png)
 
 ## aop:advisor
 ```
 private AbstractBeanDefinition createAdvisorBeanDefinition(Element advisorElement, ParserContext parserContext) {
    RootBeanDefinition advisorDefinition = new RootBeanDefinition(DefaultBeanFactoryPointcutAdvisor.class);
    advisorDefinition.setSource(parserContext.extractSource(advisorElement));
    String adviceRef = advisorElement.getAttribute(ADVICE_REF);
    if (!StringUtils.hasText(adviceRef)) {
        parserContext.getReaderContext().error(
                "'advice-ref' attribute contains empty value.", advisorElement, this.parseState.snapshot());
    }
    else {
        advisorDefinition.getPropertyValues().add(
                ADVICE_BEAN_NAME, new RuntimeBeanNameReference(adviceRef));
    }
    if (advisorElement.hasAttribute(ORDER_PROPERTY)) {
        advisorDefinition.getPropertyValues().add(
                ORDER_PROPERTY, advisorElement.getAttribute(ORDER_PROPERTY));
    }
    return advisorDefinition;
}
 ```
首先是其所有属性的示例:
```
<aop:advisor id="" order="" advice-ref="aopAdvice" pointcut="" pointcut-ref="" />
```
advisor概念是Spring独有的，来自于上古时代，应该是较早时候的aop概念的实现: [AOP Alliance (Java/J2EE AOP standards)](http://aopalliance.sourceforge.net/)。
Spring官方的说法: [aop-schema-advisors](https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop)。<br/>
相关包/类：<br/>
![spring-aop相关类图](../image/spring-aop相关类图.png)<br/>
advice-ref是必须的属性，并且这里的advice必须实现org.aopalliance.aop.Advice的子接口。这些子接口指的什么呢，见Spring官方文档: [aop-api-advice-types](https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop-api)。
比如org.aopalliance.intercept.MethodInterceptor。<br/>
最常见的用途就是结合事务使用:
```
<tx:advice id="txAdvice" transaction-manager="transactionManager">
    <tx:attributes>
        <tx:method name="get*" read-only="true" propagation="NOT_SUPPORTED"/>
        <tx:method name="find*" read-only="true" propagation="NOT_SUPPORTED"/>
        <tx:method name="*" propagation="REQUIRED"/>
    </tx:attributes>
</tx:advice>

<aop:config>
    <aop:pointcut expression="execution(* exam.service..*.*(..))" id="transaction"/>
    <aop:advisor advice-ref="txAdvice" pointcut-ref="transaction"/>
</aop:config>
```
处的beanClass是DefaultBeanFactoryPointcutAdvisor，其类图:<br/>
![DefaultBeanFactoryPointcutAdvisor](../image/DefaultBeanFactoryPointcutAdvisor.png)<br/>
另外注意对于pointcut和pointcut-ref两者处理的区别，对于pointcut属性，Spring会同样创建一个AspectJExpressionPointcut类型的BeanDefinition，
对于pointcut-ref会生成一个RuntimeBeanReference对象指向原pointcut的引用。此类的类图:<br/>
![org.springframework.beans.factory.config.RuntimeBeanNameReference.RuntimeBeanNameReference](../image/RuntimeBeanNameReference.png)<br/>
aop的实现需要实现各种接口，所以不应该再使用此种方式进行aop，除了Spring内部的实现。

## aop:aspect
```
private void parseAspect(Element aspectElement, ParserContext parserContext) {
    String aspectId = aspectElement.getAttribute(ID);
    String aspectName = aspectElement.getAttribute(REF);
    try {
        this.parseState.push(new AspectEntry(aspectId, aspectName));
        List<BeanDefinition> beanDefinitions = new ArrayList<>();
        List<BeanReference> beanReferences = new ArrayList<>();

        List<Element> declareParents = DomUtils.getChildElementsByTagName(aspectElement, DECLARE_PARENTS);
        for (int i = METHOD_INDEX; i < declareParents.size(); i++) {
            Element declareParentsElement = declareParents.get(i);
            beanDefinitions.add(parseDeclareParents(declareParentsElement, parserContext));
        }
        // We have to parse "advice" and all the advice kinds in one loop, to get the
        // ordering semantics right.
        NodeList nodeList = aspectElement.getChildNodes();
        boolean adviceFoundAlready = false;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (isAdviceNode(node, parserContext)) {
                if (!adviceFoundAlready) {
                    adviceFoundAlready = true;
                    if (!StringUtils.hasText(aspectName)) {
                        parserContext.getReaderContext().error(
                                "<aspect> tag needs aspect bean reference via 'ref' attribute when declaring advices.",
                                aspectElement, this.parseState.snapshot());
                        return;
                    }
                    beanReferences.add(new RuntimeBeanReference(aspectName));
                }
                AbstractBeanDefinition advisorDefinition = parseAdvice( ⬇️ -- 其他-parseAdvice
                        aspectName, i, aspectElement, (Element) node, parserContext, beanDefinitions, beanReferences);
                beanDefinitions.add(advisorDefinition);
            }
        }

        AspectComponentDefinition aspectComponentDefinition = createAspectComponentDefinition(
                aspectElement, aspectId, beanDefinitions, beanReferences, parserContext);
        parserContext.pushContainingComponent(aspectComponentDefinition);

        List<Element> pointcuts = DomUtils.getChildElementsByTagName(aspectElement, POINTCUT);
        for (Element pointcutElement : pointcuts) {
            parsePointcut(pointcutElement, parserContext);
        }

        parserContext.popAndRegisterContainingComponent();
    }
    finally {
        this.parseState.pop();
    }
}
```
配置举例:
```
<bean id="aopAdvice" class="base.aop.AopDemoAdvice" />
<!-- 必须配置，因为被代理的对象必须在Spring容器中 -->
<bean id="aopDemo" class="base.aop.AopDemo" />
<aop:config>
    <aop:pointcut id="pointcut" expression="execution(* base.aop.AopDemo.send())" />
    <aop:aspect ref="aopAdvice">
        <aop:before method="beforeSend" pointcut-ref="pointcut" />
        <aop:after method="afterSend" pointcut-ref="pointcut" />
    </aop:aspect>
</aop:config>
```
解析形成的BeanDefinition结构如下:
```
AspectComponentDefinition
    beanRefArray
        RuntimeBeanReference(aop:aspect的ref属性)
    beanDefArray
        // 被注册
        RootBeanDefinition(aop:declare-parents)
            beanClass: DeclareParentsAdvisor
            ConstructorArg
                implement-interface
                types-matching
                default-impl
                delegate-ref
        // 被注册
        RootBeanDefinition(aop:before,aop:after...)
            beanClass: AspectJPointcutAdvisor
            ConstructorArg
                RootBeanDefinition
                    beanClass: 由子标签决定
                    ConstructorArg
                        RootBeanDefinition
                            beanClass: MethodLocatingFactoryBean
                            properties
                                targetBeanName: aspectName
                                methodName: method属性
                        RootBeanDefinition
                            beanClass: SimpleBeanFactoryAwareAspectInstanceFactory
                            properties
                                aspectBeanName: aspectName
                        //还有pointcut定义和引用...
```
aop:declare-parents对于aop:declare-parents子标签，其决定的是代理子类应该实现哪些接口:<br/>
`<aop:declare-parents types-matching="" implement-interface="" />`<br/>
此标签最终被解析成为beanClass为DeclareParentsAdvisor的BeanDefinition，并注册到容器中。其类图:<br/>
![DeclareParentsAdvisor的BeanDefinition](../image/DeclareParentsAdvisor.png)
```
org.springframework.aop.config.ConfigBeanDefinitionParser.parseDeclareParents
private AbstractBeanDefinition parseDeclareParents(Element declareParentsElement, ParserContext parserContext) {
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(DeclareParentsAdvisor.class);
    builder.addConstructorArgValue(declareParentsElement.getAttribute(IMPLEMENT_INTERFACE));
    builder.addConstructorArgValue(declareParentsElement.getAttribute(TYPE_PATTERN));

    String defaultImpl = declareParentsElement.getAttribute(DEFAULT_IMPL);
    String delegateRef = declareParentsElement.getAttribute(DELEGATE_REF);

    if (StringUtils.hasText(defaultImpl) && !StringUtils.hasText(delegateRef)) {
        builder.addConstructorArgValue(defaultImpl);
    }
    else if (StringUtils.hasText(delegateRef) && !StringUtils.hasText(defaultImpl)) {
        builder.addConstructorArgReference(delegateRef);
    }
    else {
        parserContext.getReaderContext().error(
                "Exactly one of the " + DEFAULT_IMPL + " or " + DELEGATE_REF + " attributes must be specified",
                declareParentsElement, this.parseState.snapshot());
    }

    AbstractBeanDefinition definition = builder.getBeanDefinition();
    definition.setSource(parserContext.extractSource(declareParentsElement));
    parserContext.getReaderContext().registerWithGeneratedName(definition);
    return definition;
}
```
## 其它
此处的其它指的是aop:before, aop:after等最核心的标签。其最终被解析为beanClass为AspectJPointcutAdvisor的BeanDefinition，类图:<br/>
![AspectJPointcutAdvisor的BeanDefinition](../image/AspectJPointcutAdvisor.png)<br/>
正如上面结构图里所描述的，其构造参数为一个BeanDefinition，此对象的beanClass是不确定的，由aop:before/after中的before和after决定，代码:
**parseAdvice**
```
private AbstractBeanDefinition parseAdvice(
			String aspectName, int order, Element aspectElement, Element adviceElement, ParserContext parserContext,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {
    try {
        this.parseState.push(new AdviceEntry(parserContext.getDelegate().getLocalName(adviceElement)));

        // create the method factory bean
        RootBeanDefinition methodDefinition = new RootBeanDefinition(MethodLocatingFactoryBean.class);
        methodDefinition.getPropertyValues().add("targetBeanName", aspectName);
        methodDefinition.getPropertyValues().add("methodName", adviceElement.getAttribute("method"));
        methodDefinition.setSynthetic(true);

        // create instance factory definition
        RootBeanDefinition aspectFactoryDef =
                new RootBeanDefinition(SimpleBeanFactoryAwareAspectInstanceFactory.class);
        aspectFactoryDef.getPropertyValues().add("aspectBeanName", aspectName);
        aspectFactoryDef.setSynthetic(true);

        // register the pointcut
        AbstractBeanDefinition adviceDef = createAdviceDefinition( ⬇️ -- createAdviceDefinition
                adviceElement, parserContext, aspectName, order, methodDefinition, aspectFactoryDef,
                beanDefinitions, beanReferences);

        // configure the advisor
        RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);
        advisorDefinition.setSource(parserContext.extractSource(adviceElement));
        advisorDefinition.getConstructorArgumentValues().addGenericArgumentValue(adviceDef);
        if (aspectElement.hasAttribute(ORDER_PROPERTY)) {
            advisorDefinition.getPropertyValues().add(
                    ORDER_PROPERTY, aspectElement.getAttribute(ORDER_PROPERTY));
        }

        // register the final advisor
        parserContext.getReaderContext().registerWithGeneratedName(advisorDefinition);

        return advisorDefinition;
    }
    finally {
        this.parseState.pop();
    }
}
```
**createAdviceDefinition**
```
private AbstractBeanDefinition createAdviceDefinition(
			Element adviceElement, ParserContext parserContext, String aspectName, int order,
			RootBeanDefinition methodDef, RootBeanDefinition aspectFactoryDef,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

    RootBeanDefinition adviceDefinition = new RootBeanDefinition(getAdviceClass(adviceElement, parserContext)); ⬇️ -- getAdviceClass
    adviceDefinition.setSource(parserContext.extractSource(adviceElement));

    adviceDefinition.getPropertyValues().add(ASPECT_NAME_PROPERTY, aspectName);
    adviceDefinition.getPropertyValues().add(DECLARATION_ORDER_PROPERTY, order);

    if (adviceElement.hasAttribute(RETURNING)) {
        adviceDefinition.getPropertyValues().add(
                RETURNING_PROPERTY, adviceElement.getAttribute(RETURNING));
    }
    if (adviceElement.hasAttribute(THROWING)) {
        adviceDefinition.getPropertyValues().add(
                THROWING_PROPERTY, adviceElement.getAttribute(THROWING));
    }
    if (adviceElement.hasAttribute(ARG_NAMES)) {
        adviceDefinition.getPropertyValues().add(
                ARG_NAMES_PROPERTY, adviceElement.getAttribute(ARG_NAMES));
    }

    ConstructorArgumentValues cav = adviceDefinition.getConstructorArgumentValues();
    cav.addIndexedArgumentValue(METHOD_INDEX, methodDef);

    Object pointcut = parsePointcutProperty(adviceElement, parserContext);
    if (pointcut instanceof BeanDefinition) {
        cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcut);
        beanDefinitions.add((BeanDefinition) pointcut);
    }
    else if (pointcut instanceof String) {
        RuntimeBeanReference pointcutRef = new RuntimeBeanReference((String) pointcut);
        cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcutRef);
        beanReferences.add(pointcutRef);
    }

    cav.addIndexedArgumentValue(ASPECT_INSTANCE_FACTORY_INDEX, aspectFactoryDef);

    return adviceDefinition;
}
```
**getAdviceClass**
```
private Class<?> getAdviceClass(Element adviceElement, ParserContext parserContext) {
    String elementName = parserContext.getDelegate().getLocalName(adviceElement);
    if (BEFORE.equals(elementName)) {
        return AspectJMethodBeforeAdvice.class;
    }
    else if (AFTER.equals(elementName)) {
        return AspectJAfterAdvice.class;
    }
    else if (AFTER_RETURNING_ELEMENT.equals(elementName)) {
        return AspectJAfterReturningAdvice.class;
    }
    else if (AFTER_THROWING_ELEMENT.equals(elementName)) {
        return AspectJAfterThrowingAdvice.class;
    }
    else if (AROUND.equals(elementName)) {
        return AspectJAroundAdvice.class;
    }
    else {
        throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
    }
}
```


