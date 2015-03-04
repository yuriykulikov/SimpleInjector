package com.simple.injector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import com.simple.injector.Injector.Binder;
import com.simple.injector.Injector.IConfig;
import com.simple.injector.Injector.IProvider;

public class InjectorTest extends TestCase {

    private static class TestStatic
    {
        public TestStatic(List list)
        {
            
        }
    }

    public void testInjectClass() {
        IInjector injector = Injector.createInjector(new IConfig() {
            
            public void configure(Binder binder) {
                binder.bind(List.class).toInstance(new ArrayList());
                
            }
        });
        
        injector.getInstance(List.class);
    }
    
    public void testInjectClass2() {
        IInjector injector = Injector.createInjector(new IConfig() {
            
            public void configure(Binder binder) {
                binder.bind(Collection.class).toInstance(new ArrayList());
                binder.bind(List.class).to(ArrayList.class).asSingleton();;
                
            }
        });
        
        List inject = (List) injector.getInstance(List.class);
    }
    
    public void testInjectClass3() {
        IInjector injector = Injector.createInjector(new IConfig() {
            
            public void configure(Binder binder) {
                binder.bind(Collection.class).toProvider(new IProvider() {
                    
                    public Object provide(IInjector context) {
                        ArrayList arrayList = new ArrayList();
                        arrayList.add("provider!");
                        return arrayList;
                    }
                })
                .asSingleton();
                
                binder.bind(List.class).to(ArrayList.class).asSingleton();;
                
            }
        });
        
        List inject = (List) injector.getInstance(List.class);
    }
    
    
    public void testInjectClass4() {
        IInjector injector = Injector.createInjector(new IConfig() {
            
            public void configure(Binder binder) {
                binder.bind(Collection.class).toProvider(new IProvider() {
                    
                    public Object provide(IInjector context) {
                        ArrayList arrayList = new ArrayList();
                        arrayList.add("provider!");
                        return arrayList;
                    }
                })
                .asSingleton();
                
                binder.bind(TestStatic.class).asSingleton();
                binder.bind(List.class).to(ArrayList.class).asSingleton();
                
            }
        });
        
        TestStatic inject = (TestStatic) injector.getInstance(TestStatic.class);
        inject.toString();
    }

}
