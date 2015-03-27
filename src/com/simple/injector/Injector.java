package com.simple.injector;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Simple dependency injection framework.
 * 
 * Create using {@link #createInjector(IConfig)}. Configure bindings using
 * {@link IConfig}. Inject objects using {@link #getInstance(Class)}.
 */
public class Injector implements IInjector {

    public static final Object DEFAULT_SCOPE = "DEFAULT_SCOPE";
    private final boolean DBG;

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
        return new Injector(module, false);
    }
    
    public static IInjector createInjector(IConfig module, boolean debug) {
        return new Injector(module, debug);
    }

    public Object getInstance(Class clazz) {
        return getInstanceInternal(DEFAULT_SCOPE, clazz);
    }

    public Object getInstance(Object scope, Class clazz) {
        return getInstanceInternal(scope, clazz);
    }

    private Object getInstanceInternal(Object scope, Class clazz) {
        Map factories = (Map) scopes.get(scope);
        IFactory factory = (IFactory) factories.get(clazz);
        
        if (factory == null) {
            factory = (IFactory)  ((Map) scopes.get(DEFAULT_SCOPE)).get(clazz);
        }
        
        if (factory != null) {
            // this is an explicit binding, everything is fine
            final Object instance = checkNotNull(factory.get(clazz));
            saveForDump(clazz, instance, true);
            return instance;
            
        } else if (clazz.getConstructors().length > 0){
            // this is an implicit binding, we can try to instantiate if this is
            // a concrete class
            try {
                //TODO me not like this
                final Object  instance = instantiate(clazz, this, scope);
                saveForDump(clazz, instance, false);
                return instance;
            } catch (Exception e) {
                // TODO look in other scopes to help devs, dump something
                throw new RuntimeException("Class " + clazz + " was not bound for scope " + scope
                        + " and I was not able to instantiate it as a concrete class. Have you configured the injector correctly?", e);
            }
        } else {
            throw new RuntimeException("Class " + clazz + " was not bound for scope " + scope
                    + " and it has no public constructors. Have you configured the injector correctly?");
        }
    }

    private Injector(IConfig module, boolean debug) {
        this.DBG = debug;
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
        SingletonFactory singletonFactory = new SingletonFactory(binding);
        Map scope = getOrCreateScope(binding);
        if(scope.put(binding.clazz, singletonFactory) !=null){
            throw new RuntimeException("Binding for " + binding.clazz + " already existed!");
        }
        if(!binding.boundToClazz.equals(binding.clazz) && scope.put(binding.boundToClazz, singletonFactory)!=null){
            throw new RuntimeException("Binding for " + binding.boundToClazz + " already existed!");
        }
    }

    private Map getOrCreateScope(final Binding binding) {
        Map scopeBindings = (Map) scopes.get(binding.scope);
        if (scopeBindings == null) {
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
            return instantiate(binding.boundToClazz, (Injector) injector, binding.scope);
        }
    }

    /**
     * Must have either no constructors or only one. All arguments must be
     * bound.
     * @param scope TODO
     */
    private static Object instantiate(final Class boundToClazz, Injector injector, Object scope) {
        try {
            Constructor[] constructors = boundToClazz.getConstructors();
            if (constructors[0].getParameterTypes().length == 0) {
                return instantiateWithDefaultConstructor(constructors[0]);
            } else if (constructors.length == 1) {
                return instantiateWithConstructorInjection(constructors[0], injector, scope);
            } else {
                return instantiateWithConstructorInjection(constructors[0], injector, scope);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object instantiateWithDefaultConstructor(Constructor constructor) throws Exception {
        return constructor.newInstance(new Object[] {});
    }

    private static Object instantiateWithConstructorInjection(Constructor constructor, Injector injector, Object scope) throws Exception {
        Object[] parameterObjects = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class parameterClass = constructor.getParameterTypes()[i];
            parameterObjects[i] = injector.getInstanceInternal(scope, parameterClass);
        }
        return constructor.newInstance(parameterObjects);
    }

    /**
     * <Class, List<Object>>
     */
    private final Map createdInstances = new HashMap();
    
    private static class InstantiatedObject {
       public final Object object;
       public final boolean explicitBinding;
       public final List dependencies;
        public InstantiatedObject(Object object, boolean explicitBinding) {
            this.object = object;
            this.explicitBinding = explicitBinding;
            this.dependencies = new ArrayList();
            Constructor[] constructors = object.getClass().getConstructors();
            if(constructors.length == 1){
                dependencies.addAll( Arrays.asList(constructors[0].getParameterTypes()));
            }
        }
    }

    private void saveForDump(Class clazz, Object instance, boolean explicitBinding) {
        if (DBG){
            List list = (List) createdInstances.get(clazz);
            if (list == null){
                list = new ArrayList();
                createdInstances.put(clazz, list);
            }
            
            boolean foundSameObject = false;
            
            for (Iterator iterator = list.iterator(); iterator.hasNext();) {
                Object object = ((InstantiatedObject) iterator.next()).object;
                if (object == instance){
                    foundSameObject = true;
                }
            }
            
            if (!foundSameObject){
                list.add(new InstantiatedObject(instance, explicitBinding));
            }
        }
    }

    public List dump() {
        
        ArrayList arrayList = new ArrayList(); 
        // Bindings
    
        // instances
//        for (Iterator iterator = createdInstances.entrySet().iterator(); iterator.hasNext();) {
//            Entry entry = (Entry) iterator.next();
//            List list = (List) entry.getValue();
//            set.add(new StringBuffer().append("\n").append(entry.getKey()).append(" - ").append(list.size()).append(" instance").append(list.size() == 1 ? "" :"s"));
//            for (Iterator values = list.iterator(); values.hasNext();) {
//                InstantiatedObject object =  (InstantiatedObject) values.next();
//                buffer.append("    ").append(object.explicitBinding ? "" : "IMPLICIT ").append(object.object.getClass()).append(" - ").append(object.object).append("\n");
//            }
//        }
        
        //let's do some expensive stuff as this will be only a debug thing
        
        
        Set implementedBy = new HashSet();
        Set interfaces = new HashSet();
        
        // implemented by
        for (Iterator iterator = createdInstances.entrySet().iterator(); iterator.hasNext();) {
            Entry entry = (Entry) iterator.next();
            List list = (List) entry.getValue();
            for (Iterator values = list.iterator(); values.hasNext();) {
                InstantiatedObject object =  (InstantiatedObject) values.next();
                String implementation = object.object.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "_");
                String abstraction = ((Class)entry.getKey()).getSimpleName().replaceAll("[^A-Za-z0-9]", "_");
                if(!implementation.equals(abstraction)){
                    String string = new StringBuffer()
                            .append(implementation)
                            .append(" .up.|> ")
                            .append(abstraction)
                            .toString();
                    implementedBy.add(string);
                }
                
                if(((Class)entry.getKey()).isInterface()){
                    interfaces.add("interface " + abstraction);
                }
//                String string = object.object.toString();
//                String shortToString = string.indexOf('\n') >0 ?  string.substring(0, string.indexOf('\n')) : string;
//                buffer.append(shortToString.replaceAll("[^A-Za-z0-9]", "_")).append(" -up-|> ").append(object.object.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "_")).append("\n"); 
            }
        }
        
        //TODO build a map dependencies to dependees with back and forth zeugs
        
        Map dependenciesToDependees =  new HashMap();

        //to do this we have to save interfaces when we instantiate stuff
        for (Iterator iterator = createdInstances.values().iterator(); iterator.hasNext();) {
            List list = (List) iterator.next();
            for (Iterator values = list.iterator(); values.hasNext();) {
                InstantiatedObject object =  (InstantiatedObject) values.next();
                
                for (Iterator dependenciesIterator = object.dependencies.iterator(); dependenciesIterator.hasNext();) {
                    
                    Class dependencyClazz = (Class) dependenciesIterator.next();
                    String dependee = object.object.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "_");
                    String dependency = dependencyClazz.getSimpleName().replaceAll("[^A-Za-z0-9]", "_");
                    
           
                    if(!dependenciesToDependees.containsKey(dependency)){
                        dependenciesToDependees.put(dependency, new ArrayList());
                    }
                    
                  ((List)  dependenciesToDependees.get(dependency)).add(dependee);
                    
                }
            }
        }
        
        //dependency graph
        Set dependsOn = new HashSet();
        
        for (Iterator iterator = dependenciesToDependees.entrySet().iterator(); iterator.hasNext();) {
            Entry entry = (Entry) iterator.next();
            List dependees = (List)entry.getValue();
            for (Iterator values = dependees.iterator(); values.hasNext();) {
                String dependee =  (String) values.next();
                String string = new StringBuffer()
                        .append(dependee)
                        .append(dependees.size() > 5 ? " o---down- " : " o-down- " )
                        .append(entry.getKey())
                        .toString();
                dependsOn.add(string);
            }
        }

        arrayList.add("\n@startuml\n");
        arrayList.add("hide empty methods\n");
        arrayList.add("hide empty fields\n");
        arrayList.addAll(interfaces);
        arrayList.addAll(implementedBy);
        arrayList.addAll(dependsOn);
        arrayList.add("\n@enduml\n");
        return arrayList;
    }
}
