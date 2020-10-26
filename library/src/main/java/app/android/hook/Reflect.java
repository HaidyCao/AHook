package app.android.hook;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Reflect {

    private static final String TAG = Reflect.class.getSimpleName();
    private static Method gInvoke;
    private static Method gSetAccessible;

    static {
        try {
            gInvoke = Method.class.getDeclaredMethod("invoke", Object.class, Object[].class);
            gSetAccessible = Method.class.getMethod("setAccessible", boolean.class);
        } catch (Exception e) {
            logE(e.getMessage(), e);
        }
    }

    private static void logE(String message, Exception e) {
        System.out.println("logE() called with: message = [" + message + "], e = [" + e + "]");
//        Log.e(TAG, message, e);
    }

    public static Class<?> getClass(ClassLoader classLoader, String className) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return classLoader.loadClass(className);
            }

            return (Class<?>) invoke(classLoader, ClassLoader.class, "loadClass", new Class[]{String.class}, new Object[]{className});
        } catch (ClassNotFoundException e) {
            logE("getClass: " + e.getMessage() + "; className = " + className, e);
        }

        return null;
    }

    public static Class<?> getClass(String className) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return Class.forName(className);
            }

            Method method = Class.class.getDeclaredMethod("forName", String.class);
            return (Class<?>) method.invoke(null, className);
        } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException e) {
            logE("getClass: " + e.getMessage() + "; className = " + className, e);
        }

        return null;
    }

    private static Method getMethod(Class<?> clazz, String methodName, Class<?>[] paramsClasses) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return clazz.getDeclaredMethod(methodName, paramsClasses);
            }

            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            return (Method) invokeInternal(clazz, getDeclaredMethod, new Object[]{methodName, paramsClasses});
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logE("getMethod: " + e.getMessage() + "; methodName = " + methodName, e);
        }

        return null;
    }

    private static Field getField(Class<?> clazz, String field) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return clazz.getDeclaredField(field);
            }

            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredField", String.class);
            return (Field) getDeclaredMethod.invoke(clazz, field);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
            logE("getField: " + e.getMessage() + "; field = " + field, e);
        }

        return null;
    }
    
    private static Object invokeInternal(Object obj, Method method, Object[] params) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            method.setAccessible(true);
            return method.invoke(obj, method, params);
        }

        gSetAccessible.invoke(method, true);
        return gInvoke.invoke(method, new Object[]{obj, params});
    }

    public static Object invokeStatic(String className, String methodName) {
        return invokeStatic(getClass(className), methodName);
    }

    public static Object invokeStatic(Class<?> clazz, String methodName) {
        return invokeStatic(clazz, methodName, new Class[]{}, new Object[]{});
    }

    public static Object invokeStatic(String className, String methodName, Class<?>[] paramsClasses, Object[] params) {
        return invokeStatic(getClass(className), methodName, paramsClasses, params);
    }

    public static Object invokeStatic(Class<?> clazz, String methodName, Class<?>[] paramsClasses, Object[] params) {
        Method method = getMethod(clazz, methodName, paramsClasses);

        if (method == null) {
            return null;
        }

        try {
            return invokeInternal(null, method, params);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logE("invoke: " + e.getMessage(), e);
        }
        return null;
    }

    public static Object invoke(Object object, String methodName) {
        return invoke(object, object.getClass(), methodName, new Class[]{}, new Object[]{});
    }

    public static Object invoke(Object object, Class<?> clazz, String methodName) {
        return invoke(object, clazz, methodName, new Class[]{}, new Object[]{});
    }

    public static Object invoke(Object object, String methodName, Class<?>[] paramsClasses, Object[] params) {
        return invoke(object, object.getClass(), methodName, paramsClasses, params);
    }

    public static Object invoke(Object object, Class<?> clazz, String methodName, Class<?>[] paramsClasses, Object[] params) {
        Method method = getMethod(clazz, methodName, paramsClasses);

        if (method == null) {
            return null;
        }

        try {
            return invokeInternal(object, method, params);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logE("invoke: " + e.getMessage(), e);
        }
        return null;
    }

    public static Object getStaticField(String className, String fieldName) {
        return getStaticField(getClass(className), fieldName);
    }

    public static Object getStaticField(Class<?> clazz, String fieldName) {
        if (clazz == null) {
            return null;
        }

        Field field = getField(clazz, fieldName);
        if (field == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (IllegalAccessException e) {
            logE("field.get: " + e.getMessage(), e);
        }
        return null;
    }

    public static Object getField(Object object, String fieldName) {
        return getField(object.getClass(), object, fieldName);
    }

    public static Object getField(Class<?> clazz, Object object, String fieldName) {
        Field field = getField(clazz != null ? clazz : object.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(object);
        } catch (IllegalAccessException e) {
            logE("field.get: " + e.getMessage(), e);
        }
        return null;
    }

    public static void setStaticField(Class<?> clazz, String fieldName, Object v) {
        Field field = getField(clazz, fieldName);
        if (field == null) {
            logE("getField failed: " + fieldName, null);
            return;
        }

        try {
            field.setAccessible(true);
            field.set(null, v);
        } catch (IllegalAccessException e) {
            logE("field.get: " + e.getMessage(), e);
        }
    }

    public static void setField(Object object, String fieldName, Object v) {
        setField(object, object.getClass(), fieldName, v);
    }

    public static void setField(Object object, Class<?> clazz, String fieldName, Object v) {
        Field field = getField(clazz, fieldName);
        if (field == null) {
            logE("getField failed:" + fieldName, null);
            return;
        }

        try {
            field.setAccessible(true);
            field.set(object, v);
        } catch (IllegalAccessException e) {
            logE("field.get: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        invokeStatic(Reflect.class, "logE", new Class[]{String.class, Exception.class}, new Object[]{"Hello", new Exception("Bad")});
        invoke(new Test(), "a", new Class[]{}, new Object[]{});

        Test a = new Test();
        Test b = new Test();

        setStaticField(Test.class, "c", 4);
        setField(a, "a", 1024);
        setField(b, "b", "bbbbbbbbbbbbbbbbbbbbbbbb");

        System.out.println("a = " + getField(a, "a"));
        System.out.println("b = " + getField(b, "b"));
        System.out.println("c = " + getStaticField(Test.class, "c"));
    }

    private static class Test {

        private int a = 1;
        private String b = "b";
        private static int c = 3;

        private void a() {
            System.out.println("Test.a");
        }
    }
}
