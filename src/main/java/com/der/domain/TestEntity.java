package com.der.domain;

import lombok.Data;

/**
 * @author K0790016
 **/
@Data
public class TestEntity {

    private Integer id;

    private String name;

    private Boolean sex;

    private Double height;

    public void send() {
        System.out.println("发送一个测试的消息");
    }

}
