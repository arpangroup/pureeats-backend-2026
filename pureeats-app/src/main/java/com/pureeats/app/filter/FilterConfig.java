package com.pureeats.app.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Must run before Spring Security's filter chain so the request id is available for
        // every downstream filter/handler, including auth failures.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
