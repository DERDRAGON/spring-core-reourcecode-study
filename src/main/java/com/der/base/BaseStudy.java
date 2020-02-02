package com.der.base;

import com.der.domain.TestEntity;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author K0790016
 **/
public class BaseStudy {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-start.xml");
        TestEntity bean = context.getBean(TestEntity.class);
        bean.send();
        context.close();
    }
}
