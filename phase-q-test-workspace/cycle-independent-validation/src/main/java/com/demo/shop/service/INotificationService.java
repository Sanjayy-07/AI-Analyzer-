package com.demo.shop.service;

/**
 * Auto-generated interface for dependency inversion refactoring.
 * Defines the extracted public contract of NotificationService.
 */
public interface INotificationService {

    void sendPaymentConfirmation(String customerId, String productId, int quantity);

    void sendRefundNotice(String orderId);

}
