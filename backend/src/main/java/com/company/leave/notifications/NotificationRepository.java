package com.company.leave.notifications;

import java.util.List;

import org.springframework.data.repository.Repository;

interface NotificationRepository extends Repository<Notification, Long> {

    Notification save(Notification notification);

    List<Notification> findAllByUserIdOrderBySentAtDescIdDesc(long userId);
}
