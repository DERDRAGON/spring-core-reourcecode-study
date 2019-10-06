# Spring - bean的lazy-init属性（懒加载）

默认情况下，容器初始化的时候便会把bean实例化，通常这样做可以让一些配置或者bean实例化的异常在容器启动的时候就发现，
而不是在N久之后。但有时候，我们希望某个可能不会用到但又不是100%不用的bean能够在我们用的时候才实例化，
这样可以节省系统资源。所以万能的Spring为我们提供了lazy-init属性：

```
<bean id="lazy" class="com.foo.ExpensiveToCreateBean" lazy-init="true"/>
<bean name="not.lazy" class="com.foo.AnotherBean"/>
```

上面例子中，容器初始化时not.lazy bean会被实例化，而lazy bean不会被实例化。
但如果一个配置了lazy-init="true"属性的bean被另外一个bean依赖，那Spring还是会在容器初始化的时候实例化这个bean。

另外，假设我们希望某个bean的配置文件中的所有bean都是懒加载的，那我们可以给<beans/>标签添加default-lazy-init="true"属性。