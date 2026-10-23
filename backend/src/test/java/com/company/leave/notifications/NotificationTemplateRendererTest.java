package com.company.leave.notifications;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer =
            new NotificationTemplateRenderer(new DefaultResourceLoader());

    @Test
    void rendersReusableSubjectAndBodyTemplates() {
        RenderedNotification rendered = renderer.render(new NotificationMessage(
                3,
                101,
                NotificationKind.SUBMITTED,
                "Vacation",
                LocalDate.of(2026, 10, 29),
                LocalDate.of(2026, 11, 3),
                4,
                "PENDING"));

        assertThat(rendered.subject()).contains("PENDING", "Vacation", "2026-10-29", "2026-11-03");
        assertThat(rendered.body())
                .contains("Vacation", "2026-10-29", "2026-11-03", "Working days: 4", "Status: PENDING",
                        "/requests/101");
    }
}
