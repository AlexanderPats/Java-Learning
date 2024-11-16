package searchengine.dto.indexing;

public enum ErrMessage {
    INDEXING_ALREADY_STARTED("Индексация уже запущена"),
    INDEXING_NOT_STARTED("Индексация не запущена"),
    PAGE_IS_OUTSIDE_THE_SITES("Данная страница находится за пределами сайтов,указанных в конфигурационном файле"),
    EMPTY_REQUEST("Задан пустой поисковый запрос"),
    PAGE_NOT_FOUND("Указанная страница не найдена");

    private final String errMessage;

    ErrMessage(String errMessage) {
        this.errMessage = errMessage;
    }

    @Override
    public String toString() {
        return errMessage;
    }

}
