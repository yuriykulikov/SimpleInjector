package com.simple.injector;

public interface IInjector {
    Object inject(Class clazz);

    Object inject(Object scope, Class clazz);
}
