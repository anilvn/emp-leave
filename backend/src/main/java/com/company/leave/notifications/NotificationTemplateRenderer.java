package com.company.leave.notifications;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
class NotificationTemplateRenderer {

    private final ResourceLoader resourceLoader;

    NotificationTemplateRenderer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    RenderedNotification render(NotificationMessage message) {
        Map<String, String> values = Map.of(
                "status", message.status(),
                "leaveType", message.leaveType(),
                "startDate", message.startDate().toString(),
                "endDate", message.endDate().toString(),
                "workingDays", Integer.toString(message.workingDays()),
                "requestLink", "/requests/" + message.requestId());
        return new RenderedNotification(
                apply(readTemplate("leave-notification-subject.txt"), values),
                apply(readTemplate("leave-notification-body.txt"), values));
    }

    private String readTemplate(String name) {
        try (var stream = resourceLoader.getResource("classpath:templates/email/" + name).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read notification template " + name, exception);
        }
    }

    private String apply(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            rendered = rendered.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        if (rendered.contains("{{")) {
            throw new IllegalStateException("Notification template contains an unresolved placeholder");
        }
        return rendered;
    }
}
