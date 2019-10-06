package com.der.expand;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author K0790016
 **/
public class TestLookupMethod {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-expand.xml");
        FruitPlate fruitPlate1 = context.getBean("fruitPlate1", FruitPlate.class);
        FruitPlate fruitPlate2 = context.getBean("fruitPlate2", FruitPlate.class);
        fruitPlate1.getFruit();
        fruitPlate2.getFruit();
    }
}
