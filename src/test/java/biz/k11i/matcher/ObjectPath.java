package biz.k11i.matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * オブジェクト階層を表現します。
 *
 * @author KOMIYA Atsushi
 */
public class ObjectPath {
    static class Element {
        enum Type {
            OBJECT {
                @Override
                String stringifyWith(String name) {
                    return "." + name;
                }
            },

            LIST {
                @Override
                String stringifyWith(String name) {
                    return "[" + name + "]";
                }
            },

            MAP {
                @Override
                String stringifyWith(String name) {
                    return "['" + name + "']";
                }
            };

            public Element newElement(String name) {
                return new Element(this, name);
            }

            abstract String stringifyWith(String name);
        }

        final Type type;
        final String name;

        private Element(Type type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return type.stringifyWith(name);
        }
    }

    private List<Element> pathElements;

    ObjectPath() {
        this(new ArrayList<Element>());
    }

    ObjectPath(List<Element> pathElements) {
        this.pathElements = pathElements;
    }

    List<Element> pathElements() {
        return pathElements;
    }

    public void push(Element element) {
        pathElements.add(element);
    }

    public Element pop() {
        int lastIndex = pathElements.size() - 1;
        if (lastIndex < 0) {
            throw new IllegalStateException("パス要素がない状態での pop() はできません。");
        }
        return pathElements.remove(lastIndex);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (Element element : pathElements) {
            sb.append(element);
        }

        return sb.toString();
    }

}
