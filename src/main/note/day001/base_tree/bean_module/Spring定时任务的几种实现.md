# Spring定时任务的几种实现

## 一、分类
- **从实现的技术上来分类，目前主要有三种技术（或者说有三种产品）：**<br/>
Java自带的java.util.Timer类，这个类允许你调度一个java.util.TimerTask任务。使用这种方式可以让你的程序按照某一个频度执行，但不能在指定时间运行。一般用的较少，这篇文章将不做详细介绍。<br/>
使用Quartz，这是一个功能比较强大的的调度器，可以让你的程序在指定时间执行，也可以按照某一个频度执行，配置起来稍显复杂。<br/>
Spring3.0以后自带的task，可以将它看成一个轻量级的Quartz，而且使用起来比Quartz简单许多。<br/>
- **从作业类的继承方式来讲，可以分为两类：**<br/>
作业类需要继承自特定的作业类基类，如Quartz中需要继承自org.springframework.scheduling.quartz.QuartzJobBean；java.util.Timer中需要继承自java.util.TimerTask。<br/>
作业类即普通的java类，不需要继承自任何基类。
- **从任务调度的触发时机来分，这里主要是针对作业使用的触发器，主要有以下两种：**<br/>
每隔指定时间则触发一次，在Quartz中对应的触发器为：org.springframework.scheduling.quartz.SimpleTriggerBean <br/>
每到指定时间则触发一次，在Quartz中对应的调度器为：org.springframework.scheduling.quartz.CronTriggerBean <br/>
注：并非每种任务都可以使用这两种触发器，如java.util.TimerTask任务就只能使用第一种。Quartz和spring task都可以支持这两种触发条件。<br/>

## 二、用法说明
### **Quartz**
**第一步，作业类继承自特定的基类：org.springframework.scheduling.quartz.QuartzJobBean**。<br/>
**第二步：spring配置文件中配置作业类JobDetailBean（旧版），JobDetailFactoryBean（新版）** <br/>
```
eg: com.der.expand.timedTask.QuartzJobBeanSupport
<!--  两个属性，jobClass属性即我们在java代码中定义的任务类，jobDataAsMap属性即该任务类中需要注入的属性值。 -->
<bean name="job1" class="org.springframework.scheduling.quartz.JobDetailFactoryBean">
    <property name="jobClass" value="com.der.expand.timedTask.QuartzJobBeanSupport" />
    <property name="jobDataAsMap">
        <map>
            <entry key="timeout" value="0" />
        </map>
    </property>
</bean>
```
**第三步：配置作业调度的触发方式（触发器）**
Quartz的作业触发器有两种，分别是<br/>
org.springframework.scheduling.quartz.SimpleTriggerBean --> org.springframework.scheduling.quartz.SimpleTriggerFactoryBean<br/>
org.springframework.scheduling.quartz.CronTriggerBean --> org.springframework.scheduling.quartz.CronTriggerFactoryBean<br/>
第一种SimpleTriggerBean，只支持按照一定频度调用任务，如每隔30分钟运行一次。<br/>
配置方式如下：<br/>
```
<bean id="simpleTrigger" class="org.springframework.scheduling.quartz.SimpleTriggerFactoryBean">
    <property name="jobDetail" ref="job1" />
    <property name="startDelay" value="0" /><!-- 调度工厂实例化后，经过0秒开始执行调度 -->
    <property name="repeatInterval" value="2000" /><!-- 每2秒调度一次 -->
</bean>
```
第二种CronTriggerBean，支持到指定时间运行一次，如每天12:00运行一次等。<br/>
 关于cronExpression表达式的语法参见附录。<br/>
配置方式如下：<br/>
```
<bean id="cronTrigger" class="org.springframework.scheduling.quartz.CronTriggerFactoryBean">  
    <property name="jobDetail" ref="job1" />  
    <!—每天12:00运行一次 -->  
    <property name="cronExpression" value="0 0 12 * * ?" />  
</bean>
```
**第四步：配置调度工厂**
```
说明：该参数指定的就是之前配置的触发器的名字。
<bean class="org.springframework.scheduling.quartz.SchedulerFactoryBean">  
    <property name="triggers">  
    <list>  
    <ref bean="cronTrigger" />  
    </list>  
    </property>  
</bean> 
```

### 第二种，作业类不继承特定基类。
Spring能够支持这种方式，归功于两个类：<br/>
org.springframework.scheduling.timer.MethodInvokingTimerTaskFactoryBean<br/>
org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean<br/>
这两个类分别对应spring支持的两种实现任务调度的方式，即前文提到到java自带的timer task方式和Quartz方式。<br/>
这里我只写MethodInvokingJobDetailFactoryBean的用法，使用该类的好处是,我们的任务类不再需要继承自任何类，而是普通的pojo。<br/>
**第一步：编写任务类**
`com.der.expand.timedTask.NormalTimeTask`
**第二步：配置作业类**
```
<bean id="job2"  
    class="org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean">  
    <property name="targetObject">  
    <bean class="com.der.expand.timedTask.NormalTimeTask" />  
    </property>  
    <property name="targetMethod" value="doJob" />  
    <property name="concurrent" value="false" /><!-- 作业不并发调度 -->  
</bean>
```
说明：这一步是关键步骤，声明一个MethodInvokingJobDetailFactoryBean，有两个关键属性：targetObject指定任务类，targetMethod指定运行的方法。
往下的步骤就与方法一相同了
**第三步：配置作业调度的触发方式（触发器）**
Quartz的作业触发器有两种，分别是<br/>
org.springframework.scheduling.quartz.SimpleTriggerBean<br/>
org.springframework.scheduling.quartz.CronTriggerBean<br/>
第一种SimpleTriggerBean，只支持按照一定频度调用任务，如每隔30分钟运行一次。<br/>
配置方式如下：
```
<bean id="simpleTrigger" class="org.springframework.scheduling.quartz.SimpleTriggerBean">  
    <property name="jobDetail" ref="job2" />  
    <property name="startDelay" value="0" /><!-- 调度工厂实例化后，经过0秒开始执行调度 -->  
    <property name="repeatInterval" value="2000" /><!-- 每2秒调度一次 -->  
</bean> 
```
第二种CronTriggerBean，支持到指定时间运行一次，如每天12:00运行一次等。<br/>
配置方式如下：<br/>
```
<bean id="cronTrigger" class="org.springframework.scheduling.quartz.CronTriggerBean">  
    <property name="jobDetail" ref="job2" />  
    <!—每天12:00运行一次 -->  
    <property name="cronExpression" value="0 0 12 * * ?" />  
</bean>  
```
**第四步：配置调度工厂**
```
说明：该参数指定的就是之前配置的触发器的名字
<bean class="org.springframework.scheduling.quartz.SchedulerFactoryBean">  
    <property name="triggers">  
    <list>  
    <ref bean="cronTrigger" />  
    </list>  
    </property>  
</bean> 
```

### Spring-Task
Spring3.0以后自主开发的定时任务工具，spring task，可以将它比作一个轻量级的Quartz，而且使用起来很简单，除spring相关的包外不需要额外的包，而且支持注解和配置文件两种。

#### 第一种：配置文件方式
**第一步：编写作业类**
```
import org.springframework.stereotype.Service;  
@Service  
public class TaskJob {  
    public void job1() {  
        System.out.println(“任务进行中。。。”);  
    }  
}  
```
**第二步：在spring配置文件头中添加命名空间及描述**
```
<beans xmlns="http://www.springframework.org/schema/beans"  
    xmlns:task="http://www.springframework.org/schema/task"   
    。。。。。。  
    xsi:schemaLocation="http://www.springframework.org/schema/task http://www.springframework.org/schema/task/spring-task-3.0.xsd"> 
```
**第三步：spring配置文件中设置具体的任务**
```
 <task:scheduled-tasks>   
        <!-- ref参数指定的即任务类，method指定的即需要运行的方法，cron及cronExpression表达式 -->
        <task:scheduled ref="taskJob" method="job1" cron="0 * * * * ?"/>   
</task:scheduled-tasks>  
<!-- spring扫描注解 -->
<context:component-scan base-package=" com.gy.mytask " /> 
```
#### 第二种：使用注解形式
我们可以使用注解@Scheduled，源文件中该注解的定义：
```
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules.class)
public @interface Scheduled {
	String CRON_DISABLED = "-";
	// cron：指定cron表达式
	String cron() default "";
	String zone() default "";
	// 即表示从上一个任务完成开始到下一个任务开始的间隔，单位是毫秒。
	long fixedDelay() default -1;
	String fixedDelayString() default "";
	// 即从上一个任务开始到下一个任务开始的间隔，单位是毫秒。
	long fixedRate() default -1;
	String fixedRateString() default "";
	long initialDelay() default -1;
	String initialDelayString() default "";
}
```
**第一步：编写pojo**
```
import org.springframework.scheduling.annotation.Scheduled;    
import org.springframework.stereotype.Component;  
@Component(“taskJob”)  
public class TaskJob {  
    @Scheduled(cron = "0 0 3 * * ?")  
    public void job1() {  
        System.out.println(“任务进行中。。。”);  
    }  
} 
```
**二步：添加task相关的配置：**
```
理论上只需要加上<task:annotation-driven />这句配置就可以了，这些参数都不是必须的。
<?xml version="1.0" encoding="UTF-8"?>  
<beans xmlns="http://www.springframework.org/schema/beans"  
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:aop="http://www.springframework.org/schema/aop"  
    xmlns:context="http://www.springframework.org/schema/context"  
    xmlns:tx="http://www.springframework.org/schema/tx"  
    xmlns:task="http://www.springframework.org/schema/task"  
    xsi:schemaLocation="  
        http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd  
        http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop-3.0.xsd  
        http://www.springframework.org/schema/context   
        http://www.springframework.org/schema/jdbc/spring-jdbc-3.0.xsd  
        http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-3.0.xsd  
        http://www.springframework.org/schema/task http://www.springframework.org/schema/task/spring-task-3.0.xsd"  
    default-lazy-init="false">  
  
    <context:annotation-config />  
    <!—spring扫描注解的配置   -->  
    <context:component-scan base-package="com.gy.mytask" />  
      
    <!—开启这个配置，spring才能识别@Scheduled注解   -->  
    <task:annotation-driven scheduler="qbScheduler" mode="proxy"/>  
    <task:scheduler id="qbScheduler" pool-size="10"/> 
```

PS: 常用CRON表达式
```
"0 0 12 * * ?"    每天中午十二点触发 
"0 15 10 ? * *"    每天早上10：15触发 
"0 15 10 * * ?"    每天早上10：15触发 
"0 15 10 * * ? *"    每天早上10：15触发 
"0 15 10 * * ? 2005"    2005年的每天早上10：15触发 
"0 * 14 * * ?"    每天从下午2点开始到2点59分每分钟一次触发 
"0 0/5 14 * * ?"    每天从下午2点开始到2：55分结束每5分钟一次触发 
"0 0/5 14,18 * * ?"    每天的下午2点至2：55和6点至6点55分两个时间段内每5分钟一次触发 
"0 0-5 14 * * ?"    每天14:00至14:05每分钟一次触发 
"0 10,44 14 ? 3 WED"    三月的每周三的14：10和14：44触发 
"0 15 10 ? * MON-FRI"    每个周一、周二、周三、周四、周五的10：15触发 
```




