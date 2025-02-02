package searchengine.services;

public enum ResultMessage {
    EMPTY_SITES_LIST("Задан пустой список сайтов в конфигурационном файле"),
    INDEXING_ALREADY_STARTED("Индексация уже запущена"),
    INDEXING_NOT_STARTED("Индексация не запущена"),
    INDEXING_IS_COMPLETED("Индексация успешно завершена"),
    INDEXING_IS_CANCELED("Индексация остановлена пользователем"),
    PAGE_IS_OUTSIDE_THE_SITES("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"),
    EMPTY_REQUEST("Задан пустой запрос"),
    EMPTY_SEARCH_REQUEST("Задан пустой поисковый запрос"),
    PAGE_NOT_FOUND("Указанная страница не найдена"),
    PAGE_IS_CHECKED("Страница проверена"),
    NO_PROTOCOL("Не указан протокол"),
    URL_HAS_NO_TEXT_CONTENT("URL не содержит текстового контента"),
    URL_TOO_LONG("Указан слишком длинный URL"),
    SITE_IS_UNAVAILABLE("Сайт недоступен"),
    SITE_IS_NOT_INDEXED("Сайт не проиндексирован"),
    NO_TITLE("Заголовок отсутствует"),
    OFFSET_TOO_LARGE("Параметр 'offset' превышает количество найденных элементов"),
    RUS_WORDS_ARE_REQUIRED("Запрос должен содержать по крайней мере одно слово русского языка, " +
            "исключая предлоги, союзы, междометия и частицы"),
    SERVICE_WAS_STOPPED_BY_USER("Работа сервиса прекращена пользователем");

    private final String indexResultMessage;

    ResultMessage(String indexResultMessage) {
        this.indexResultMessage = indexResultMessage;
    }

    public static ResultMessage getByValue(String value) {
        for ( ResultMessage message : ResultMessage.values() ) {
            if ( message.toString().equals(value) ) { return message; }
        }
        return null;
    }

    @Override
    public String toString() {
        return indexResultMessage;
    }

}
