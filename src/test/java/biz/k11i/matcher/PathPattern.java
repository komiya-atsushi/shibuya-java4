package biz.k11i.matcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * オブジェクト階層構造をパスとみなしたときのパターンを表します。
 *
 * @author KOMIYA Atsushi
 */
public class PathPattern {
    static class Element {
        enum Type {
            PROPERTY_VALUE {
                final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z$_][a-zA-Z0-9$_]*");

                @Override
                String stringifyWith(String name) {
                    return ".";
                }

                @Override
                Element parse(String pattern, int beginIndex) {
                    Matcher matcher = NAME_PATTERN.matcher(pattern.substring(beginIndex + 1));

                    if (!matcher.find()) {
                        // TODO エラーメッセージ
                        throw new IllegalArgumentException("");
                    }

                    return new Element(this, matcher.group());
                }

                @Override
                int lengthOf(String name) {
                    return 1 + name.length();
                }
            },

            INDEXED_VALUE {
                final Pattern NAME_PATTERN = Pattern.compile("^\\[([1-9][0-9]*|'[^']*')\\]");

                @Override
                String stringifyWith(String name) {
                    return "[" + name + "]";
                }

                @Override
                Element parse(String pattern, int beginIndex) {
                    Matcher matcher = NAME_PATTERN.matcher(pattern.substring(beginIndex));

                    if (!matcher.find()) {
                        // TODO メッセージ
                        throw new IllegalArgumentException("");
                    }

                    String name = matcher.group(1);

                    return new Element(this, name);
                }

                @Override
                int lengthOf(String name) {
                    return name.length() + 2;
                }
            };

            abstract String stringifyWith(String name);

            abstract Element parse(String pattern, int beginIndex);

            abstract int lengthOf(String name);
        }

        private static final String WILDCARD = "*";

        private final Type type;
        private final String name;

        private final String dequotedName;

        Element(Type type, String name) {
            this.type = type;
            this.name = name;

            if (name.startsWith("'") && name.endsWith("'")) {
                dequotedName = name.substring(1, name.length() - 1);

            } else {
                dequotedName = name;
            }
        }

        boolean isAcceptable(ObjectPath.Element objectPathElem) {
            switch (type) {
                case PROPERTY_VALUE:
                    if (objectPathElem.type != ObjectPath.Element.Type.OBJECT) {
                        return false;
                    }
                    break;

                case INDEXED_VALUE:
                    if (objectPathElem.type != ObjectPath.Element.Type.LIST
                            && objectPathElem.type != ObjectPath.Element.Type.MAP) {
                        return false;
                    }
                    break;
            }

            if (WILDCARD.equals(name)) {
                // ワイルドカードの場合は、型が合致していればそれで OK という扱いにしている
                return true;
            }

            return dequotedName.equals(objectPathElem.name);
        }

        int length() {
            return type.lengthOf(name);
        }

        @Override
        public String toString() {
            return type.stringifyWith(name);
        }
    }

    private final List<Element> pathElements;

    public PathPattern(List<Element> pathElements) {
        this.pathElements = pathElements;
    }

    public boolean isAcceptable(ObjectPath objectPath) {
        if (this.pathElements.size() != objectPath.pathElements().size()) {
            return false;
        }

        Iterator<Element> thisIterator = pathElements.iterator();
        Iterator<ObjectPath.Element> objectPathIterator = objectPath.pathElements().iterator();

        while (thisIterator.hasNext() && objectPathIterator.hasNext()) {
            Element pathElem = thisIterator.next();
            ObjectPath.Element objectPathElem = objectPathIterator.next();

            if (!pathElem.isAcceptable(objectPathElem)) {
                return false;
            }
        }

        return true;
    }

    public static PathPattern compile(String pattern) {
        // 最初のトークンは無視する
        int dotIndex = pattern.indexOf('.');
        int leftBracketIndex = pattern.indexOf('[');
        int beginIndex = dotIndex;
        if (beginIndex < 0) {
            beginIndex = leftBracketIndex;
        }

        if (beginIndex < 0) {
            // TODO エラーメッセージ
            throw new IllegalArgumentException();
        }

        List<Element> patternElements = new ArrayList<>();
        for (int i = beginIndex;
             i < pattern.length(); ) {
            char ch = pattern.charAt(i);

            Element elem;
            switch (ch) {
                case '.':
                    elem = Element.Type.PROPERTY_VALUE.parse(pattern, i);
                    break;

                case '[':
                    elem = Element.Type.INDEXED_VALUE.parse(pattern, i);
                    break;

                default:
                    // TODO 何がまずいのかが分かるようにしよう
                    throw new IllegalArgumentException();
            }

            patternElements.add(elem);
            i += elem.length();
        }

        return new PathPattern(patternElements);
    }
}
