package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {

    private boolean result;
    private Integer count;
    private SearchData[] data;
    private String error;
    @JsonIgnore
    private HttpStatus httpStatus;

}
