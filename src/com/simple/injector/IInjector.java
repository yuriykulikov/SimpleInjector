package com.simple.injector;

import java.util.List;

public interface IInjector {
    Object getInstance(Class clazz);

    Object getInstance(Object scope, Class clazz);
    
    List dump();
}
