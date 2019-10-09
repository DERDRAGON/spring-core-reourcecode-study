# Spring事件驱动模型

## 简介
事件驱动模型也就是我们常说的观察者，或者发布-订阅模型；理解它的几个关键点：<br/>
首先是一种对象间的一对多的关系；最简单的如交通信号灯，信号灯是目标（一方），行人注视着信号灯（多方）；<br/>
当目标发送改变（发布），观察者（订阅者）就可以接收到改变；<br/>
观察者如何处理（如行人如何走，是快走/慢走/不走，目标不会管的），目标无需干涉；所以就松散耦合了它们之间的关系。<br/>
eg:<br/>
用户注册<br/>
![用户注册](../../image/userRegist.png)<br/>

使用一个观察者来解耦这些Service之间的依赖关系<br/>
![用户监听器](../../image/RegistListener.png)<br/>

典型的事件处理模型/观察者，**解耦目标对象和它的依赖对象，目标只需要通知它的依赖对象，具体怎么处理，依赖对象自己决定**。比如是异步还是同步，延迟还是非延迟等。<br/>
**上边其实也使用了DIP（依赖倒置原则），依赖于抽象，而不是具体。**<br/>
**IoC思想：即以前主动去创建它依赖的Service，现在只是被动等待别人注册进来。**<br/>
**目的是**：松散耦合对象间的一对多的依赖关系<br/>

**接口目的是抽象，抽象类目的是复用**

**在Java中接口还一个非常重要的好处：接口是可以多实现的，类/抽象类只能单继承，所以使用接口可以非常容易扩展新功能（还可以实现所谓的mixin），类/抽象类办不到。**

## Java GUI事件驱动模型/观察者 
JavaBean规范提供了JavaBean的PropertyEditorSupport及PropertyChangeListener支持。<br/>
PropertyEditorSupport就是目标，而PropertyChangeListener就是监听器<br/>

## Java提供的事件驱动模型/观察者抽象
JDK内部直接提供了观察者模式的抽象：<br/>
目标：java.util.Observable，提供了目标需要的关键抽象：addObserver/deleteObserver/notifyObservers()等，具体请参考javadoc。<br/>
观察者：java.util.Observer，提供了观察者需要的主要抽象：update(Observable o, Object arg)，
此处还提供了一种推模型（目标主动把数据通过arg推到观察者）/拉模型（目标需要根据o自己去拉数据，arg为null）。<br/>

 ## Spring提供的事件驱动模型/观察者抽象
 ![Spring提供的事件驱动模型体系图](../../image/spring-eventDriver.png)<br/>
 **事件**
具体代表者是：ApplicationEvent：<br/>
1、其继承自JDK的EventObject，JDK要求所有事件将继承它，并通过source得到事件源，比如我们的AWT事件体系也是继承自它；<br/>
2、系统默认提供了如下ApplicationEvent事件实现：<br/>
![ApplicationEvent](../../image/applicationEvent.png)<br/>
只有一个ApplicationContextEvent，表示ApplicationContext容器事件，且其又有如下实现：<br/>
- ContextStartedEvent：ApplicationContext启动后触发的事件；（目前版本没有任何作用）<br/>
- ContextStoppedEvent：ApplicationContext停止后触发的事件；（目前版本没有任何作用）<br/>
- ContextRefreshedEvent：ApplicationContext初始化或刷新完成后触发的事件；（容器初始化完成后调用）<br/>
- ContextClosedEvent：ApplicationContext关闭后触发的事件；（如web容器关闭时自动会触发spring容器的关闭，如果是普通java应用，需要调用ctx.registerShutdownHook();注册虚拟机关闭时的钩子才行）<br/>
注：org.springframework.context.support.AbstractApplicationContext抽象类实现了LifeCycle的start和stop回调并发布ContextStartedEvent和ContextStoppedEvent事件；但是无任何实现调用它，所以目前无任何作用。<br/>
**目标（发布事件者）**<br/>
具体代表者是：ApplicationEventPublisher及ApplicationEventMulticaster，系统默认提供了如下实现：<br/>
![ApplicationEventPublisher](../../image/ApplicationEventPublisher.png)<br/>
1、ApplicationContext接口继承了ApplicationEventPublisher，并在AbstractApplicationContext实现了具体代码，实际执行是委托给ApplicationEventMulticaster（可以认为是多播）：<br/>
```
public void publishEvent(ApplicationEvent event) {  
    //省略部分代码  
    }  
    getApplicationEventMulticaster().multicastEvent(event);  
    if (this.parent != null) {  
        this.parent.publishEvent(event);  
    }  
}
```
我们常用的ApplicationContext都继承自AbstractApplicationContext，如ClassPathXmlApplicationContext、XmlWebApplicationContext等。所以自动拥有这个功能。<br/>
2、ApplicationContext自动到本地容器里找一个名字为”“的ApplicationEventMulticaster实现，如果没有自己new一个SimpleApplicationEventMulticaster。其中SimpleApplicationEventMulticaster发布事件的代码如下：<br/>
```
public void multicastEvent(final ApplicationEvent event) {  
    for (final ApplicationListener listener : getApplicationListeners(event)) {  
        Executor executor = getTaskExecutor();  
        if (executor != null) {  
            executor.execute(new Runnable() {  
                public void run() {  
                    listener.onApplicationEvent(event);  
                }  
            });  
        }  
        else {  
            listener.onApplicationEvent(event);  
        }  
    }  
} 
```
如果给它一个executor（java.util.concurrent.Executor），它就可以异步支持发布事件了。佛则就是通过发送。<br/>
所以我们发送事件只需要通过ApplicationContext.publishEvent即可，没必要再创建自己的实现了。除非有必要。<br/>
**监听器**<br/>
具体代表者是：ApplicationListener
1、其继承自JDK的EventListener，JDK要求所有监听器将继承它，比如我们的AWT事件体系也是继承自它；
2、ApplicationListener接口：
```
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {  
    void onApplicationEvent(E event);  
} 
```
其只提供了onApplicationEvent方法，我们需要在该方法实现内部判断事件类型来处理，也没有提供按顺序触发监听器的语义，所以Spring提供了另一个接口，SmartApplicationListener：

## Spring事件机制的简单例子
本例子模拟一个给多个人发送内容（类似于报纸新闻）的例子。<br/>
**1、定义事件**<br/>
```
package com.sishuok.hello;  
import org.springframework.context.ApplicationEvent;  
public class ContentEvent extends ApplicationEvent {  
    public ContentEvent(final String content) {  
        super(content);  
    }  
} 
非常简单，如果用户发送内容，只需要通过构造器传入内容，然后通过getSource即可获取。
```
**2、定义无序监听器**<br/>
无序，类似于AOP机制，顺序是无法确定
```
package com.sishuok.hello;  
import org.springframework.context.ApplicationEvent;  
import org.springframework.context.ApplicationListener;  
import org.springframework.stereotype.Component;  
@Component  
public class LisiListener implements ApplicationListener<ApplicationEvent> {  
    @Override  
    public void onApplicationEvent(final ApplicationEvent event) {  
        if(event instanceof ContentEvent) {  
            System.out.println("李四收到了新的内容：" + event.getSource());  
        }  
    }  
} 
1、使用@Compoent注册Bean即可；
2、在实现中需要判断event类型是ContentEvent才可以处理；

更简单的办法是通过泛型指定类型，如下所示
package com.sishuok.hello;  
import org.springframework.context.ApplicationListener;  
import org.springframework.stereotype.Component;  
@Component  
public class ZhangsanListener implements ApplicationListener<ContentEvent> {  
    @Override  
    public void onApplicationEvent(final ContentEvent event) {  
        System.out.println("张三收到了新的内容：" + event.getSource());  
    }  
} 
```
**3、定义有序监听器**<br/>
实现SmartApplicationListener接口即可。
```
package com.sishuok.hello;  
import org.springframework.context.ApplicationEvent;  
import org.springframework.context.event.SmartApplicationListener;  
import org.springframework.stereotype.Component;  
@Component  
public class WangwuListener implements SmartApplicationListener {  
    @Override  
    public boolean supportsEventType(final Class<? extends ApplicationEvent> eventType) {  
        return eventType == ContentEvent.class;  
    }  
    @Override  
    public boolean supportsSourceType(final Class<?> sourceType) {  
        return sourceType == String.class;  
    }  
    @Override  
    public void onApplicationEvent(final ApplicationEvent event) {  
        System.out.println("王五在孙六之前收到新的内容：" + event.getSource());  
    }  
    @Override  
    public int getOrder() {  
        return 1;  
    }  
}  

supportsEventType：用于指定支持的事件类型，只有支持的才调用onApplicationEvent；
supportsSourceType：支持的目标类型，只有支持的才调用onApplicationEvent；
getOrder：即顺序，越小优先级越高

package com.sishuok.hello;  
import org.springframework.context.ApplicationEvent;  
import org.springframework.context.event.SmartApplicationListener;  
import org.springframework.stereotype.Component;  
@Component  
public class SunliuListener implements SmartApplicationListener {  
    @Override  
    public boolean supportsEventType(final Class<? extends ApplicationEvent> eventType) {  
        return eventType == ContentEvent.class;  
    }  
    @Override  
    public boolean supportsSourceType(final Class<?> sourceType) {  
        return sourceType == String.class;  
    }  
    @Override  
    public void onApplicationEvent(final ApplicationEvent event) {  
        System.out.println("孙六在王五之后收到新的内容：" + event.getSource());  
    }  
    @Override  
    public int getOrder() {  
        return 2;  
    }  
} 
```

## 测试
**4.1配置文件**<br/>
```
<!-- 自动扫描注解Bean -->
<context:component-scan base-package="com.sishuok"/>  
```
**4.2、测试类**<br/>
```
package com.sishuok;  
import com.sishuok.hello.ContentEvent;  
import org.junit.Test;  
import org.junit.runner.RunWith;  
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.context.ApplicationContext;  
import org.springframework.test.context.ContextConfiguration;  
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;  
@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(locations={"classpath:spring-config-hello.xml"})  
public class HelloIT {  
    @Autowired  
    private ApplicationContext applicationContext;  
    @Test  
    public void testPublishEvent() {  
        applicationContext.publishEvent(new ContentEvent("今年是龙年的博客更新了"));  
    }  
}
王五在孙六之前收到新的内容：今年是龙年的博客更新了  
孙六在王五之后收到新的内容：今年是龙年的博客更新了  
李四收到了新的内容：今年是龙年的博客更新了  
张三收到了新的内容：今年是龙年的博客更新了  
```
## Spring事件机制实现之前提到的注册流程
Spring对异步事件机制的支持，实现方式有两种：<br/>
**1、全局异步**<br/>
即只要是触发事件都是以异步执行，具体配置（spring-config-register.xml）如下：
```
<task:executor id="executor" pool-size="10" />  
<!-- 名字必须是applicationEventMulticaster和messageSource是一样的，默认找这个名字的对象 -->  
<!-- 名字必须是applicationEventMulticaster，因为AbstractApplicationContext默认找个 -->  
<!-- 如果找不到就new一个，但不是异步调用而是同步调用 -->  
<bean id="applicationEventMulticaster" class="org.springframework.context.event.SimpleApplicationEventMulticaster">  
    <!-- 注入任务执行器 这样就实现了异步调用（缺点是全局的，要么全部异步，要么全部同步（删除这个属性即是同步））  -->  
    <property name="taskExecutor" ref="executor"/>  
</bean> 
通过注入taskExecutor来完成异步调用。具体实现可参考之前的代码介绍。这种方式的缺点很明显：要么大家都是异步，要么大家都不是。所以不推荐使用这种方式。
```
**2、更灵活的异步支持**<br/>
spring3提供了@Aync注解来完成异步调用。此时我们可以使用这个新特性来完成异步调用。不仅支持异步调用，还支持简单的任务调度，比如我的项目就去掉Quartz依赖，直接使用spring3这个新特性，具体可参考[spring-config.xml](https://github.com/zhangkaitao/es/blob/master/web/src/main/resources/spring-config.xml)。<br/>
 _2.1、开启异步调用支持_<br/>
 ```
 <!-- 开启@AspectJ AOP代理 -->  
 <aop:aspectj-autoproxy proxy-target-class="true"/>  
 <!-- 任务调度器 -->  
 <task:scheduler id="scheduler" pool-size="10"/>  
 <!-- 任务执行器 -->  
 <task:executor id="executor" pool-size="10"/>  
 <!--开启注解调度支持 @Async @Scheduled-->  
 <task:annotation-driven executor="executor" scheduler="scheduler" proxy-target-class="true"/>  
 ```
_2.2、配置监听器让其支持异步调用_<br/>
```
@Component  
public class EmailRegisterListener implements ApplicationListener<RegisterEvent> {  
    @Async  
    @Override  
    public void onApplicationEvent(final RegisterEvent event) {  
        System.out.println("注册成功，发送确认邮件给：" + ((User)event.getSource()).getUsername());  
    }
}
```
使用@Async注解即可，非常简单。
这样不仅可以支持通过调用，也支持异步调用，非常的灵活，实际应用推荐大家使用这种方式。







