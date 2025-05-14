package pascal.taie.analysis.utils;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SensAPIHandler {

    private final static String PATH = "src/main/resources/methods.txt";

    private final static String JSONPATH = "src/main/resources/methods.json";

    @Getter
    private Set<String> sensitiveMethods = Sets.newSet();

    @Getter
    private List<SensitiveCategory> readFromJSON = new ArrayList<>();

    private Map<String, Map<String, String>> apiDetailsMap = Maps.newConcurrentMap();

    public SensAPIHandler() {
        readJSON();
        initial();
    }

    private void readJSON() {
        try {
            File file = new File(JSONPATH);
            ObjectMapper mapper = new ObjectMapper();
            readFromJSON = mapper.readValue(file, new TypeReference<List<SensitiveCategory>>() {});
        } catch (IOException e) {
            System.out.println("load json error, check!");
            throw new RuntimeException(e);
        }
    }

    private void initial() {
        for (SensitiveCategory category : readFromJSON) {
            for (FineGrainedType type : category.getFineGrainedType()) {
                List<String> apiNames = type.getApiNames();
                sensitiveMethods.addAll(apiNames);
                apiNames.stream().forEach(name -> {
                    Map<String, String> details = Maps.newConcurrentMap();
                    details.put("categoryName", category.getCategoryName());
                    details.put("shortTitle", type.getShortCode());
                    details.put("subcategoryName", type.getSubcategoryName());
                    apiDetailsMap.put(name, details);
                });
            }
        }
    }

    public MethodRef getMethodRef(Invoke invoke) {
        InvokeExp exp = invoke.getInvokeExp();
        MethodRef ref = exp instanceof InvokeDynamic ?
                ((InvokeDynamic) exp).getBootstrapMethodRef():
                exp.getMethodRef();
        return ref;
    }

    /**
     * 查询API的行为大类
     * @param signature
     */
    public String getAPICategory(String signature) {
        return apiDetailsMap.get(signature).get("categoryName");
    }

    /**
     * 查询API的细分类型
     * @param signature
     */
    public String getAPIType(String signature) {
        return apiDetailsMap.get(signature).get("subcategoryName");
    }

    public String getAPIShortCode(String signature) {
        return apiDetailsMap.get(signature).get("shortTitle");
    }

    public boolean matchInvokeWithSignature(Invoke invoke, String signature) {
        return getMethodRef(invoke).toString().equals(signature);
    }

    public boolean isSensitive(Invoke invoke) {
        return sensitiveMethods.contains(getMethodRef(invoke).toString());
    }



}
