# SPRING.NET 1.3.2 学习20--方法注入之替换方法注入

这是一种用的比较少的注入方式。在Spring的配置中，通过replaced-method在需要替换类中指定需要被替换的方法，以及被哪个类替换。
替换类应该实现Spring.Objects.Factory.Support中的IMethodReplacer接口。
IMethodReplacer接口只有一个object Implement(object target, MethodInfo method, object[] arguments)方法。
当我们调用被替换类中相应方法时，会执行Implement方法。当然，我们可以中Implement中，通过参数信息获取到相应的部分信息。

replaced方法替换：实现MethodReplacer接口，替换目标Bean的方法

eg:<br/>
com.der.expand.TestReplacedMethod