package searchengine.services.morphology;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MorphologyServiceImpl implements MorphologyService {

    private static final String[] STOP_WORDS_TYPES = {"МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ"};
    private static final String REGEX_RUS_WORDS = "[а-я]+";

    private final LuceneMorphology luceneMorph = getRusMorphology();

    @Override
    public Map<String, Integer> getMapOfLemmasMentions(String text) {
        List<String> lemmas = getLemmasFromText(text);
        HashMap<String, Integer> resultMap = new HashMap<>();
        lemmas.forEach( lemma -> resultMap.put(lemma, resultMap.getOrDefault(lemma, 0) + 1) );
        return resultMap;
    }

    @Override
    public List<String> getLemmasFromText(String text) {
        text = text.replace('ё', 'е');
        Pattern pattern = Pattern.compile(REGEX_RUS_WORDS);
        Matcher matcher = pattern.matcher(text.toLowerCase());
        List<String> resultList = new ArrayList<>();
        while (matcher.find()) {
            String currentWord = matcher.group();
            if (currentWord.length() == 1 && !currentWord.equals("я")) { continue; } // т.к. лемматизатор неадекватно воспринимает некоторые одиночные буквы
            AtomicBoolean isStopWord = new AtomicBoolean(false);
            List<String> wordMorphInfo = luceneMorph.getMorphInfo(currentWord);
            wordMorphInfo.forEach(wordInfo -> {
                if (Arrays.stream(STOP_WORDS_TYPES).anyMatch(wordInfo::contains)) { isStopWord.set(true); }
            });
            if (!isStopWord.get()) {
                resultList.add(luceneMorph.getNormalForms(currentWord).get(0));
//                resultList.addAll(luceneMorph.getNormalForms(currentWord));
            }
        }
        return resultList;
    }

    @Override
    public Set<String> getUniqueLemmasFromText(String text) {
        List<String> lemmas = getLemmasFromText(text);
        return new HashSet<>(lemmas);
    }

    @Override
    public String getNormalForm(String word) {
        if ( word.matches(REGEX_RUS_WORDS) ) { return luceneMorph.getNormalForms(word).get(0); }
        else { return null; }
    }

    private LuceneMorphology getRusMorphology() {
        LuceneMorphology luceneMorph = null;
        try { luceneMorph = new RussianLuceneMorphology(); }
        catch (IOException e) {
            log.error("Не удалось инициализировать сервис морфологии: {}", e.toString());
        }
        return luceneMorph;
    }

}
