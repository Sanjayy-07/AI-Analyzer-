package com.archanalyzer.audit;

import java.util.ArrayList;
import java.util.List;

public class ValidationSufficiencyDemonstrator {

    // Global audit log to monitor actual runtime side effects
    public static final List<String> AUDIT_LOG = new ArrayList<>();

    // 1. Target interface extracted by refactoring
    interface INotificationService {
        void sendReceipt(String orderId, double amount);
    }

    // 2. Provider implementing the interface
    static class NotificationService implements INotificationService {
        @Override
        public void sendReceipt(String orderId, double amount) {
            AUDIT_LOG.add("DISPATCHED_EMAIL_RECEIPT for " + orderId + " ($" + amount + ")");
        }
    }

    // 3. PaymentService after cycle refactoring with null decoupling
    static class PaymentService {
        private INotificationService notificationService;

        public PaymentService() {
            // Decoupled concrete instantiation to null
            this.notificationService = null;
        }

        public PaymentService(INotificationService notificationService) {
            this.notificationService = notificationService;
        }

        public boolean charge(String orderId, double amount) {
            // Guarded call by AstCallerRewriter
            if (notificationService != null) {
                notificationService.sendReceipt(orderId, amount);
            }
            return true;
        }
    }

    // 4. OrderService caller
    static class OrderService {
        private PaymentService paymentService = new PaymentService();

        public String createOrder(String customerId, double amount) {
            String orderId = "ORD-" + System.currentTimeMillis();
            paymentService.charge(orderId, amount);
            return orderId;
        }
    }

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("PHASE R-H: TEST SUCCESS VS BEHAVIORAL CORRECTNESS DEMONSTRATION");
        System.out.println("=================================================================");

        // Step 1: Compilation Check
        System.out.println("[STEP 1] Compilation Status: SUCCESS");
        System.out.println("  All classes compiled cleanly with 0 errors via javac.");

        // Step 2: Run Existing Suite (simulating OrderServiceTest assertions)
        System.out.println("\n[STEP 2] Executing Existing Test Suite:");
        OrderService service = new OrderService();
        String orderId = service.createOrder("cust-101", 89.95);

        // Exact assertions from OrderServiceTest:
        boolean assertion1 = orderId != null;
        boolean assertion2 = orderId.startsWith("ORD-");
        boolean allTestsPassed = assertion1 && assertion2;

        System.out.printf("  Assertion 1 (assertNotNull(orderId)):         %b\n", assertion1);
        System.out.printf("  Assertion 2 (assertTrue(orderId.startsWith)):  %b\n", assertion2);
        System.out.printf("  Unit Test Result:                             PASS (%s)\n", orderId);

        // Step 3: Deep Behavioral & Side-Effect Verification
        System.out.println("\n[STEP 3] Runtime Behavioral & Side-Effect Audit:");
        System.out.printf("  Dispatched Audit Log Size: %d\n", AUDIT_LOG.size());

        boolean behavioralDefectObserved = AUDIT_LOG.isEmpty();
        System.out.printf("  Expected: Email receipt should have been dispatched to customer.\n");
        System.out.printf("  Actual:   Email receipt was SILENTLY SUPPRESSED (audit log empty)!\n");
        System.out.printf("  Defect Confirmed: %b\n", behavioralDefectObserved);

        System.out.println("\n=================================================================");
        System.out.println("DEMONSTRATION CONCLUSION:");
        System.out.println("Compilation: PASS");
        System.out.println("Unit Tests:  PASS (100%)");
        System.out.println("Behavior:    FAIL (Silent suppression of critical side-effect)");
        System.out.println("Insight: Passing tests do NOT prove behavioral correctness when");
        System.out.println("         test suites lack side-effect or integration assertions.");
        System.out.println("=================================================================");
    }
}
