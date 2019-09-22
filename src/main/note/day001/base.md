## 第一节学习

###ClassPathXmlApplicationContext

demo：com.der.day001.BaseStudy
```$xslt  主要片段
ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-start.xml");
TestEntity bean = context.getBean(TestEntity.class);
bean.send();
context.close();
```