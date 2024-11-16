package searchengine.services;

public enum IndexResultMessage {
    INDEXING_IS_COMPLETED("Индексация завершена"),
    INDEXING_IS_CANCELED("Индексация остановлена пользователем"),
    SITE_IS_UNAVAILABLE("Сайт недоступен"),
    SITE_HAS_NO_CONTENT("Сайт не содержит текстового контента");

    private final String indexResultMessage;

    IndexResultMessage(String indexResultMessage) {
        this.indexResultMessage = indexResultMessage;
    }

    @Override
    public String toString() {
        return indexResultMessage;
    }

}
