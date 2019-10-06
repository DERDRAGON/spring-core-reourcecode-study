# Spring - lookup-method方式实现依赖注入

假设一个单例模式的bean A需要引用另外一个非单例模式的bean B，为了在我们每次引用的时候都能拿到最新的bean B，
我们可以让bean A通过实现ApplicationContextWare来感知applicationContext（即可以获得容器上下文），
从而能在运行时通过ApplicationContext.getBean(String beanName)的方法来获取最新的bean B。
但是如果用ApplicationContextAware接口，就让我们与Spring代码耦合了，违背了反转控制原则（IoC，即bean完全由Spring容器管理，我们自己的代码只需要用bean就可以了）。

所以Spring为我们提供了方法注入的方式来实现以上的场景。方法注入方式主要是通过<lookup-method/>标签。

eg：com.der.expand.TestLookupMethod

例子我们可以看到，在代码中，我们没有用到Spring的任何类和接口，实现了与Spring代码的耦合。
其中，最为核心的部分就是lookup-method的配置和FruitPlate.getFruit()方法。
上面代码中，我们可以看到getFruit()方法是个抽象方法，我们并没有实现它啊，那它是怎么拿到水果的呢。
这里的奥妙就是Srping应用了CGLIB（动态代理）类库。
Spring在初始化容器的时候对配置<lookup-method/>的bean做了特殊处理，Spring会对bean指定的class做动态代理，
代理<lookup-method/>标签中name属性所指定的方法，返回bean属性指定的bean实例对象。
每次我们调用fruitPlate1或者fruitPlate2这2个bean的getFruit()方法时，其实是调用了CGLIB生成的动态代理类的方法。

lookup-method实现方式说明：
```
<bean class="beanClass">
    <lookup-method name="method" bean="non-singleton-bean"/>
</bean>
```
method是beanClass中的一个方法，beanClass和method是不是抽象都无所谓，不会影响CGLIB的动态代理，根据项目实际需求去定义。
non-singleton-bean指的是lookup-method中bean属性指向的必须是一个非单例模式的bean，当然如果不是也不会报错，
只是每次得到的都是相同引用的bean（同一个实例），这样用lookup-method就没有意义了。

另外对于method在代码中的签名有下面的标准：
<public|protected> [abstract] <return-type> theMethodName(no-arguments);
public|protected要求方法必须是可以被子类重写和调用的；
abstract可选，如果是抽象方法，CGLIB的动态代理类就会实现这个方法，如果不是抽象方法，就会覆盖这个方法，所以没什么影响；
return-type就是non-singleton-bean的类型咯，当然可以是它的父类或者接口。
no-arguments不允许有参数。

eg:<br/>
com.der.expand.TestLookupMethod