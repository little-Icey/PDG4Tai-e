package pascal.taie.analysis.utils;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SensitiveAPIInfo {
    private String apiName;
    private String categoryName;
    private String shortTitle;
    private String subcategoryName;

    public SensitiveAPIInfo(String apiName, String categoryName, String shortTitle, String subcategoryName) {
        this.apiName = apiName;
        this.categoryName = categoryName;
        this.shortTitle = shortTitle;
        this.subcategoryName = subcategoryName;
    }
}
