package com.archanalyzer.refactor.cycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S: Regression and verification test for DEF-01 (Semantically Safe Constructor Injection).
 * Verifies that converting concrete dependencies into interfaces preserves runtime side-effects,
 * supports default construction without null degradation, supports explicit DI, and throws
 * explicit exceptions rather than silently swallowing method calls.
 */
public class SemanticSafeConstructorInjectionTest {

    @Test
    public void testSection3E_SemanticallySafeConstructorAndSideEffectPreservation(@TempDir Path tempDir) throws Exception {
        // Step 1: Set up the exact fixture from Section 3.E
        Path notifFile = tempDir.resolve("NotificationService.java");
        Path payFile = tempDir.resolve("PaymentService.java");

        String notifSrc = "package com.fixture.shop;\n\n" +
                "public class NotificationService {\n" +
                "    public int receiptCount = 0;\n" +
                "    public void sendReceipt() {\n" +
                "        receiptCount++;\n" +
                "    }\n" +
                "}\n";

        String paySrc = "package com.fixture.shop;\n\n" +
                "public class PaymentService {\n" +
                "    private NotificationService notificationService = new NotificationService();\n" +
                "    public void pay() {\n" +
                "        notificationService.sendReceipt();\n" +
                "    }\n" +
                "}\n";

        Files.writeString(notifFile, notifSrc);
        Files.writeString(payFile, paySrc);

        // Step 2: Extract Interface
        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult extRes = extractor.extractInterface(tempDir, notifFile, "NotificationService");
        assertNotNull(extRes);
        assertEquals("INotificationService", extRes.interfaceName);
        Files.writeString(extRes.interfaceFile, extRes.interfaceSource);
        SourceFileEditor.applyEditsToFile(notifFile, List.of(extRes.classImplementsEdit));

        // Step 3: Rewrite Caller (PaymentService)
        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rewriteRes = rewriter.rewriteCaller(
                payFile,
                "NotificationService",
                "com.fixture.shop.NotificationService",
                "INotificationService",
                "com.fixture.shop.INotificationService",
                "com.fixture.shop",
                true // decouple constructor instantiation
        );

        assertNotNull(rewriteRes);
        SourceFileEditor.applyEditsToFile(payFile, rewriteRes.getEdits());

        String rewrittenPaymentSrc = Files.readString(payFile);

        // Verify AST Invariants:
        // 1. Must NOT contain literal "= null;" for decoupled dependency
        assertFalse(rewrittenPaymentSrc.contains("= null;"),
                "Refactoring must NOT substitute null for required dependency: " + rewrittenPaymentSrc);

        // 2. Must NOT wrap business methods in silent no-op null checks
        assertFalse(rewrittenPaymentSrc.contains("if (notificationService != null)"),
                "Refactoring must NOT wrap method calls in silent no-op null checks: " + rewrittenPaymentSrc);

        // 3. Must contain safe default provider and DI constructors
        assertTrue(rewrittenPaymentSrc.contains("createDefaultNotificationService()"),
                "Refactoring must use safe default provider: " + rewrittenPaymentSrc);
        assertTrue(rewrittenPaymentSrc.contains("public PaymentService(INotificationService notificationService)"),
                "Refactoring must provide explicit DI constructor: " + rewrittenPaymentSrc);
        assertTrue(rewrittenPaymentSrc.contains("public void setNotificationService(INotificationService notificationService)"),
                "Refactoring must provide setter injection: " + rewrittenPaymentSrc);
        assertTrue(rewrittenPaymentSrc.contains("throw new IllegalArgumentException"),
                "Refactoring must enforce non-null invariants: " + rewrittenPaymentSrc);

        // Step 4: Compile rewritten sources using javax.tools.JavaCompiler
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);

        int exitCode = compiler.run(null, null, null,
                "-d", binDir.toString(),
                notifFile.toString(),
                extRes.interfaceFile.toString(),
                payFile.toString());
        assertEquals(0, exitCode, "Rewritten sources must compile cleanly without errors");

        // Step 5: Test Execution and Side-Effect Invariant via isolated ClassLoader
        java.net.URLClassLoader classLoader = new java.net.URLClassLoader(
                new java.net.URL[]{ binDir.toUri().toURL() },
                this.getClass().getClassLoader()
        );

        Class<?> notifClass = classLoader.loadClass("com.fixture.shop.NotificationService");
        Class<?> ifaceClass = classLoader.loadClass("com.fixture.shop.INotificationService");
        Class<?> payClass = classLoader.loadClass("com.fixture.shop.PaymentService");

        // Scenario 1 & 2: Default Construction behavior & dispatch invariant
        Object payInstance1 = payClass.getDeclaredConstructor().newInstance();
        payClass.getMethod("pay").invoke(payInstance1);

        // Verify side effect: notificationService.receiptCount must be 1!
        // Access notificationService field via reflection
        java.lang.reflect.Field notifField = payClass.getDeclaredField("notificationService");
        notifField.setAccessible(true);
        Object notifInstance1 = notifField.get(payInstance1);
        assertNotNull(notifInstance1, "Dependency must NOT be null in default-constructed instance");
        int count1 = (int) notifClass.getField("receiptCount").get(notifInstance1);
        assertEquals(1, count1, "Invariant: Receipt dispatch count after refactoring must equal 1 (original count)!");

        // Scenario 3: Explicit Constructor Injection
        Object customNotif = notifClass.getDeclaredConstructor().newInstance();
        Object payInstance2 = payClass.getDeclaredConstructor(ifaceClass).newInstance(customNotif);
        payClass.getMethod("pay").invoke(payInstance2);
        int count2 = (int) notifClass.getField("receiptCount").get(customNotif);
        assertEquals(1, count2, "Explicit constructor injection must dispatch receipt to injected instance");

        // Scenario 4: Setter Injection
        Object customNotif3 = notifClass.getDeclaredConstructor().newInstance();
        Object payInstance3 = payClass.getDeclaredConstructor().newInstance();
        payClass.getMethod("setNotificationService", ifaceClass).invoke(payInstance3, customNotif3);
        payClass.getMethod("pay").invoke(payInstance3);
        int count3 = (int) notifClass.getField("receiptCount").get(customNotif3);
        assertEquals(1, count3, "Setter injection must dispatch receipt to set instance");

        // Scenario 7: Repeated Payment Execution
        payClass.getMethod("pay").invoke(payInstance3);
        int count3Repeat = (int) notifClass.getField("receiptCount").get(customNotif3);
        assertEquals(2, count3Repeat, "Repeated execution must increment receipt count to 2");

        // Scenario 9: Null Dependency Handling (Must fail fast, not silently swallow)
        try {
            payClass.getDeclaredConstructor(ifaceClass).newInstance((Object) null);
            fail("Passing null to DI constructor must throw exception!");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException,
                    "Must throw IllegalArgumentException on null injection: " + e.getCause());
        }

        classLoader.close();
    }
}
