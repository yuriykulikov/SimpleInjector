package com.simple.injector;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Simple dependency injection framework.
 * 
 * Create using {@link #createInjector(IConfig)}. Configure bindings using
 * {@link IConfig}. Inject objects using {@link #getInstance(Class)}.
 */
public class Injector implements IInjector {
    
    public static final Object DEFAULT_SCOPE = "DEFAULT_SCOPE";
    
    /**
     * Implement to get asked to configure the {@link Injector} using a
     * {@link Binder}.
     */
    public interface IConfig {
        public void configure(Binder binder);
    }

    /**
     * Provide an object which requires something besides bind interfaces which
     * can be injected. Use when you have to provide additional arguments, e.g.
     * ID, name and so on.
     */
    public interface IProvider {
        Object provide(IInjector injector);
    }

    /**
     * Creates new bindings and stores them to be later used to inject objects.
     */
    public static class Binder {
        private final List bindings = new ArrayList();

        public Binding bind(Class clazz) {
            Binding binding = new Binding(clazz);
            bindings.add(binding);
            return binding;
        }

        List getBindings() {
            return bindings;
        }
    }

    /**
     * Ongoing binding.
     */
    public static class Binding {
        private final Class clazz;

        private Class boundToClazz;

        private boolean isSingleton;

        private Object instance = null;

        private IProvider provider = null;

        private Object scope = DEFAULT_SCOPE;

        public Binding(Class clazz) {
            this.clazz = clazz;
            this.boundToClazz = clazz;
        }

        public Binding to(Class boundToClazz) {
            this.boundToClazz = boundToClazz;
            return this;
        }

        public Binding toProvider(IProvider provider) {
            this.provider = provider;
            return this;
        }

        public void asSingleton() {
            this.isSingleton = true;
        }

        public void toInstance(Object instance) {
            this.instance = instance;
        }

        public Binding forScope(Object scope) {
            this.scope = scope;
            return this;
        }
    }

    private interface IFactory {
        public Object get(Class clazz);
    }

    private final Map scopes = new HashMap();

    public static IInjector createInjector(IConfig module) {
        return new Injector(module);
    }

    public Object getInstance(Class clazz) {
        return getInstance(DEFAULT_SCOPE, clazz);
    }

    public Object getInstance(Object scope, Class clazz) {
        Map factories = (Map) scopes.get(scope);
        IFactory factory = (IFactory)factories.get(clazz);
        if(factory ==null){
            try {
                return instantiate(clazz, this);
            } catch (Exception e){
                //TODO look in other scopes to help devs
                throw new NullPointerException("Class " + clazz + " was not bound for scope " + scope + ". Have you configured the injector correctly?");
            }
        }
        return checkNotNull(factory.get(clazz));
    }

    private Injector(IConfig module) {
        // someone will need the IInjector itself
        scopes.put(DEFAULT_SCOPE, new HashMap());
        ((Map) scopes.get(DEFAULT_SCOPE)).put(IInjector.class, new IFactory() {
            public Object get(Class clazz) {
                return Injector.this;
            }
        });

        Binder binder = new Binder();
        module.configure(binder);
        readConfig(binder);
    }

    private Object checkNotNull(Object object) {
        if (object == null) {
            throw new NullPointerException("Tried to pass null! Please fix instantiation sequence!");
        }
        return object;
    }

    private void readConfig(Binder binder) {
        for (Iterator iterator = binder.getBindings().iterator(); iterator.hasNext();) {
            addBinding((Binding) iterator.next());
        }
    }

    private void addBinding(final Binding binding) {
        if (binding.instance != null) {
            bindToInstance(binding);
        } else if (binding.isSingleton) {
            bindAsSingleton(binding);
        } else {
            throw new UnsupportedOperationException("not yet. Only singletons!");
        }
    }

    private void bindAsSingleton(final Binding binding) {
        getOrCreateScope(binding).put(binding.clazz, new SingletonFactory(binding));
    }

    private Map getOrCreateScope(final Binding binding) {
        Map scopeBindings = (Map) scopes.get(binding.scope);
        if(scopeBindings == null){
            scopeBindings = new HashMap();
            scopes.put(binding.scope, scopeBindings);
        }
        return scopeBindings;
    }

    private final class SingletonFactory implements IFactory {
        private final IProvider provider;

        private Object object;

        private SingletonFactory(final Binding binding) {
            if (binding.provider != null) {
                this.provider = binding.provider;
            } else {
                this.provider = new ConstructorInjectionProvider(binding);
            }
        }

        public synchronized Object get(Class clazz) {
            if (object == null) {
                object = provider.provide(Injector.this);
            }

            return object;
        }
    }

    private void bindToInstance(final Binding binding) {
        getOrCreateScope(binding).put(binding.clazz, new IFactory() {
            public Object get(Class clazz) {
                return binding.instance;
            }
        });
    }

    private static final class ConstructorInjectionProvider implements IProvider {
        private final Binding binding;
    
        private ConstructorInjectionProvider(Binding binding) {
            this.binding = binding;
        }
    
        public Object provide(IInjector injector) {
            return instantiate(binding.boundToClazz, injector);
        }
    }

    /**
     * Must have either no constructors or only one. All arguments must be
     * bound.
     */
    private static Object instantiate(final Class boundToClazz, IInjector injector) {
        try {
            Constructor[] constructors = boundToClazz.getConstructors();
            if (constructors[0].getParameterTypes().length == 0) {
                return instantiateWithDefaultConstructor(constructors[0]);
            } else if (constructors.length == 1) {
                return instantiateWithConstructorInjection(constructors[0], injector);
            }  else {
                return instantiateWithConstructorInjection(constructors[0], injector);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object instantiateWithDefaultConstructor(Constructor constructor) throws Exception {
        return constructor.newInstance(new Object[] {});
    }

    private static Object instantiateWithConstructorInjection(Constructor constructor, IInjector injector) throws Exception {
        Object[] parameterObjects = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class parameterClass = constructor.getParameterTypes()[i];
            parameterObjects[i] = injector.getInstance(parameterClass);
        }
        return constructor.newInstance(parameterObjects);
    }
}


