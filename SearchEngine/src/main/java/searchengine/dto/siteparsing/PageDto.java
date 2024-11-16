package searchengine.dto.siteparsing;

import lombok.Data;

@Data
public class PageDto {
    private String site;
    private String path;
    private Integer code;
    private String content;
}
