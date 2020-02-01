- [spring-aop](#spring-aop)
  - [aop:config](#aopconfig)

# spring-aop

aop部分的解析器由AopNamespaceHandler注册，其init方法:
```
@Override
public void init() {
    registerBeanDefinitionParser("config", new ConfigBeanDefinitionParser());
    registerBeanDefinitionParser("aspectj-autoproxy", new AspectJAutoProxyBeanDefinitionParser());
    registerBeanDefinitionDecorator("scoped-proxy", new ScopedProxyBeanDefinitionDecorator());
}
```

## aop:config
此标签用以配置pointcut, advisor, aspect，实例:
```
<aop:config>
    <aop:pointcut expression="execution(* exam.service..*.*(..))" id="transaction"/>
    <aop:advisor advice-ref="txAdvice" pointcut-ref="transaction"/>
    <aop:aspect ref="" />
</aop:config>
```
ConfigBeanDefinitionParser.parse:
```
@Override
@Nullable
public BeanDefinition parse(Element element, ParserContext parserContext) {
    CompositeComponentDefinition compositeDef =
            new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
    parserContext.pushContainingComponent(compositeDef);

    configureAutoProxyCreator(parserContext, element);

    List<Element> childElts = DomUtils.getChildElements(element);
    for (Element elt: childElts) {
        String localName = parserContext.getDelegate().getLocalName(elt);
        if (POINTCUT.equals(localName)) {
            parsePointcut(elt, parserContext);
        }
        else if (ADVISOR.equals(localName)) {
            parseAdvisor(elt, parserContext);
        }
        else if (ASPECT.equals(localName)) {
            parseAspect(elt, parserContext);
        }
    }

    parserContext.popAndRegisterContainingComponent();
    return null;
}
```
[解析ConfigBeanDefinitionParser.parse](./aop-modules/ConfigBeanDefinitionParser-parse解析.md)








