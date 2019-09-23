## 第一节学习

### ClassPathXmlApplicationContext

demo：com.der.day001.BaseStudy

```$xslt
ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-start.xml");
TestEntity bean = context.getBean(TestEntity.class);
bean.send();
context.close();

<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean class="com.der.domain.TestEntity" />
</beans>
```

整个继承体系如下:<br/>
![ClassPathXmlApplicationContext继承](./image/diagram.png)<br/>
构造器源码：<br/>
```$xslt
public ClassPathXmlApplicationContext(
        String[] configLocations, boolean refresh, @Nullable ApplicationContext parent)
        throws BeansException {
    super(parent);
    setConfigLocations(configLocations);
    if (refresh) {
        refresh();
    }
}
```

ClassPathXmlApplicationContext初始化步骤：<br/>
&nbsp;&nbsp;&nbsp;&nbsp;① ClassPathXmlApplicationContext继承父类直到AbstractApplicationContext调用getResourcePatternResolver获取资源解析器<br/>
&nbsp;&nbsp;&nbsp;&nbsp;② AbstractApplicationContext调用setParent方法设置父级ApplicationContext <br/>
``` AbstractApplicationContext的setParent方法
public void setParent(@Nullable ApplicationContext parent) {
    this.parent = parent;
    if (parent != null) {
        Environment parentEnvironment = parent.getEnvironment();
        if (parentEnvironment instanceof ConfigurableEnvironment) {
            getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
        }
    }
}
```
&nbsp;&nbsp;&nbsp;&nbsp;③ ClassPathXmlApplicationContext调用AbstractRefreshableConfigApplicationContext.setConfigLocations设置配置文件路径<br/>
``` AbstractRefreshableConfigApplicationContext.setConfigLocations
public void setConfigLocations(@Nullable String... locations) {
    if (locations != null) {
        Assert.noNullElements(locations, "Config locations must not be null");
        this.configLocations = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            this.configLocations[i] = resolvePath(locations[i]).trim();
        }
    }
    else {
        this.configLocations = null;
    }
}

protected String resolvePath(String path) {
    return getEnvironment().resolveRequiredPlaceholders(path);
}

此方法的目的在于将占位符(placeholder)解析成实际的地址。比如可以这么写: new ClassPathXmlApplicationContext("classpath:config.xml");那么classpath:就是需要被解析的。
getEnvironment方法来自于ConfigurableApplicationContext接口，源码很简单，如果为空就调用createEnvironment创建一个。
AbstractApplicationContext.createEnvironment:

protected ConfigurableEnvironment createEnvironment() {
    return new StandardEnvironment();
}
```
StandardEnvironment继承结构：<br/>
![StandardEnvironment](./image/ConfigurablePropertyResolver.png)
Environment构造器<br/>
```$xslt
private final MutablePropertySources propertySources = new MutablePropertySources(this.logger);
public AbstractEnvironment() {
    customizePropertySources(this.propertySources);
}
```
PropertySources继承体系：<br/>
