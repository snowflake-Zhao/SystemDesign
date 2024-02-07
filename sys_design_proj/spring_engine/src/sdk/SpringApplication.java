package sdk;

import sdk.annotation.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class SpringApplication {
  private HashMap<String, Object> beanContainerMap = new HashMap<String, Object>();
  private HashMap<String, Object> proxyBeanContainerMap = new HashMap<String, Object>();
  private HashMap<String, AopConf> classAopConfMap = new HashMap<String, AopConf>();


  public static void run(Class<?> primarySource, String... args) {
    try {
      SpringApplication appContext = new SpringApplication();
      appContext.initBeanContainer(primarySource);

      appContext.aop();

      appContext.di();

      appContext.run(primarySource);


    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void aop() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    //filter the AOP configure class
    ArrayList<Class> aopConfigClassList = new ArrayList<Class>();
    for (String beanClass : beanContainerMap.keySet()) {
      Class<?> beanClazz = Class.forName(beanClass);
      for (Annotation annotation : beanClazz.getAnnotations()) {
        if (annotation instanceof Aspect) {
          aopConfigClassList.add(beanClazz);
        }
      }
    }
    //parse the AOP configure class
    for (Class aopConfigClass : aopConfigClassList) {
      Method[] declaredMethods = aopConfigClass.getDeclaredMethods();
      for (Method method : declaredMethods) {
        Annotation[] annotations = method.getAnnotations();
        for (Annotation annotation : annotations) {
          if (annotation instanceof Before) {
            String value = ((Before) annotation).value();
            if (!classAopConfMap.containsKey(aopConfigClass.getCanonicalName())) {
              AopConf aopConf = new AopConf();
              int sepIdx = value.lastIndexOf(".");
              aopConf.aspect = aopConfigClass.getCanonicalName();
              aopConf.targetClass = value.substring(0, sepIdx);
              aopConf.targetMethod = value.substring(sepIdx + 1);
              aopConf.beforeMethod = method;
              classAopConfMap.put(aopConfigClass.getCanonicalName(), aopConf);
              continue;
            }
            classAopConfMap.get(aopConfigClass.getCanonicalName()).beforeMethod = method;
          }
          if (annotation instanceof After) {
            String value = ((After) annotation).value();
            if (!classAopConfMap.containsKey(aopConfigClass.getCanonicalName())) {
              AopConf aopConf = new AopConf();
              int sepIdx = value.lastIndexOf(".");
              aopConf.aspect = aopConfigClass.getCanonicalName();
              aopConf.targetClass = value.substring(0, sepIdx);
              aopConf.targetMethod = value.substring(sepIdx + 1);
              aopConf.afterMethod = method;
              classAopConfMap.put(aopConfigClass.getCanonicalName(), aopConf);
              continue;
            }
            classAopConfMap.get(aopConfigClass.getCanonicalName()).afterMethod = method;
          }
        }
      }
    }

    for (String aopClass : classAopConfMap.keySet()) {
      AopConf aopConf = classAopConfMap.get(aopClass);
      final Method beforeMethod = aopConf.beforeMethod;
      final Method afterMethod = aopConf.afterMethod;
      final String targetMethod = aopConf.targetMethod;
      Object aspectObj = Class.forName(aopConf.aspect).newInstance();
      Class<?> beanClass = Class.forName(aopConf.targetClass);
      Object bean = beanContainerMap.get(beanClass.getCanonicalName());
      Object proxyBean = Proxy.newProxyInstance(beanClass.getClassLoader(),
                                                beanClass.getInterfaces(), new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (beforeMethod != null && methodName.equals(targetMethod)) {
              beforeMethod.invoke(aspectObj, method, args);
            }
            Object ref = method.invoke(bean, args);
            if (afterMethod != null && methodName.equals(targetMethod)) {
              afterMethod.invoke(aspectObj, method, args);
            }
            return ref;
          }
        });
      proxyBeanContainerMap.put(beanClass.getCanonicalName(), proxyBean);
    }
  }

  private void run(Class<?> primarySource) throws Exception {
    if (sdk.CommandLineRunner.class.isAssignableFrom(primarySource)) {
      sdk.CommandLineRunner commandLineRunner = (sdk.CommandLineRunner) beanContainerMap.get(primarySource.getCanonicalName());
      commandLineRunner.run();
    }

  }

  private void di() throws IllegalAccessException, ClassNotFoundException {
    for (String beanClass : beanContainerMap.keySet()) {
      Class<?> beanClazz = Class.forName(beanClass);
      for (Field field : beanClazz.getDeclaredFields()) {
        for (Annotation annotation : field.getAnnotations()) {
          if (annotation instanceof Autowired) {
            field.setAccessible(true);
            Class<?> fieldClass = field.getType();
            for (String clazz : beanContainerMap.keySet()) {
              if (fieldClass.isAssignableFrom(Class.forName(clazz))) {
                Object injectedField = proxyBeanContainerMap.get(clazz);
                if (injectedField == null) {
                  injectedField = beanContainerMap.get(clazz);
                }
                field.set(beanContainerMap.get(beanClass), injectedField);
                break;
              }
            }
          }
        }
      }
    }
  }

  private void initBeanContainer(Class<?> primarySource) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    File appFile = new File(primarySource.getProtectionDomain().getCodeSource().getLocation().getPath());
    ArrayList<File> processAppDirList = new ArrayList<File>();
    processAppDirList.add(appFile);
    while (appFile != null && processAppDirList.size() > 0) {
      File curFile = processAppDirList.remove(0);
      if (curFile.isDirectory()) {
        File[] files = curFile.listFiles();
        processAppDirList.addAll(Arrays.asList(files));
        continue;
      }
      String endExt = ".class";
      String curFilePath = curFile.getPath();
      if (!curFilePath.endsWith(endExt)) {
        continue;
      }
      String filenameWithoutExt = curFilePath.substring(appFile.getPath().length() + 1, curFilePath.indexOf(endExt)).replace("\\", ".");
      Class<?> clazz = Class.forName(filenameWithoutExt);
      for (Annotation annotation : clazz.getAnnotations()) {
        if (annotation instanceof Component) {
          beanContainerMap.put(clazz.getCanonicalName(), clazz.newInstance());
        }
      }
    }
  }

  class AopConf {
    String aspect;
    String targetClass;
    String targetMethod;
    Method beforeMethod;
    Method afterMethod;
  }
}
