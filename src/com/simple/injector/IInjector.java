package com.simple.injector;

public interface IInjector {
    Object getInstance(Class clazz);

    Object getInstance(Object scope, Class clazz);
}
