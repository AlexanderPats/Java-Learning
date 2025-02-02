package searchengine.services.morphology;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface MorphologyService {

    Map<String, Integer> getMapOfLemmasMentions(String text);
    List<String> getLemmasFromText(String text);
    Set<String> getUniqueLemmasFromText(String text);
    String getNormalForm(String word);

}
