package biz.k11i.shibuyajava;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import static biz.k11i.matcher.IsEquivalentTo.isEquivalentTo;
import static biz.k11i.matcher.IsEquivalentTo.pathPattern;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * WordCount クラスに対するテストケースです。
 *
 * @author KOMIYA Atsushi
 */
@RunWith(Enclosed.class)
public class WordCountTest {
    private static final Yaml YAML = new Yaml();

    /**
     * 期待される結果のオブジェクトを、YAML シリアライズされているファイルから読み込み返却します。
     *
     * @param clazz     オブジェクトの型を表す Class オブジェクト
     * @param _filename YAML ファイルの名前
     * @param <T>       オブジェクトの型
     * @return YAML からデシリアライズされたオブジェクト
     */
    static <T> T loadFromYaml(Class<T> clazz, String _filename) {
        URL url = WordCountTest.class.getResource(_filename);
        if (url == null) {
            throw new RuntimeException(_filename + " は存在しません。");
        }

        String filename = url.getPath();
        try (FileInputStream stream = new FileInputStream(filename)) {
            return YAML.loadAs(stream, clazz);

        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    // -----

    public static class WordCountオブジェクトのアサーションは本来はこう書くべきなのかも {
        private static WordCount wordCount;

        @BeforeClass
        public static void setUpClass() {
            // exercise
            wordCount = WordCount.countWords("Hello world WORLD");
        }

        @Test
        public void wordCountsのMapオブジェクトがhelloが1でworldが2であること() {
            assertThat(wordCount.wordCounts, is(notNullValue()));
            assertThat(wordCount.wordCounts.keySet(), hasSize(2));
            assertThat(wordCount.wordCounts, hasEntry("hello", 1));
            assertThat(wordCount.wordCounts, hasEntry("world", 2));
        }

        @Test
        public void top3WordsのListオブジェクトがworldとhelloの順であること() {
            assertThat(wordCount.top3Words, is(notNullValue()));
            assertThat(wordCount.top3Words, hasItems("world", "hello"));
        }

        @Test
        public void textはHello_world_WORLDであること() {
            assertThat(wordCount.text, is("Hello world WORLD"));
        }
    }

    // -----

    public static class アサーションに成功するケース {
        private static WordCount wordCount;

        @BeforeClass
        public static void setUpClass() {
            // exercise
            wordCount = WordCount.countWords("Hello world WORLD");
        }

        @Test
        public void _1_org_hamcrest_Matchersクラスで提供されるMatcherのみで頑張ってみる() {
            // verify

            // elapsedMillis は等価比較しない

            // wordCounts の内容は {'hello': 1, 'world': 2} だよね？
            assertThat(wordCount.wordCounts, is(notNullValue()));
            assertThat(wordCount.wordCounts.keySet(), hasSize(2));
            assertThat(wordCount.wordCounts, hasEntry("hello", 1));
            assertThat(wordCount.wordCounts, hasEntry("world", 2));

            // top3Words の内容は ['world', 'hello'] だよね？
            assertThat(wordCount.top3Words, is(notNullValue()));
            assertThat(wordCount.top3Words, hasItems("world", "hello"));

            // text に "Hello world WORLD" が設定されてるよね？
            assertThat(wordCount.text, is("Hello world WORLD"));
        }

        @Test
        public void _2_EqualsBuilderを利用して楽をする() {
            // setup
            WordCount expected = loadFromYaml(WordCount.class, "WordCount_HelloWorld.yml");

            class Wrap {
                // commons-lang の EqualsBuilder を使いたいので、
                // 入れ物となるクラスを用意しています。

                public WordCount wordCount;

                Wrap(WordCount wordCount) {
                    this.wordCount = wordCount;
                }

                @Override
                public boolean equals(Object obj) {
                    if (!(obj instanceof Wrap)) {
                        return false;
                    }

                    Wrap other = (Wrap) obj;
                    return new EqualsBuilder()
                            // elapsedMillis は等価比較しない
                            .append(this.wordCount.wordCounts, other.wordCount.wordCounts)
                            .append(this.wordCount.top3Words, other.wordCount.top3Words)
                            .append(this.wordCount.text, other.wordCount.text)
                            .isEquals();
                }
            }

            // verify
            assertThat(
                    new Wrap(wordCount),
                    is(new Wrap(expected)));
        }

        @Test
        public void _3_もっともっとスマートに等価比較したい() {
            // setup
            WordCount expected = loadFromYaml(WordCount.class, "WordCount_HelloWorld.yml");

            // verify
            assertThat(wordCount,
                    isEquivalentTo(expected)
                            .exclude(
                                    // elapsedMillis は等価比較しない
                                    pathPattern("object.elapsedMillis")));
        }
    }

    // -----

    public static class アサーションに失敗したときのメッセージを確認してみよう {
        private static WordCount wordCount;

        @BeforeClass
        public static void setUpClass() {
            // exercise
            wordCount = WordCount.countWords("Hello world");
        }

        @Test
        public void _1_org_hamcrest_Matchersクラスで提供されるMatcherのみで頑張ってみる() {
            // verify

            // elapsedMillis は等価比較しない

            // そもそも null じゃないよね？
            assertThat(wordCount, is(notNullValue()));

            // text に "Hello world WORLD" が設定されてるよね？
            assertThat(wordCount.text, is("Hello world WORLD"));

            // wordCounts の内容は {'hello': 1, 'world': 2} だよね？
            assertThat(wordCount.wordCounts, is(notNullValue()));
            assertThat(wordCount.wordCounts.keySet(), hasSize(2));
            assertThat(wordCount.wordCounts, hasEntry("hello", 1));
            assertThat(wordCount.wordCounts, hasEntry("world", 2));

            // top3Words の内容は ['world', 'hello'] だよね？
            assertThat(wordCount.top3Words, is(notNullValue()));
            assertThat(wordCount.top3Words, hasItems("world", "hello"));
        }

        @Test
        public void _2_EqualsBuilderを利用して楽をする() {
            // setup
            WordCount expected = loadFromYaml(WordCount.class, "WordCount_HelloWorld.yml");

            class Wrap {
                // commons-lang の EqualsBuilder を使いたいので、
                // 入れ物となるクラスを用意しています。

                public WordCount wordCount;

                Wrap(WordCount wordCount) {
                    this.wordCount = wordCount;
                }

                @Override
                public boolean equals(Object obj) {
                    if (!(obj instanceof Wrap)) {
                        return false;
                    }

                    Wrap other = (Wrap) obj;
                    return new EqualsBuilder()
                            // elapsedMillis は等価比較しない
                            .append(this.wordCount.text, other.wordCount.text)
                            .append(this.wordCount.wordCounts, other.wordCount.wordCounts)
                            .append(this.wordCount.top3Words, other.wordCount.top3Words)
                            .isEquals();
                }
            }

            // verify
            assertThat(
                    new Wrap(wordCount),
                    is(new Wrap(expected)));
        }

        @Test
        public void _3_もっともっとスマートに等価比較したい() {
            // setup
            WordCount expected = loadFromYaml(WordCount.class, "WordCount_HelloWorld.yml");

            // verify
            assertThat(wordCount,
                    isEquivalentTo(expected)
                            .exclude(
                                    // elapsedMillis は等価比較しない
                                    pathPattern("object.elapsedMillis")));
        }
    }

}
