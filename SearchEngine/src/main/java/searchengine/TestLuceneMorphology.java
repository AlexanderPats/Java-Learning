package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class TestLuceneMorphology {

    public static void main(String[] args) throws IOException {
        String word = "кола";
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();
        List<String> wordMorphInfo = luceneMorph.getMorphInfo(word);
        List<String> wordBaseForms = luceneMorph.getNormalForms(word);
        System.out.println(word);
        wordMorphInfo.forEach(System.out::println);
        wordBaseForms.forEach(System.out::println);

    }

}
