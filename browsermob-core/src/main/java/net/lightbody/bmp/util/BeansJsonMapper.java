package net.lightbody.bmp.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BeansJsonMapper {
    static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(new ObjectMapper().getSerializationConfig().getDefaultVisibilityChecker()
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));

    private BeansJsonMapper() {
    }

    public static <T> T json2Bean(String json, Class classToInit) {
        try {
            return (T) objectMapper.readValue(json, classToInit);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to instantiate class: " + classToInit.getName());
        }
    }

    public static String getJsonString(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to serialize object to json: " + object.getClass().getName());
        }
    }
}
