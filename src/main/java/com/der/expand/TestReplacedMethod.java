package com.der.expand;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author K0790016
 **/
public class TestReplacedMethod {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-expand.xml");
        MagicBoss magicBoss = context.getBean("appleMagicBoss", MagicBoss.class);
        Fruit fruit = magicBoss.getFruit();
        System.out.println(fruit.getClass());
    }
}
