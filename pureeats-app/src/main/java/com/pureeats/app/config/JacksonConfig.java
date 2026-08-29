package com.pureeats.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Request DTOs are Java records with primitive {@code boolean}/{@code int} fields for optional
 * flags (e.g. {@code ItemRequest.isRecommended}); Jackson's record deserializer otherwise throws
 * when such a field is simply omitted from the JSON body instead of defaulting to false/0.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer primitiveDefaultsCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
