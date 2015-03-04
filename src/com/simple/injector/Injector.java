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
        Object provide(IInjector context);
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

        private Object scope;

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

    private final Map factories = new HashMap();

    public static IInjector createInjector(IConfig module) {
        return new Injector(module);
    }

    public Object getInstance(Class clazz) {
        return checkNotNull(((IFactory) factories.get(clazz)).get(clazz));
    }

    public Object getInstance(Object scope, Class clazz) {
        return getInstance(clazz);
    }

    private Injector(IConfig module) {
        // someone will need the IInjector itself
        factories.put(IInjector.class, new IFactory() {
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
        factories.put(binding.clazz, new SingletonFactory(binding));
    }

    private final class SingletonFactory implements IFactory {
        private final IProvider provider;

        private Object object;

        private SingletonFactory(final Binding binding) {
            if (binding.provider != null) {
                this.provider = binding.provider;
            } else {
                this.provider = new ConstructorInjectionProvider(binding, Injector.this);
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
        factories.put(binding.clazz, new IFactory() {
            public Object get(Class clazz) {
                return binding.instance;
            }
        });
    }

    private static final class ConstructorInjectionProvider implements IProvider {
        private final Binding binding;
        private final Injector injector;

        private ConstructorInjectionProvider(Binding binding, Injector injector) {
            this.binding = binding;
            this.injector = injector;
        }

        public Object provide(IInjector context) {
            return instantiate(binding);
        }

        /**
         * Must have either no constructors or only one. All arguments must be
         * bound.
         */
        private Object instantiate(final Binding binding) {
            try {
                Constructor[] constructors = binding.boundToClazz.getConstructors();
                if (constructors[0].getParameterTypes().length == 0) {
                    return instantiateWithDefaultConstructor(constructors[0]);
                } else if (constructors.length == 1) {
                    return instantiateWithConstructorInjection(binding, constructors[0]);
                }  else {
                    return instantiateWithConstructorInjection(binding, constructors[0]);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Object instantiateWithDefaultConstructor(Constructor constructor) throws Exception {
            return constructor.newInstance(new Object[] {});
        }

        private Object instantiateWithConstructorInjection(final Binding binding, Constructor constructor) throws Exception {
            Object[] parameterObjects = new Object[constructor.getParameterTypes().length];
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                Class parameterClass = constructor.getParameterTypes()[i];
                parameterObjects[i] = injector.getInstance(parameterClass);
            }
            return constructor.newInstance(parameterObjects);
        }
    }
}
