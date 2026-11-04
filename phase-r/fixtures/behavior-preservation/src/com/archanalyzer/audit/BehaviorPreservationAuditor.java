package com.archanalyzer.audit;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public class BehaviorPreservationAuditor {

    public static class TestResult {
        public String scenarioName;
        public boolean passed;
        public String originalOutput;
        public String refactoredOutput;
        public String notes;

        public TestResult(String scenarioName, boolean passed, String originalOutput, String refactoredOutput, String notes) {
            this.scenarioName = scenarioName;
            this.passed = passed;
            this.originalOutput = originalOutput;
            this.refactoredOutput = refactoredOutput;
            this.notes = notes;
        }
    }

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("PHASE R-B: BEHAVIOR PRESERVATION DIFFERENTIAL AUDIT");
        System.out.println("=================================================================");

        List<TestResult> results = new ArrayList<>();

        results.add(auditScenario1_BasicDependencyInversion());
        results.add(auditScenario2_ConstructorDependencyPreservation());
        results.add(auditScenario3_InterfaceMethodSignaturePreservation());
        results.add(auditScenario4_MultipleImplementations());
        results.add(auditScenario5_RuntimeStatePreservation());

        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("DIFFERENTIAL AUDIT SUMMARY");
        System.out.println("-----------------------------------------------------------------");
        int passed = 0;
        for (TestResult r : results) {
            System.out.printf("[%s] %s: %s\n", r.passed ? "PASS" : "FAIL", r.scenarioName, r.notes);
            if (r.passed) passed++;
        }
        System.out.printf("\nTotal: %d, Passed: %d, Failed: %d\n", results.size(), passed, results.size() - passed);
        System.out.println("=================================================================");
    }

    // =========================================================================
    // SCENARIO 1: Basic Dependency Inversion
    // =========================================================================
    static class OrigNotificationService {
        public List<String> log = new ArrayList<>();
        public String sendReceipt(String orderId, double amount) {
            String msg = "RECEIPT:" + orderId + ":" + amount;
            log.add(msg);
            return msg;
        }
    }

    static class OrigOrderService {
        private OrigNotificationService notificationService = new OrigNotificationService();
        public String processOrder(String orderId, double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            return notificationService.sendReceipt(orderId, amount);
        }
        public List<String> getLog() { return notificationService.log; }
    }

    interface IRefactoredNotificationService {
        String sendReceipt(String orderId, double amount);
    }

    static class RefactoredNotificationService implements IRefactoredNotificationService {
        public List<String> log = new ArrayList<>();
        @Override
        public String sendReceipt(String orderId, double amount) {
            String msg = "RECEIPT:" + orderId + ":" + amount;
            log.add(msg);
            return msg;
        }
    }

    static class RefactoredOrderService {
        private IRefactoredNotificationService notificationService;
        public RefactoredOrderService(IRefactoredNotificationService notificationService) {
            this.notificationService = notificationService;
        }
        public String processOrder(String orderId, double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            if (notificationService != null) {
                return notificationService.sendReceipt(orderId, amount);
            }
            return null;
        }
    }

    private static TestResult auditScenario1_BasicDependencyInversion() {
        System.out.println("\n[SCENARIO 1] Auditing Basic Dependency Inversion...");
        OrigOrderService orig = new OrigOrderService();
        RefactoredNotificationService refNotif = new RefactoredNotificationService();
        RefactoredOrderService ref = new RefactoredOrderService(refNotif);

        String origOut = orig.processOrder("ORD-101", 149.99);
        String refOut = ref.processOrder("ORD-101", 149.99);

        boolean outputMatch = origOut.equals(refOut);
        boolean logMatch = orig.getLog().equals(refNotif.log);

        // Exception equivalence check
        boolean exceptionMatch = false;
        try {
            orig.processOrder("ORD-ERR", -5.0);
        } catch (IllegalArgumentException e1) {
            try {
                ref.processOrder("ORD-ERR", -5.0);
            } catch (IllegalArgumentException e2) {
                exceptionMatch = e1.getMessage().equals(e2.getMessage());
            }
        }

        boolean pass = outputMatch && logMatch && exceptionMatch;
        System.out.printf("  Original Output:   %s\n", origOut);
        System.out.printf("  Refactored Output: %s\n", refOut);
        System.out.printf("  Outputs Match:     %b, Logs Match: %b, Exceptions Match: %b\n", outputMatch, logMatch, exceptionMatch);

        return new TestResult("Scenario 1 — Basic Dependency Inversion", pass, origOut, refOut,
                pass ? "Observable outputs, state logs, and exceptions identical." : "Discrepancy detected.");
    }

    // =========================================================================
    // SCENARIO 2: Constructor Dependency Preservation
    // =========================================================================
    static class OrigPaymentService {
        private OrigNotificationService notif;
        private double balance = 1000.0;
        public OrigPaymentService(OrigNotificationService notif) {
            this.notif = notif;
        }
        public boolean charge(String id, double amt) {
            if (balance >= amt) {
                balance -= amt;
                notif.sendReceipt(id, amt);
                return true;
            }
            return false;
        }
        public double getBalance() { return balance; }
    }

    static class RefactoredPaymentService {
        private IRefactoredNotificationService notif;
        private double balance = 1000.0;
        public RefactoredPaymentService(IRefactoredNotificationService notif) {
            this.notif = notif;
        }
        public boolean charge(String id, double amt) {
            if (balance >= amt) {
                balance -= amt;
                if (notif != null) {
                    notif.sendReceipt(id, amt);
                }
                return true;
            }
            return false;
        }
        public double getBalance() { return balance; }
    }

    private static TestResult auditScenario2_ConstructorDependencyPreservation() {
        System.out.println("\n[SCENARIO 2] Auditing Constructor Dependency Preservation...");
        OrigNotificationService origNotif = new OrigNotificationService();
        OrigPaymentService orig = new OrigPaymentService(origNotif);

        RefactoredNotificationService refNotif = new RefactoredNotificationService();
        RefactoredPaymentService ref = new RefactoredPaymentService(refNotif);

        boolean origC1 = orig.charge("PAY-1", 200.0);
        boolean refC1 = ref.charge("PAY-1", 200.0);
        boolean origC2 = orig.charge("PAY-2", 900.0); // should fail
        boolean refC2 = ref.charge("PAY-2", 900.0); // should fail

        boolean resultsMatch = (origC1 == refC1) && (origC2 == refC2);
        boolean balanceMatch = (orig.getBalance() == ref.getBalance());
        boolean logsMatch = origNotif.log.equals(refNotif.log);

        boolean pass = resultsMatch && balanceMatch && logsMatch;
        System.out.printf("  Original Balance:   %.2f, Invocations: %d\n", orig.getBalance(), origNotif.log.size());
        System.out.printf("  Refactored Balance: %.2f, Invocations: %d\n", ref.getBalance(), refNotif.log.size());

        return new TestResult("Scenario 2 — Constructor Dependency Preservation", pass,
                "Bal: " + orig.getBalance(), "Bal: " + ref.getBalance(),
                pass ? "Constructor injection preserved object state and downstream calls." : "State discrepancy.");
    }

    // =========================================================================
    // SCENARIO 3: Interface Method Signature Preservation
    // =========================================================================
    static class TargetProvider {
        public String executeTask(String id, int priority, double budget) throws IOException, IllegalArgumentException {
            return "Task " + id;
        }
        public <T extends Comparable<T>> T findMax(List<T> items) {
            return Collections.max(items);
        }
        public int[] processBatch(int[] data) {
            return data;
        }
    }

    interface ITargetProvider {
        String executeTask(String id, int priority, double budget) throws IOException, IllegalArgumentException;
        <T extends Comparable<T>> T findMax(List<T> items);
        int[] processBatch(int[] data);
    }

    private static TestResult auditScenario3_InterfaceMethodSignaturePreservation() {
        System.out.println("\n[SCENARIO 3] Auditing Interface Method Signature Preservation...");
        Method[] classMethods = TargetProvider.class.getDeclaredMethods();
        Method[] ifaceMethods = ITargetProvider.class.getDeclaredMethods();

        boolean allSignaturesFound = true;
        for (Method im : ifaceMethods) {
            boolean found = false;
            for (Method cm : classMethods) {
                if (cm.getName().equals(im.getName()) &&
                    cm.getReturnType().equals(im.getReturnType()) &&
                    Arrays.equals(cm.getParameterTypes(), im.getParameterTypes()) &&
                    Arrays.equals(cm.getExceptionTypes(), im.getExceptionTypes())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.printf("  Mismatch on interface method: %s\n", im.getName());
                allSignaturesFound = false;
            }
        }

        System.out.printf("  Class methods: %d, Interface methods: %d, Signature match: %b\n",
                classMethods.length, ifaceMethods.length, allSignaturesFound);

        return new TestResult("Scenario 3 — Interface Method Signature Preservation", allSignaturesFound,
                ifaceMethods.length + " methods", classMethods.length + " methods",
                allSignaturesFound ? "All names, return types, parameter types, generics, and checked exceptions preserved." : "Signature divergence.");
    }

    // =========================================================================
    // SCENARIO 4: Multiple Implementations
    // =========================================================================
    static class EmailNotificationService implements IRefactoredNotificationService {
        public List<String> emails = new ArrayList<>();
        @Override
        public String sendReceipt(String orderId, double amount) {
            String m = "EMAIL_RECEIPT:" + orderId + ":$" + amount;
            emails.add(m);
            return m;
        }
    }

    static class SmsNotificationService implements IRefactoredNotificationService {
        public List<String> smsMessages = new ArrayList<>();
        @Override
        public String sendReceipt(String orderId, double amount) {
            String m = "SMS_RECEIPT:" + orderId + ":$" + amount;
            smsMessages.add(m);
            return m;
        }
    }

    private static TestResult auditScenario4_MultipleImplementations() {
        System.out.println("\n[SCENARIO 4] Auditing Multiple Implementations...");
        EmailNotificationService emailService = new EmailNotificationService();
        SmsNotificationService smsService = new SmsNotificationService();

        RefactoredOrderService emailOrderService = new RefactoredOrderService(emailService);
        RefactoredOrderService smsOrderService = new RefactoredOrderService(smsService);

        String out1 = emailOrderService.processOrder("ORD-A", 50.0);
        String out2 = smsOrderService.processOrder("ORD-B", 75.0);

        boolean pass = out1.startsWith("EMAIL_RECEIPT") && out2.startsWith("SMS_RECEIPT") &&
                       emailService.emails.size() == 1 && smsService.smsMessages.size() == 1;

        System.out.printf("  Implementation A Result: %s\n", out1);
        System.out.printf("  Implementation B Result: %s\n", out2);

        return new TestResult("Scenario 4 — Multiple Implementations", pass,
                out1, out2,
                pass ? "Polymorphic dispatch functions correctly across multiple interface implementations." : "Polymorphism failure.");
    }

    // =========================================================================
    // SCENARIO 5: Runtime State Preservation
    // =========================================================================
    static class StatefulService {
        private int sequence = 0;
        private String currentState = "INITIAL";
        private final List<String> transitionLog = new ArrayList<>();

        public void transition(String newState) {
            sequence++;
            this.currentState = newState;
            transitionLog.add(sequence + ":" + newState);
        }

        public String getCurrentState() { return currentState; }
        public int getSequence() { return sequence; }
        public List<String> getTransitionLog() { return transitionLog; }
    }

    static class OrigStateCoordinator {
        private StatefulService statefulService = new StatefulService();
        public void executeWorkflow() {
            statefulService.transition("PENDING");
            statefulService.transition("PROCESSING");
            statefulService.transition("COMPLETED");
        }
        public List<String> getLog() { return statefulService.getTransitionLog(); }
    }

    interface IStatefulService {
        void transition(String newState);
        String getCurrentState();
        int getSequence();
        List<String> getTransitionLog();
    }

    static class RefactoredStatefulService extends StatefulService implements IStatefulService {}

    static class RefactoredStateCoordinator {
        private IStatefulService statefulService;
        public RefactoredStateCoordinator(IStatefulService statefulService) {
            this.statefulService = statefulService;
        }
        public void executeWorkflow() {
            if (statefulService != null) {
                statefulService.transition("PENDING");
                statefulService.transition("PROCESSING");
                statefulService.transition("COMPLETED");
            }
        }
        public List<String> getLog() { return statefulService != null ? statefulService.getTransitionLog() : Collections.emptyList(); }
    }

    private static TestResult auditScenario5_RuntimeStatePreservation() {
        System.out.println("\n[SCENARIO 5] Auditing Runtime State Preservation...");
        OrigStateCoordinator orig = new OrigStateCoordinator();
        orig.executeWorkflow();

        RefactoredStatefulService refStateful = new RefactoredStatefulService();
        RefactoredStateCoordinator ref = new RefactoredStateCoordinator(refStateful);
        ref.executeWorkflow();

        boolean match = orig.getLog().equals(ref.getLog());
        System.out.printf("  Original Log:   %s\n", orig.getLog());
        System.out.printf("  Refactored Log: %s\n", ref.getLog());

        return new TestResult("Scenario 5 — Runtime State Preservation", match,
                orig.getLog().toString(), ref.getLog().toString(),
                match ? "State transitions, counters, and execution sequence exactly preserved." : "State sequence diverted.");
    }
}
