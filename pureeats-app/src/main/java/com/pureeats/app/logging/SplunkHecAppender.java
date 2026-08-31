package com.pureeats.app.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Forwards log events to a Splunk HTTP Event Collector endpoint, in addition
 * to (not instead of) the console/file appenders configured alongside it.
 *
 * A no-op when {@code url}/{@code token} aren't set - see logback-spring.xml,
 * which wires those from the SPLUNK_HEC_URL/SPLUNK_HEC_TOKEN env vars and
 * leaves this appender harmlessly disabled when they're absent.
 */
public class SplunkHecAppender extends AppenderBase<ILoggingEvent> {

    private String url;
    private String token;
    private String index;
    private String sourcetype;
    private String source;
    private String host;
    private boolean insecureTls;

    private volatile boolean enabled;
    private HttpClient httpClient;
    private ThreadPoolExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setUrl(String url) {
        this.url = url;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public void setSourcetype(String sourcetype) {
        this.sourcetype = sourcetype;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setInsecureTls(boolean insecureTls) {
        this.insecureTls = insecureTls;
    }

    @Override
    public void start() {
        if (isBlank(url) || isBlank(token)) {
            addInfo("Splunk HEC url/token not configured - SplunkHecAppender staying disabled (no-op)");
            super.start();
            return;
        }
        try {
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
            if (insecureTls) {
                builder.sslContext(trustAllSslContext());
            }
            this.httpClient = builder.build();
            this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1000), runnable -> {
                Thread thread = new Thread(runnable, "splunk-hec-appender");
                thread.setDaemon(true);
                return thread;
            });
            this.enabled = true;
        } catch (Exception e) {
            addError("Failed to initialize Splunk HEC appender, staying disabled", e);
        }
        super.start();
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled) {
            return;
        }
        try {
            String payload = buildPayload(event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Splunk " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            executor.execute(() -> {
                try {
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception e) {
                    addWarn("Failed to send log event to Splunk HEC: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            addWarn("Splunk HEC send queue is full, dropping this event");
        } catch (Exception e) {
            addWarn("Failed to build/queue Splunk HEC event: " + e.getMessage());
        }
    }

    private String buildPayload(ILoggingEvent event) throws Exception {
        StringBuilder message = new StringBuilder(event.getFormattedMessage());
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            message.append('\n').append(ThrowableProxyUtil.asString(throwableProxy));
        }

        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("level", event.getLevel().toString());
        eventNode.put("logger", event.getLoggerName());
        eventNode.put("thread", event.getThreadName());
        eventNode.put("message", message.toString());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("time", event.getTimeStamp() / 1000.0);
        if (!isBlank(host)) {
            root.put("host", host);
        }
        if (!isBlank(source)) {
            root.put("source", source);
        }
        if (!isBlank(sourcetype)) {
            root.put("sourcetype", sourcetype);
        }
        if (!isBlank(index)) {
            root.put("index", index);
        }
        root.set("event", eventNode);

        return objectMapper.writeValueAsString(root);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static SSLContext trustAllSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        return sslContext;
    }
}
