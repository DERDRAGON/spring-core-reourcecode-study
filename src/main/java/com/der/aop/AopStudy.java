package com.der.aop;

import com.der.domain.TestEntity;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author K0790016
 **/
public class AopStudy {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-aop.xml");
        TestEntity bean = context.getBean(TestEntity.class);
        bean.send();
        context.close();
    }
}
