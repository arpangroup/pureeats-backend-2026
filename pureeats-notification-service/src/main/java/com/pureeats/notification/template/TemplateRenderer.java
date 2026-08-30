package com.pureeats.notification.template;

import java.util.Map;

/**
 * Resolves {@code {{param}}} placeholders inside a template string. Deliberately narrow so a
 * heavier engine (Thymeleaf/Freemarker) can implement this same interface later without any
 * caller change.
 */
public interface TemplateRenderer {
    String render(String template, Map<String, Object> params);
}
