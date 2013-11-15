package biz.k11i.shibuyajava;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 指定された文字列で利用されている各単語の頻度を計測する機能を提供します。
 *
 * @author KOMIYA Atsushi
 */
public class WordCount {
    private static final Pattern WHITESPACES = Pattern.compile("\\s+");

    /** 各単語の出現頻度を保持します */
    public Map<String, Integer> wordCounts;

    /** 最頻出する単語の上位３つを保持します */
    public List<String> top3Words;

    /** ワードカウントの処理に要した時間 (ミリ秒) を保持します */
    public long elapsedMillis;

    /** 元の文章を保持します */
    public String text;

    /** YAML からのデシリアライズでデフォルトコンストラクタが必要となります。 */
    public WordCount() {
    }

    private WordCount(long begin, String text, Map<String, Integer> wordCounts) {
        this.elapsedMillis = System.currentTimeMillis() - begin;
        this.text = text;
        this.wordCounts = wordCounts;

        List<Map.Entry<String, Integer>> wordCountEntries = new ArrayList<>(wordCounts.entrySet());
        Collections.sort(wordCountEntries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                int count1 = o1.getValue();
                int count2 = o2.getValue();

                if (count1 > count2) {
                    return -1;

                } else if (count1 < count2) {
                    return 1;

                } else {
                    return o1.getKey().compareTo(o2.getKey());
                }
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }
        });

        top3Words = new ArrayList<>();
        for (int i = 0; i < 3 && i < wordCountEntries.size(); i++) {
            top3Words.add(wordCountEntries.get(i).getKey());
        }
    }

    /**
     * 指定された文章をホワイトスペースでトークナイズし、小文字に揃えた状態で
     * 各単語の出現頻度を計測（ワードカウント）します。
     *
     * @param text ワードカウント対象の文章
     * @return ワードカウント結果が含まれている WordCount オブジェクト
     */
    public static WordCount countWords(String text) {
        long begin = System.currentTimeMillis();

        String[] words = WHITESPACES.split(text);
        Map<String, Integer> wordCounts = new HashMap<>();

        for (String word : words) {
            word = word.toLowerCase();

            Integer count = wordCounts.get(word);
            if (count == null) {
                wordCounts.put(word, 1);

            } else {
                wordCounts.put(word, count + 1);
            }
        }

        return new WordCount(begin, text, wordCounts);
    }
}
