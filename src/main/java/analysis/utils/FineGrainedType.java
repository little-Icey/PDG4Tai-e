package pascal.taie.analysis.blackcat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FineGrainedType {
    @JsonProperty("subcategoryName")
    private String subcategoryName;

    @JsonProperty("short")
    private String shortCode;

    @JsonProperty("apiNames")
    private List<String> apiNames;
}
