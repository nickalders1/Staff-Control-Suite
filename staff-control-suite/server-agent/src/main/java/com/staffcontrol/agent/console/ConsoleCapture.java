package com.staffcontrol.agent.console;

import com.staffcontrol.agent.proxy.ProxyClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

/**
 * Captures console output by attaching a custom Log4j2 appender to the root logger.
 * Each log event is forwarded to the proxy via {@link ProxyClient#sendConsoleOutput(String)}.
 */
public class ConsoleCapture {

    private static final String APPENDER_NAME = "StaffControlCapture";

    private final ProxyClient proxyClient;
    private StaffControlAppender appender;

    public ConsoleCapture(ProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    /**
     * Attaches the custom appender to Log4j2's root logger.
     */
    public void startCapture() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss} %level]: %msg")
                .build();

        appender = new StaffControlAppender(APPENDER_NAME, null, layout, true,
                Property.EMPTY_ARRAY, proxyClient);
        appender.start();

        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, null, null);
        ctx.updateLoggers();
    }

    /**
     * Detaches the custom appender from Log4j2's root logger and stops it.
     */
    public void stopCapture() {
        if (appender != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            config.getRootLogger().removeAppender(APPENDER_NAME);
            appender.stop();
            ctx.updateLoggers();
            appender = null;
        }
    }

    // -------------------------------------------------------------------------
    // Inner appender class
    // -------------------------------------------------------------------------

    /**
     * A Log4j2 appender that forwards each log event's formatted message to the proxy.
     */
    static final class StaffControlAppender extends AbstractAppender {

        private final ProxyClient proxyClient;

        StaffControlAppender(String name,
                             org.apache.logging.log4j.core.Filter filter,
                             PatternLayout layout,
                             boolean ignoreExceptions,
                             Property[] properties,
                             ProxyClient proxyClient) {
            super(name, filter, layout, ignoreExceptions, properties);
            this.proxyClient = proxyClient;
        }

        @Override
        public void append(LogEvent event) {
            // Avoid potential infinite recursion: if the event originated from
            // ProxyClient itself, skip forwarding.
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.startsWith("com.staffcontrol.agent.proxy")) {
                return;
            }

            try {
                // Use the layout to produce the formatted string
                Serializable serializable = getLayout().toSerializable(event);
                String line = serializable.toString();
                // Trim trailing newline characters added by PatternLayout
                if (line.endsWith(System.lineSeparator())) {
                    line = line.substring(0, line.length() - System.lineSeparator().length());
                } else if (line.endsWith("\n")) {
                    line = line.substring(0, line.length() - 1);
                }
                proxyClient.sendConsoleOutput(line);
            } catch (Exception ignored) {
                // Never throw from an appender to avoid breaking the server's logging
            }
        }
    }
}
