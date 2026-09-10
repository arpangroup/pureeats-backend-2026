package com.pureeats.notification.dto;

/**
 * One button on a web push notification (Web Notifications API "actions") - {@code action} is the
 * id the service worker's {@code notificationclick} handler switches on, {@code title} is the
 * button label, {@code icon} an optional small icon URL. Chrome/Edge show at most 2; other browsers
 * (Firefox, Safari) ignore actions entirely and just render the plain notification.
 */
public record FcmAction(String action, String title, String icon) {
}
