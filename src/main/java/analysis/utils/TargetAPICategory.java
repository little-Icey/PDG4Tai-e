package pascal.taie.analysis.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SensitiveCategory {
    private String categoryName;

    @JsonProperty("fine-grainedType")
    private List<FineGrainedType> fineGrainedType;

}
