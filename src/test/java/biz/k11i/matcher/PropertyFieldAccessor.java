package biz.k11i.matcher;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指定されたクラスのプロパティ / フィールドにアクセスする機能を提供します。
 * <p>
 * JavaBeans の API を利用して getter メソッド経由でフィールドにアクセスしたり、
 * public なフィールドについては直接アクセスします。
 * </p>
 * <p>
 * フィールド宣言をもとにプロパティアクセスを試みる関係上、対応するフィールドがなく
 * getter / setter メソッドだけが定義されているプロパティは現状の実装では
 * 対応できていません。
 * </p>
 *
 * @author KOMIYA Atsushi
 */
public class PropertyFieldAccessor {
    public static interface Accessor {
        Object get(Object target);
    }

    static class PropertyAccessor implements Accessor {
        private final PropertyDescriptor descriptor;

        PropertyAccessor(PropertyDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Object get(Object target) {
            try {
                return descriptor.getReadMethod().invoke(target, (Object[]) null);

            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("想定外の例外が発生しました", e);
            }
        }

        static Accessor tryNewOrNull(Field field, Class clazz) {
            try {
                PropertyDescriptor descriptor = new PropertyDescriptor(field.getName(), clazz);
                return new PropertyAccessor(descriptor);

            } catch (IntrospectionException e) {
                return null;
            }
        }
    }

    static class PublicFieldAccessor implements Accessor {
        private final Field field;

        PublicFieldAccessor(Field field) {
            this.field = field;
        }

        static Accessor tryNewOrNull(Field field) {
            if (Modifier.isPublic(field.getModifiers())) {
                return new PublicFieldAccessor(field);
            }

            return null;
        }

        @Override
        public Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("想定外の例外が発生しました", e);
            }
        }
    }

    /** 一度生成した PropertyFieldAccessor オブジェクトは LRU キャッシュで保持します */
    private static Map<String, PropertyFieldAccessor> cache = new LinkedHashMap<String, PropertyFieldAccessor>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PropertyFieldAccessor> eldest) {
            return size() > 256;
        }
    };

    /** */
    private final Map<String, Accessor> accessors;

    PropertyFieldAccessor(Map<String, Accessor> accessors) {
        this.accessors = accessors;
    }

    public static PropertyFieldAccessor newInstance(Class clazz) {
        if (cache.containsKey(clazz.getName())) {
            return cache.get(clazz.getName());
        }

        Map<String, Accessor> accessors = prepareAccessors(clazz);
        PropertyFieldAccessor accessor = new PropertyFieldAccessor(accessors);

        cache.put(clazz.getName(), accessor);

        return accessor;
    }

    static Map<String, Accessor> prepareAccessors(Class clazz) {
        Map<String, Accessor> accessors = new LinkedHashMap<>();

        for (Field field : clazz.getDeclaredFields()) {
            Accessor accessor = PropertyAccessor.tryNewOrNull(field, clazz);
            if (accessor == null) {
                accessor = PublicFieldAccessor.tryNewOrNull(field);
            }

            if (accessor == null) {
                continue;
            }

            accessors.put(field.getName(), accessor);
        }

        return accessors;
    }

    public Iterable<Map.Entry<String, Accessor>> newIterable() {
        return new Iterable<Map.Entry<String, Accessor>>() {
            @Override
            public Iterator<Map.Entry<String, Accessor>> iterator() {
                return accessors.entrySet().iterator();
            }
        };
    }
}
