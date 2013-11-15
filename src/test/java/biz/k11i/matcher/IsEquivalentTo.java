package biz.k11i.matcher;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 二つのオブジェクトを照合する {@link BaseMatcher} の実装です。
 * <p>
 * このクラスでは、オブジェクトの型ごとに、以下の照合処理をします。
 * <ul>
 * <li>{@link java.util.List} オブジェクト ... それぞれのリストの要素が一致していることを確認する。</li>
 * <li>配列 ... リストと同じ扱い。</li>
 * <li>{@link java.util.Map} オブジェクト ... それぞれのマップを構成するキーセットが同じで、かつ対応する値も一致していることを確認する。</li>
 * <li>その他のオブジェクト ... {@link #equals(Object)} により、値が一致していることを確認する。</li>
 * </ul>
 * </p>
 *
 * @author KOMIYA Atsushi
 */
public class IsEquivalentTo extends BaseMatcher<Object> {
    private final MatchingContext matchingContext = new MatchingContext();
    private final Object expectedObject;

    private IsEquivalentTo(Object expectedObject) {
        this.expectedObject = expectedObject;
    }

    @Override
    public boolean matches(Object item) {
        return matchingContext.matches(expectedObject, item);
    }

    @Override
    public void describeTo(Description description) {
        description
                .appendText("object" + matchingContext.fixedObjectHierarchy())
                .appendText(" ")
                .appendText(matchingContext.expectedMessage());
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        description
                .appendText("object" + matchingContext.fixedObjectHierarchy())
                .appendText(" ")
                .appendText(matchingContext.mismatchMessage());
    }

    /**
     * @param expectedObject 期待される結果を表すオブジェクト
     * @return 生成された IsEquivalentTo
     */
    public static IsEquivalentTo isEquivalentTo(Object expectedObject) {
        return new IsEquivalentTo(expectedObject);
    }

    public IsEquivalentTo exclude(PathPattern... pathPatterns) {
        matchingContext.addExcludePaths(pathPatterns);
        return this;
    }

    public static PathPattern pathPattern(String pathPattern) {
        return PathPattern.compile(pathPattern);
    }
}

/**
 * 二つのオブジェクトの比較をします。
 *
 * @author KOMIYA Atsushi
 */
class MatchingContext {
    private List<PathPattern> excludePaths = new ArrayList<>();

    private ObjectPath currentPath = new ObjectPath();
    private String fixedObjectHierarchy;

    private String expectedMessage;
    private String mismatchMessage;

    void addExcludePaths(PathPattern[] pathPatterns) {
        Collections.addAll(excludePaths, pathPatterns);
    }

    boolean matches(Object expectedObject, Object actualObject) {
        InternalMatcher subMatcher = newInternalMatcher(expectedObject);
        return subMatcher.matches(actualObject);
    }

    String expectedMessage() {
        return expectedMessage;
    }

    String mismatchMessage() {
        return mismatchMessage;
    }

    String fixedObjectHierarchy() {
        return fixedObjectHierarchy;
    }

    void setExpectedMessage(String messageFormat, Object... args) {
        expectedMessage = String.format(messageFormat, args);
    }

    void setMismatchMessage(String messageFormat, Object... args) {
        mismatchMessage = String.format(messageFormat, args);
    }

    void fixObjectHierarchyForMessaging() {
        fixedObjectHierarchy = currentPath.toString();
    }

    boolean needVerification(ObjectPath.Element.Type type, String name) {
        boolean need = true;

        // needsConsiderationOfElementOrder() とは呼び出しタイミングが
        // ことなるため、こちらは仮に currentPath に積む必要がある
        currentPath.push(type.newElement(name));

        for (PathPattern excludePath : excludePaths) {
            if (excludePath.isAcceptable(currentPath)) {
                need = false;
                break;
            }
        }

        currentPath.pop();
        return need;
    }

    @SuppressWarnings("unchecked")
    InternalMatcher newInternalMatcher(Object targetObject) {
        if (targetObject == null) {
            return new NullMatcher();
        }

        Class clazz = targetObject.getClass();
        if (clazz.isArray()) {
            return new ArrayMatcher(targetObject);
        }

        if (List.class.isAssignableFrom(clazz)) {
            // TODO 順序を無視するかどうかの確認が、ここで必要となる
            return new ListMatcher(List.class.cast(targetObject));
        }

        if (Map.class.isAssignableFrom(clazz)) {
            return new MapMatcher(Map.class.cast(targetObject));
        }

        if (!hasOverridenEqualsMethod(clazz)) {
            return new PropertyEnumerationMatcher(targetObject);
        }

        return new EqualsMatcher(targetObject);
    }

    static boolean hasOverridenEqualsMethod(Class clazz) {
        try {
            Method equalsMethod = clazz.getMethod("equals", Object.class);
            return !"java.lang.Object".equals(equalsMethod.getDeclaringClass().getName());

        } catch (NoSuchMethodException e) {
            // TODO エラーメッセージ
            throw new RuntimeException("");
        }
    }

    interface InternalMatcher {
        boolean matches(Object object);
    }

    abstract class InternalMatcherBase implements InternalMatcher {
        boolean forwardMatching(ObjectPath.Element.Type type, Pair<String, Object> expected, Object actual) {
            currentPath.push(type.newElement(expected.first));

            InternalMatcher subMatcher = newInternalMatcher(expected.second);
            boolean result = subMatcher.matches(actual);

            currentPath.pop();

            return result;
        }
    }

    /**
     * 期待される値が null 値の場合の InternalMatcher の実装です。
     *
     * @author KOMIYA Atsushi
     */
    class NullMatcher extends InternalMatcherBase {
        @Override
        public boolean matches(Object object) {
            if (object != null) {
                setExpectedMessage("は null");
                setMismatchMessage("は null ではありません");
                fixObjectHierarchyForMessaging();
                return false;
            }

            return true;
        }
    }

    /**
     * {@link Object#equals(Object)} で照合処理を行う InternalMatcher の実装です。
     *
     * @author KOMIYA Atsushi
     */
    class EqualsMatcher extends InternalMatcherBase {
        private Object expectedObject;

        public EqualsMatcher(Object expectedObject) {
            this.expectedObject = expectedObject;
        }

        @Override
        public boolean matches(Object object) {
            if (object == null) {
                setExpectedMessage("は %s", expectedObject);
                setMismatchMessage("は null です");
                fixObjectHierarchyForMessaging();
                return false;
            }

            if (!expectedObject.equals(object)) {
                setExpectedMessage("は %s", expectedObject);
                setMismatchMessage("は %s です", object);
                fixObjectHierarchyForMessaging();
                return false;
            }

            return true;
        }
    }

    /**
     * 期待される値のオブジェクトに対して、プロパティを列挙して参照する InternalMatcher の実装です。
     *
     * @author KOMIYA Atsushi
     */
    class PropertyEnumerationMatcher extends InternalMatcherBase {
        private final Object expectedObject;

        PropertyEnumerationMatcher(Object expectedObject) {
            this.expectedObject = expectedObject;
        }

        @Override
        public boolean matches(Object object) {
            if (object == null) {
                setExpectedMessage("は %s オブジェクト %s", expectedMessage.getClass(), expectedMessage);
                setMismatchMessage("は null です");
                fixObjectHierarchyForMessaging();
                return false;
            }

            if (!expectedObject.getClass().equals(object.getClass())) {
                setExpectedMessage("は %s オブジェクト %s", expectedMessage.getClass(), expectedMessage);
                setMismatchMessage("は %s オブジェクトではありません (%s, %s)", object.getClass(), object);
                fixObjectHierarchyForMessaging();
                return false;
            }

            for (PropertyIterator expectedIterator = new PropertyIterator(expectedObject),
                         actualIterator = new PropertyIterator(object);
                 expectedIterator.hasNext() && actualIterator.hasNext(); ) {
                Pair<String, Object> expected = expectedIterator.next();
                Pair<String, Object> actual = actualIterator.next();

                if (!forwardMatching(ObjectPath.Element.Type.OBJECT, expected, actual.second)) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * 期待される値が {@link List} オブジェクトの場合の InternalMatcher の実装です。
     * <p/>
     * 以下の照合をします。
     * <ul>
     * <li>actual が null でないこと</li>
     * <li>リストの長さが同じであること</li>
     * <li>(同じインデックスの要素が等しいこと)</li>
     * </ul>
     *
     * @author KOMIYA Atsushi
     */
    class ListMatcher extends InternalMatcherBase {
        private List<Object> expectedList;

        ListMatcher(List<Object> expectedList) {
            this.expectedList = expectedList;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean matches(Object object) {
            if (object == null) {
                setExpectedMessage("は List オブジェクト %s", expectedList);
                setMismatchMessage("は null です");
                fixObjectHierarchyForMessaging();
                return false;
            }

            if (!(object instanceof List)) {
                setExpectedMessage("は List オブジェクト %s", expectedList);
                setMismatchMessage("は List オブジェクトではありません (%s, %s)", object.getClass(), object);
                fixObjectHierarchyForMessaging();
                return false;
            }

            List<Object> list = (List) object;

            if (expectedList.size() != list.size()) {
                setExpectedMessage("はサイズ %d の List オブジェクト", expectedList.size());
                setMismatchMessage("のサイズは %d です", list.size());
                fixObjectHierarchyForMessaging();
                return false;
            }

            for (ListIterator expectedIterator = new ListIterator(expectedList),
                         actualIterator = new ListIterator(list);
                 expectedIterator.hasNext() && actualIterator.hasNext(); ) {
                Pair<String, Object> expectedValue = expectedIterator.next();
                Pair<String, Object> actualValue = actualIterator.next();

                if (!forwardMatching(
                        ObjectPath.Element.Type.LIST,
                        expectedValue, actualValue.second)) {
                    return false;
                }
            }

            return true;
        }
    }

    class ArrayMatcher extends InternalMatcherBase {
        private final Object expectedArray;
        private final Class expectedClass;

        ArrayMatcher(Object expectedArray) {
            this.expectedArray = expectedArray;
            this.expectedClass = expectedArray.getClass();
        }

        @Override
        public boolean matches(Object object) {
            if (object == null) {
                setExpectedMessage("は %s の配列", expectedClass.getComponentType().getSimpleName());
                setMismatchMessage("は null です");
                fixObjectHierarchyForMessaging();
                return false;
            }

            Class clazz = object.getClass();
            if (!clazz.isArray()) {
                setExpectedMessage("は %s の配列", expectedClass.getComponentType().getSimpleName());
                setMismatchMessage("は配列ではありません (%s, %s)", clazz, object);
                fixObjectHierarchyForMessaging();
                return false;
            }

            if (!expectedClass.getComponentType().equals(clazz.getComponentType())) {
                setExpectedMessage("は %s の配列", expectedClass.getComponentType().getSimpleName());
                setMismatchMessage("は %s の配列です", clazz.getComponentType());
                fixObjectHierarchyForMessaging();
                return false;
            }

            for (ArrayIterator expectedIterator = new ArrayIterator(expectedArray),
                         actualIterator = new ArrayIterator(object);
                 expectedIterator.hasNext() && actualIterator.hasNext(); ) {

                Pair<String, Object> expectedValue = expectedIterator.next();
                Pair<String, Object> actualValue = actualIterator.next();

                if (!forwardMatching(
                        ObjectPath.Element.Type.LIST,
                        expectedValue, actualValue.second)) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * 期待される値が {@link Map} オブジェクトの場合の InternalMatcher の実装です。
     *
     * @author KOMIYA Atsushi
     */
    class MapMatcher extends InternalMatcherBase {
        private final Map<Object, Object> expectedMap;

        MapMatcher(Map<Object, Object> expectedMap) {
            this.expectedMap = expectedMap;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean matches(Object object) {
            if (object == null) {
                setExpectedMessage("は Map オブジェクト %s", expectedMap);
                setMismatchMessage("は null です");
                fixObjectHierarchyForMessaging();
                return false;
            }

            if (!(object instanceof Map)) {
                setExpectedMessage("は Map オブジェクト %s", expectedMap);
                setMismatchMessage("は Map オブジェクトではありません (%s, %s)", object.getClass(), object);
                fixObjectHierarchyForMessaging();
                return false;
            }

            Map<Object, Object> map = (Map) object;

            if (expectedMap.size() != map.size()) {
                setExpectedMessage("はサイズ %s の Map オブジェクト", expectedMap.size());
                setMismatchMessage("のサイズは %d です", map.size());
                fixObjectHierarchyForMessaging();
                return false;
            }

            for (MapIterator i = new MapIterator(expectedMap); i.hasNext(); ) {
                Pair<String, Object> expectedValue = i.next();
                Object actual = map.get(expectedValue.first);

                if (!forwardMatching(ObjectPath.Element.Type.MAP, expectedValue, actual)) {
                    if (!map.containsKey(expectedValue.first)) {
                        setMismatchMessage("は存在しません");
                    }
                    return false;
                }
            }

            // TODO actual にあって一方にないものを探す

            return true;
        }
    }

    static abstract class IteratorBase implements Iterator<Pair<String, Object>> {
        Pair<String, Object> nextObject;

        @Override
        public Pair<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Pair<String, Object> result = nextObject;
            nextObject = null;

            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    class PropertyIterator extends IteratorBase {
        private final Object targetObject;

        private final Iterator<Map.Entry<String, PropertyFieldAccessor.Accessor>> accessors;

        PropertyIterator(Object targetObject) {
            this.targetObject = targetObject;
            this.accessors
                    = PropertyFieldAccessor.newInstance(targetObject.getClass())
                    .newIterable()
                    .iterator();
        }

        @Override
        public boolean hasNext() {
            if (nextObject != null) {
                return true;
            }

            while (accessors.hasNext()) {
                Map.Entry<String, PropertyFieldAccessor.Accessor> entry = accessors.next();
                String propertyName = entry.getKey();

                if (needVerification(ObjectPath.Element.Type.OBJECT, propertyName)) {
                    nextObject = new Pair<>(propertyName, entry.getValue().get(targetObject));
                    return true;
                }
            }

            return false;
        }
    }


    class ArrayIterator extends IteratorBase {
        private final int arrayLength;
        private final Object targetArray;

        private int index;

        ArrayIterator(Object targetArray) {
            this.arrayLength = Array.getLength(targetArray);
            this.targetArray = targetArray;
        }

        @Override
        public boolean hasNext() {
            if (nextObject != null) {
                return true;
            }

            while (index < arrayLength
                    && !needVerification(ObjectPath.Element.Type.LIST, String.valueOf(index))) {
                index++;
            }

            if (index >= arrayLength) {
                return false;
            }

            nextObject = new Pair<>(
                    String.valueOf(index),
                    Array.get(targetArray, index));
            index++;

            return true;
        }
    }

    class ListIterator extends IteratorBase {
        private final Iterator<Object> targetListIterator;
        private int index;

        ListIterator(List<Object> targetList) {
            this.targetListIterator = targetList.iterator();
        }

        @Override
        public boolean hasNext() {
            if (nextObject != null) {
                return true;
            }

            while (targetListIterator.hasNext()
                    && !needVerification(ObjectPath.Element.Type.LIST, String.valueOf(index))) {
                index++;
                targetListIterator.next();
            }

            if (!targetListIterator.hasNext()) {
                return false;
            }

            nextObject = new Pair<>(
                    String.valueOf(index),
                    targetListIterator.next());

            index++;

            return true;
        }
    }


    class MapIterator extends IteratorBase {
        private final Iterator<Map.Entry<Object, Object>> entryIterator;

        MapIterator(Map<Object, Object> targetMap) {
            this.entryIterator = targetMap.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            if (nextObject != null) {
                return true;
            }

            while (entryIterator.hasNext()) {
                Map.Entry<Object, Object> entry = entryIterator.next();
                String key = String.valueOf(entry.getKey());

                if (needVerification(ObjectPath.Element.Type.MAP, key)) {
                    nextObject = new Pair<>(key, entry.getValue());
                    return true;
                }
            }

            return false;
        }
    }

    static class Pair<T1, T2> {
        public final T1 first;
        public final T2 second;

        Pair(T1 first, T2 second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (!(obj instanceof Pair)) {
                return false;
            }

            Pair other = (Pair) obj;
            if (first == null) {
                if (other.first != null) {
                    return false;
                }
            } else {
                if (!first.equals(other.first)) {
                    return false;
                }
            }

            if (second == null) {
                return other.second == null;
            }

            return second.equals(other.second);
        }
    }
}
