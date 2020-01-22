- [meta子元素的解析](#meta%e5%ad%90%e5%85%83%e7%b4%a0%e7%9a%84%e8%a7%a3%e6%9e%90)
- [replace-mothod解析](#replace-mothod%e8%a7%a3%e6%9e%90)
- [构造参数(constructor-arg)解析](#%e6%9e%84%e9%80%a0%e5%8f%82%e6%95%b0constructor-arg%e8%a7%a3%e6%9e%90)
- [property解析:](#property%e8%a7%a3%e6%9e%90)
- [qualifier解析](#qualifier%e8%a7%a3%e6%9e%90)
- [Bean装饰](#bean%e8%a3%85%e9%a5%b0)
- [Bean注册](#bean%e6%b3%a8%e5%86%8c)

# meta子元素的解析
```
meta元素在xml配置文件里是这样的
<bean id="b" name="one, two" class="base.SimpleBean">
    <meta key="name" value="skywalker"/>
</bean>
可以将任意的元数据附到对应的bean definition上
public void parseMetaElements(Element ele, BeanMetadataAttributeAccessor attributeAccessor) {
    NodeList nl = ele.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
        Node node = nl.item(i);
        if (isCandidateElement(node) && nodeNameEquals(node, META_ELEMENT)) {
            Element metaElement = (Element) node;
            String key = metaElement.getAttribute(KEY_ATTRIBUTE);
            String value = metaElement.getAttribute(VALUE_ATTRIBUTE);
            //就是一个key, value的载体，无他
            BeanMetadataAttribute attribute = new BeanMetadataAttribute(key, value);
            //sourceExtractor默认是NullSourceExtractor，返回的是空
            attribute.setSource(extractSource(metaElement));
            attributeAccessor.addMetadataAttribute(attribute);
        }
    }
}
```
[Spring - lookup-method方式实现依赖注入](../../../expand/Spring%20-%20lookup-method方式实现依赖注入.md)

# replace-mothod解析
此标签用于替换bean里面的特定的方法实现，替换者必须实现Spring的MethodReplacer接口
```
<bean name="replacer" class="springroad.deomo.chap4.MethodReplace" />  
<bean name="testBean" class="springroad.deomo.chap4.LookupMethodBean">
    <replaced-method name="test" replacer="replacer">
        <arg-type match="String" />
    </replaced-method>  
</bean> 
arg-type的作用是指定替换方法的参数类型，因为接口的定义参数都是Object的¡
```
此标签用于替换bean里面的特定的方法实现，替换者必须实现Spring的MethodReplacer接口
eg:
```
<bean name="replacer" class="springroad.deomo.chap4.MethodReplace" />  
<bean name="testBean" class="springroad.deomo.chap4.LookupMethodBean">
    <replaced-method name="test" replacer="replacer">
        <arg-type match="String" />
    </replaced-method>  
</bean> 
```

arg-type的作用是指定替换方法的参数类型，因为接口的定义参数都是Object的 参考[SPRING.NET 1.3.2 学习20--方法注入之替换方法注入](../../../expand/SPRING.NET%201.3.2%20学习20--方法注入之替换方法注入.md)<br/>
解析之后将数据放在ReplaceOverride对象中，里面有一个LinkedList专门用于保存arg-type。

# 构造参数(constructor-arg)解析
```
<bean class="base.SimpleBean">
    <constructor-arg>
        <value type="java.lang.String">Cat</value>
    </constructor-arg>
</bean>
```
type一般不需要指定，除了泛型集合那种。除此之外，constructor-arg还支持name, index, ref等属性，可以具体的指定参数的位置等。
构造参数解析后保存在BeanDefinition内部一个ConstructorArgumentValues对象中。
如果设置了index属性，那么以Map<Integer, ValueHolder>的形式保存，反之，以List的形式保存。

# property解析:
常用的标签，用以为bean的属性赋值，支持value和ref两种形式
```
<bean class="base.SimpleBean">
    <property name="name" value="skywalker" />
</bean>
value和ref属性不能同时出现，如果是ref，那么将其值保存在不可变的RuntimeBeanReference对象中，其实现了BeanReference接口，
此接口只有一个getBeanName方法。如果是value，那么将其值保存在TypedStringValue对象中。
最终将对象保存在BeanDefinition内部一个MutablePropertyValues对象中(内部以ArrayList实现)。
```

# qualifier解析
```
<bean class="base.Student">
    <property name="name" value="skywalker"></property>
    <property name="age" value="12"></property>
    <qualifier type="org.springframework.beans.factory.annotation.Qualifier" value="student" />
</bean>	
<bean class="base.Student">
    <property name="name" value="seaswalker"></property>
    <property name="age" value="15"></property>
    <qualifier value="student_2"></qualifier>
</bean>
<bean class="base.SimpleBean" />

class SimpleBean {
    @Autowired
    @Qualifier("student")
    private Student student;
}
```
此标签和@Qualifier, @Autowired两个注解一起使用才有作用。@Autowired注解采用按类型查找的方式进行注入，
如果找到多个需要类型的bean便会报错，有了@Qualifier标签就可以再按照此注解指定的名称查找。
两者结合相当于实现了按类型+名称注入。type属性可以不指定，因为默认就是那个。
``` 
qualifier标签可以有attribute子元素,eg:
<qualifier type="org.springframework.beans.factory.annotation.Qualifier" value="student">
    <attribute key="id" value="1"/>
</qualifier>
```

# Bean装饰
```
这部分是针对其它schema的属性以及子节点
<bean class="base.Student" primary="true">
    <context:property-override />
</bean>
```

# Bean注册
