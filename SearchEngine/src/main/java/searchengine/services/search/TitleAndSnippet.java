package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.services.ResultMessage;
import searchengine.services.morphology.MorphologyService;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class TitleAndSnippet {

    final int MAX_SNIPPET_LENGTH = 160;
    //  amount of word will be output before and after the lemma in the snippet
    final int WORDS_AMOUNT_BEFORE_AND_AFTER_LEMMA = 5;

    private final MorphologyService morphologyService;


    String getPageTitle(Document htmlDoc, Set<String> lemmas) {
        String title = htmlDoc.head().tagName("title").text();
        if ( title.isEmpty() ) { title =  ResultMessage.NO_TITLE.toString(); }
        else {
            String[] titleWords = highlightSearchWordsArray(title, lemmas);
            StringBuilder titleBuilder = new StringBuilder();
            for (String titleWord : titleWords) {
                titleBuilder.append(titleWord).append(" ");
            }
            titleBuilder.setLength(titleBuilder.length() - 1);
            title = titleBuilder.toString();
        }
        return title;
    }


    String getSnippetForPage(Document htmlDoc, Set<String> lemmas) {
        Element htmlBody = htmlDoc.body();
        String s = htmlBody.toString().
                replaceAll("<header[\\s\\S]+?</header>", "<header></header>").
                replaceAll("<footer[\\s\\S]+?</footer>", "<footer></footer>");
        Element htmlBodyWoHeadersAndFooters = Jsoup.parse(s);
        String snippet = getSnippetForElement(htmlBodyWoHeadersAndFooters, lemmas);
        if (snippet == null) { snippet = getSnippetForElement(htmlBody, lemmas); }
        return snippet;
    }


    private String getSnippetForElement(Element htmlBody, Set<String> lemmas) {
        Elements htmlElements = htmlBody.getAllElements();
        TreeMap<Integer, String> snippets = new TreeMap<>();

        htmlElements.forEach(element -> {
            String[] words = highlightSearchWordsArray(element.text(), lemmas);

            TreeMap<String, Set<String>> elementSnippet = getSnippetWithFoundLemmas(words);
            if (elementSnippet != null) {
                snippets.putIfAbsent( elementSnippet.firstEntry().getValue().size(), elementSnippet.firstKey() );
            }
        } );
        if (snippets.isEmpty()) { return null; }
        else { return snippets.lastEntry().getValue(); }
    }


    /**
     * @param words array of words from HTML-element, having words in bold tags (example: <b>word2</b>).
     * @return TreeMap containing single element with key - snippet string,
     * and value - Set of lemmas contained in the snippet.
     */
    private TreeMap<String, Set<String>> getSnippetWithFoundLemmas(String[] words) {
        StringBuilder snippetBuilder = new StringBuilder();
        Set<String> foundLemmas = new HashSet<>();
        int lastIdx = -1;

        for (int i = 0; i < words.length; i++) {
            if (snippetBuilder.length() >= MAX_SNIPPET_LENGTH) { break; }

            if (!words[i].contains("<b>")) { continue; }

            String word = words[i].substring(words[i].indexOf("<b>") + 3, words[i].indexOf("</b>")).toLowerCase();

            foundLemmas.add( morphologyService.getNormalForm(word) );

            if (i - lastIdx > WORDS_AMOUNT_BEFORE_AND_AFTER_LEMMA + 1) {
                if (lastIdx > -1) { snippetBuilder.append("... "); }
                lastIdx = i - WORDS_AMOUNT_BEFORE_AND_AFTER_LEMMA;
            }

            for (int j = lastIdx + 1; j <= i; j++) {
                snippetBuilder.append(words[j]).append(" ");
            }

            lastIdx = Math.min((i + WORDS_AMOUNT_BEFORE_AND_AFTER_LEMMA), words.length - 1);

            for (int j = i + 1; j <= lastIdx; j++) {
                if (snippetBuilder.length() > MAX_SNIPPET_LENGTH) { break; }
                snippetBuilder.append(words[j]).append(" ");
                if ( words[j].startsWith("<b>") ) {
                    words[j] = words[j].substring(3, words[j].indexOf("</b>")).toLowerCase();
                    foundLemmas.add( morphologyService.getNormalForm(words[j]) );
                    lastIdx = Math.min((j + WORDS_AMOUNT_BEFORE_AND_AFTER_LEMMA), words.length - 1);
                }
            }
            i = lastIdx;
        }

        if (foundLemmas.isEmpty()) { return null;}

        snippetBuilder.setLength(snippetBuilder.length() - 1);

        if ( ! (snippetBuilder.toString().endsWith(".") ||
                snippetBuilder.toString().endsWith("!") ||
                snippetBuilder.toString().endsWith("?")) ) {
            snippetBuilder.append("...");
        }

        TreeMap<String, Set<String>> resultMap = new TreeMap<>();
        resultMap.put(snippetBuilder.toString(), foundLemmas);
        return resultMap;
    }


    /**
     * @param text text from HTML-element.
     * @param lemmas set of lemmas of search words.
     * @return array of words from param 'text'. Search words in the array are surrounded by bold tags.
     */
    private String[] highlightSearchWordsArray(String text, Set<String> lemmas) {
        final String RUS_WORLD_PATTERN = "[А-Яа-я]+";
        Pattern pattern = Pattern.compile(RUS_WORLD_PATTERN);

        // В тексте может приводиться пример HTML-кода (например, на страницах о WEB-программировании),
        // поэтому убираем болд-теги из текста, чтобы они не влияли на выполнение программы
        text = text.replace("<b>", "").replace("</b>", "");

        String[] words = text.split("\\s");
        for (int i = 0; i < words.length; i++) {
            Matcher matcher = pattern.matcher(words[i]);
            if (matcher.find()) {
                String word = matcher.group().toLowerCase().replace('ё', 'е');
                String wordInNormalForm = morphologyService.getNormalForm(word);
                if ( lemmas.contains(wordInNormalForm) ) {
                    words[i] = words[i].replace(matcher.group(), "<b>".concat(matcher.group()).concat("</b>"));
                }
            }
        }
        return words;
    }

}
