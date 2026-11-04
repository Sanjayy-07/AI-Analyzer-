package com.demo.shop.service;

public class PaymentService {

    private INotificationService notificationService;

    public PaymentService() {
        this.notificationService = null;
    }

    public boolean charge(String customerId, String productId, int quantity) {
        boolean success = Math.random() > 0.05; // simulated gateway call
        if (success) {
            if (notificationService != null) {
            notificationService.sendPaymentConfirmation(customerId, productId, quantity);
        }
        }
        return success;
    }

    public void refund(String orderId) {
        if (notificationService != null) {
            notificationService.sendRefundNotice(orderId);
        }
    }


    public PaymentService(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setNotificationService(INotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
