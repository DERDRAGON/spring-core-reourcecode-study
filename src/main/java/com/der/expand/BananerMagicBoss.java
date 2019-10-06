package com.der.expand;

import org.springframework.beans.factory.support.MethodReplacer;

import java.lang.reflect.Method;

/**
 * @author K0790016
 **/
public class BananerMagicBoss implements MethodReplacer {

    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        return new Bananer();
    }
}
