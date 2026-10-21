package vn.edu.hcmute.drss.realtime;

public interface RealtimeNotificationGateway {
    void send(NotificationDelivery delivery);
}