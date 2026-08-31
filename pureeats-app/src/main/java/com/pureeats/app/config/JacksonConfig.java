package com.pureeats.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    /**
     * This app's Jackson autoconfiguration targets the new Jackson 3 (tools.jackson) mapper, not
     * the classic com.fasterxml.jackson.databind.ObjectMapper - so anything still wired to that
     * classic type (e.g. SecurityConfig, for its error-response bodies) needs its own bean here,
     * with JavaTimeModule registered so java.time types like Instant serialize correctly.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
