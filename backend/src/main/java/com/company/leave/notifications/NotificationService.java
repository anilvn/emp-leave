package com.company.leave.notifications;

import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRenderer templateRenderer;
    private final TransactionTemplate notificationTransaction;

    NotificationService(
            NotificationRepository notificationRepository,
            NotificationTemplateRenderer templateRenderer,
            PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.templateRenderer = templateRenderer;
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void sendNotification(NotificationMessage message) {
        try {
            RenderedNotification rendered = templateRenderer.render(message);
            Instant sentAt = Instant.now();
            notificationTransaction.executeWithoutResult(status ->
                    notificationRepository.save(Notification.builder()
                            .userId(message.userId())
                            .requestId(message.requestId())
                            .kind(message.kind())
                            .subject(rendered.subject())
                            .sentAt(sentAt)
                            .build()));
            log.info("Notification for user {}: {} | {}",
                    message.userId(), rendered.subject(), rendered.body().replaceAll("\\R", " "));
        } catch (Exception exception) {
            log.error("Unable to record notification", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(long userId) {
        return notificationRepository.findAllByUserIdOrderBySentAtDescIdDesc(userId).stream()
                .map(notification -> new NotificationResponse(
                        notification.getId(),
                        notification.getUserId(),
                        notification.getRequestId(),
                        notification.getKind(),
                        notification.getSubject(),
                        notification.getSentAt()))
                .toList();
    }
}
