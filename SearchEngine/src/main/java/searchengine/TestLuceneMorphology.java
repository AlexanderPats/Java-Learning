package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class TestLuceneMorphology {

    public static void main(String[] args) throws IOException {

        String word = "е";
        LuceneMorphology luceneMorph = getRusMorphology();
//        long start = System.nanoTime();
        List<String> wordMorphInfo = luceneMorph.getMorphInfo(word);
        List<String> wordBaseForms = luceneMorph.getNormalForms(word);
        wordMorphInfo.forEach(System.out::println);
        wordBaseForms.forEach(System.out::println);
//        String s = "abc";
//        StringBuilder sb = new StringBuilder(s);
//        for (int i = 0; i < 15; i++) {
//            s = s + Integer.toString(i);
//            s = s + "cde";
//            s = s.concat(Integer.toString(i));
//            s = s.concat("cde");
//            sb.append("cde");
//        }
//        System.out.println(s);
//        System.out.println(" Затрачено время: " + (System.nanoTime() - start) / 1000 + " мкс");
    }

    private static LuceneMorphology getRusMorphology() {
        LuceneMorphology luceneMorph = null;
        try { luceneMorph = new RussianLuceneMorphology(); }
        catch (IOException e) { e.printStackTrace();}
        return luceneMorph;
    }

}
