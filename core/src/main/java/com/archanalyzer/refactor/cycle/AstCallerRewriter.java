package com.archanalyzer.refactor.cycle;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Rewrites caller / client classes using the Java Compiler Tree API.
 * Identifies field declarations, constructor/method parameters, local variables,
 * and decouples concrete instantiation where specified, replacing concrete types
 * with the inverted interface.
 *
 * Phase S.2 Hardened:
 * - DEFECT-S1-01: Removes inline field initializers on decoupled fields to prevent
 *   double instantiation and preserve exact constructor side-effect semantics.
 * - DEFECT-S1-02: Inspects provider constructor declarations to prevent unsafe
 *   reflection when the provider lacks an accessible no-arg constructor.
 * - DEFECT-S1-04: Reuses existing setter methods instead of generating duplicates.
 */
public class AstCallerRewriter {

    public static class RewriteResult {
        private final Path filePath;
        private final List<SourceEdit> edits;
        private final int rewrittenFields;
        private final int rewrittenParams;
        private final int rewrittenLocals;
        private final int rewrittenConstructors;
        private final boolean importAdded;
        private final boolean defaultProviderGenerated;
        private final String explanation;

        public RewriteResult(Path filePath,
                             List<SourceEdit> edits,
                             int rewrittenFields,
                             int rewrittenParams,
                             int rewrittenLocals,
                             int rewrittenConstructors,
                             boolean importAdded) {
            this(filePath, edits, rewrittenFields, rewrittenParams, rewrittenLocals, rewrittenConstructors, importAdded, true, "");
        }

        public RewriteResult(Path filePath,
                             List<SourceEdit> edits,
                             int rewrittenFields,
                             int rewrittenParams,
                             int rewrittenLocals,
                             int rewrittenConstructors,
                             boolean importAdded,
                             boolean defaultProviderGenerated,
                             String explanation) {
            this.filePath = filePath;
            this.edits = edits;
            this.rewrittenFields = rewrittenFields;
            this.rewrittenParams = rewrittenParams;
            this.rewrittenLocals = rewrittenLocals;
            this.rewrittenConstructors = rewrittenConstructors;
            this.importAdded = importAdded;
            this.defaultProviderGenerated = defaultProviderGenerated;
            this.explanation = explanation != null ? explanation : "";
        }

        public Path getFilePath() { return filePath; }
        public List<SourceEdit> getEdits() { return edits; }
        public int getRewrittenFields() { return rewrittenFields; }
        public int getRewrittenParams() { return rewrittenParams; }
        public int getRewrittenLocals() { return rewrittenLocals; }
        public int getRewrittenConstructors() { return rewrittenConstructors; }
        public boolean isImportAdded() { return importAdded; }
        public boolean isDefaultProviderGenerated() { return defaultProviderGenerated; }
        public String getExplanation() { return explanation; }
    }

    public RewriteResult rewriteCaller(Path callerSourceFile,
                                        String concreteTargetSimpleName,
                                        String concreteTargetFqn,
                                        String interfaceSimpleName,
                                        String interfaceFqn,
                                        String interfacePackage,
                                        boolean decoupleConstructorInstantiation) throws IOException {
        return rewriteCaller(callerSourceFile, null, concreteTargetSimpleName, concreteTargetFqn,
                interfaceSimpleName, interfaceFqn, interfacePackage, decoupleConstructorInstantiation);
    }

    public RewriteResult rewriteCaller(Path callerSourceFile,
                                        Path providerSourceFile,
                                        String concreteTargetSimpleName,
                                        String concreteTargetFqn,
                                        String interfaceSimpleName,
                                        String interfaceFqn,
                                        String interfacePackage,
                                        boolean decoupleConstructorInstantiation) throws IOException {
        String source = Files.readString(callerSourceFile, StandardCharsets.UTF_8);

        if (providerSourceFile == null) {
            providerSourceFile = resolveProviderSourceFile(callerSourceFile, concreteTargetSimpleName);
        }

        boolean hasSafeNoArgCtor = hasAccessibleNoArgConstructor(providerSourceFile, concreteTargetSimpleName, concreteTargetFqn);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(callerSourceFile.toFile());
        JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, null, null, fileObjects);

        CompilationUnitTree cu = task.parse().iterator().next();
        SourcePositions positions = Trees.instance(task).getSourcePositions();

        List<SourceEdit> edits = new ArrayList<>();
        int[] counts = new int[4]; // [fields, params, locals, constructors]
        boolean[] importAdded = new boolean[1];

        // 1. Check Package & Import
        String callerPackage = cu.getPackage() != null ? cu.getPackage().getPackageName().toString() : "";
        boolean samePackage = callerPackage.equals(interfacePackage);
        boolean importExists = false;

        for (ImportTree imp : cu.getImports()) {
            String impStr = imp.getQualifiedIdentifier().toString();
            if (impStr.equals(interfaceFqn) || impStr.equals(interfacePackage + ".*")) {
                importExists = true;
                break;
            }
        }

        if (!samePackage && !importExists && interfacePackage != null && !interfacePackage.isEmpty()) {
            int insertPos = 0;
            if (cu.getImports() != null && !cu.getImports().isEmpty()) {
                ImportTree lastImport = cu.getImports().get(cu.getImports().size() - 1);
                insertPos = (int) positions.getEndPosition(cu, lastImport);
                edits.add(new SourceEdit(insertPos, insertPos, "\nimport " + interfaceFqn + ";",
                        "Add import for inverted interface"));
            } else if (cu.getPackage() != null) {
                insertPos = (int) positions.getEndPosition(cu, cu.getPackage());
                edits.add(new SourceEdit(insertPos, insertPos, "\n\nimport " + interfaceFqn + ";",
                        "Add import for inverted interface"));
            } else {
                edits.add(new SourceEdit(0, 0, "import " + interfaceFqn + ";\n\n",
                        "Add import for inverted interface"));
            }
            importAdded[0] = true;
        }

        Set<String> rewrittenFieldNames = new HashSet<>();
        Set<String> finalFieldNames = new HashSet<>();
        Set<String> fieldInitializersRemoved = new HashSet<>();

        // 2. Scan AST for variables, parameters, and constructors
        TreePathScanner<Void, Void> scanner = new TreePathScanner<>() {
            private Tree currentParent = null;

            @Override
            public Void scan(Tree tree, Void p) {
                Tree prevParent = currentParent;
                currentParent = tree;
                Void result = super.scan(tree, p);
                currentParent = prevParent;
                return result;
            }

            @Override
            public Void visitVariable(VariableTree node, Void p) {
                Tree typeTree = node.getType();
                if (typeTree != null && isTargetType(typeTree, concreteTargetSimpleName, concreteTargetFqn)) {
                    Tree parent = getCurrentPath().getParentPath() != null ?
                            getCurrentPath().getParentPath().getLeaf() : null;

                    long start = positions.getStartPosition(cu, typeTree);
                    long end = positions.getEndPosition(cu, typeTree);

                    if (parent instanceof ClassTree) {
                        // Field declaration
                        edits.add(new SourceEdit((int) start, (int) end, interfaceSimpleName,
                                "Rewrite field type to interface " + interfaceSimpleName));
                        counts[0]++;
                        rewrittenFieldNames.add(node.getName().toString());
                        if (node.getModifiers() != null && node.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.FINAL)) {
                            finalFieldNames.add(node.getName().toString());
                        }

                        // DEFECT-S1-01 Fix: If decoupling constructor instantiation, strip inline field initializer
                        // so that constructor delegation or explicit DI does not double-instantiate the provider.
                        if (decoupleConstructorInstantiation && node.getInitializer() != null) {
                            ExpressionTree init = node.getInitializer();
                            int initStart = (int) positions.getStartPosition(cu, init);
                            int initEnd = (int) positions.getEndPosition(cu, init);

                            // Find the '=' preceding the initializer
                            int eqStart = initStart - 1;
                            while (eqStart >= 0 && Character.isWhitespace(source.charAt(eqStart))) {
                                eqStart--;
                            }
                            if (eqStart >= 0 && source.charAt(eqStart) == '=') {
                                while (eqStart > 0 && (source.charAt(eqStart - 1) == ' ' || source.charAt(eqStart - 1) == '\t')) {
                                    eqStart--;
                                }
                                edits.add(new SourceEdit(eqStart, initEnd, "",
                                        "Remove inline field initializer for decoupled field " + node.getName()));
                                fieldInitializersRemoved.add(node.getName().toString());
                            }
                        }

                    } else if (parent instanceof MethodTree) {
                        // Method or constructor parameter
                        edits.add(new SourceEdit((int) start, (int) end, interfaceSimpleName,
                                "Rewrite parameter type to interface " + interfaceSimpleName));
                        counts[1]++;
                    } else {
                        // Local variable
                        edits.add(new SourceEdit((int) start, (int) end, interfaceSimpleName,
                                "Rewrite local variable type to interface " + interfaceSimpleName));
                        counts[2]++;
                    }
                }
                return super.visitVariable(node, p);
            }

            @Override
            public Void visitMethod(MethodTree node, Void p) {
                Tree returnType = node.getReturnType();
                if (returnType != null && isTargetType(returnType, concreteTargetSimpleName, concreteTargetFqn)) {
                    long start = positions.getStartPosition(cu, returnType);
                    long end = positions.getEndPosition(cu, returnType);
                    edits.add(new SourceEdit((int) start, (int) end, interfaceSimpleName,
                            "Rewrite return type to interface " + interfaceSimpleName));
                }
                return super.visitMethod(node, p);
            }

            @Override
            public Void visitNewClass(NewClassTree node, Void p) {
                if (decoupleConstructorInstantiation) {
                    Tree idTree = node.getIdentifier();
                    if (idTree != null && isTargetType(idTree, concreteTargetSimpleName, concreteTargetFqn)) {
                        // If this NewClass is part of an inline field initializer that was stripped above, skip it.
                        Tree parent = getCurrentPath().getParentPath() != null ?
                                getCurrentPath().getParentPath().getLeaf() : null;
                        if (parent instanceof VariableTree vt) {
                            Tree grandParent = getCurrentPath().getParentPath().getParentPath() != null ?
                                    getCurrentPath().getParentPath().getParentPath().getLeaf() : null;
                            if (grandParent instanceof ClassTree) {
                                return super.visitNewClass(node, p);
                            }
                        }

                        // For method bodies or constructor bodies, only rewrite if safe default constructor exists
                        if (hasSafeNoArgCtor) {
                            long start = positions.getStartPosition(cu, node);
                            long end = positions.getEndPosition(cu, node);
                            String providerCall = "createDefault" + interfaceSimpleName.substring(1) + "()";
                            edits.add(new SourceEdit((int) start, (int) end, providerCall,
                                    "Decouple direct concrete instantiation of " + concreteTargetSimpleName + " to safe default provider"));
                            counts[3]++;
                        }
                    }
                }
                return super.visitNewClass(node, p);
            }

            @Override
            public Void visitExpressionStatement(ExpressionStatementTree node, Void p) {
                // DEF-01 Fix: Never wrap method invocations in silent no-op null guards.
                return super.visitExpressionStatement(node, p);
            }
        };

        scanner.scan(cu, null);

        // 3. If field was decoupled, add constructor & setter injection and safe default provider
        String refactoringExplanation = "";
        if (decoupleConstructorInstantiation && !rewrittenFieldNames.isEmpty()) {
            for (Tree def : cu.getTypeDecls()) {
                if (def instanceof ClassTree) {
                    ClassTree ct = (ClassTree) def;
                    String callerClassName = ct.getSimpleName().toString();
                    long classEnd = positions.getEndPosition(cu, ct);

                    boolean hasAnyConstructor = false;
                    boolean hasNoArgConstructor = false;
                    for (Tree member : ct.getMembers()) {
                        if (member instanceof MethodTree mt) {
                            if (mt.getName().toString().equals("<init>")) {
                                hasAnyConstructor = true;
                                if (mt.getParameters().isEmpty()) {
                                    hasNoArgConstructor = true;
                                }
                            }
                        }
                    }

                    for (String fieldName : rewrittenFieldNames) {
                        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                        String defaultProviderMethod = "createDefault" + interfaceSimpleName.substring(1);
                        StringBuilder sb = new StringBuilder();

                        // DEFECT-S1-01 & DEFECT-S1-02:
                        // Only generate default constructor delegation & factory method if target has an accessible no-arg constructor.
                        if (hasSafeNoArgCtor) {
                            if (!hasAnyConstructor) {
                                // No explicit constructors: generate default constructor + DI constructor
                                sb.append("\n\n    public ").append(callerClassName).append("() {\n")
                                  .append("        this(").append(defaultProviderMethod).append("());\n")
                                  .append("    }\n\n")
                                  .append("    public ").append(callerClassName).append("(").append(interfaceSimpleName).append(" ").append(fieldName).append(") {\n")
                                  .append("        if (").append(fieldName).append(" == null) {\n")
                                  .append("            throw new IllegalArgumentException(\"").append(fieldName).append(" must not be null\");\n")
                                  .append("        }\n")
                                  .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                                  .append("    }\n\n");
                            } else if (hasNoArgConstructor) {
                                // Existing no-arg constructor: delegate to it with this() to ensure final fields are initialized
                                sb.append("\n\n    public ").append(callerClassName).append("(").append(interfaceSimpleName).append(" ").append(fieldName).append(") {\n")
                                  .append("        this();\n")
                                  .append("        if (").append(fieldName).append(" == null) {\n")
                                  .append("            throw new IllegalArgumentException(\"").append(fieldName).append(" must not be null\");\n")
                                  .append("        }\n")
                                  .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                                  .append("    }\n\n");
                            }
                        } else {
                            // DEFECT-S1-02 Fix: Provider lacks accessible no-arg constructor.
                            // Generate explicit DI constructor only, requiring the caller to supply the dependency.
                            refactoringExplanation = "Omitted default constructor for " + callerClassName + " because concrete provider " +
                                    concreteTargetSimpleName + " lacks an accessible parameterless constructor. Explicit dependency injection is required.";
                            if (!hasAnyConstructor || hasNoArgConstructor) {
                                sb.append("\n\n    public ").append(callerClassName).append("(").append(interfaceSimpleName).append(" ").append(fieldName).append(") {\n");
                                if (hasNoArgConstructor) {
                                    sb.append("        this();\n");
                                }
                                sb.append("        if (").append(fieldName).append(" == null) {\n")
                                  .append("            throw new IllegalArgumentException(\"").append(fieldName).append(" must not be null\");\n")
                                  .append("        }\n")
                                  .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                                  .append("    }\n\n");
                            }
                        }

                        // DEFECT-S1-04 Fix: Check if a setter for this dependency already exists in the class.
                        // If it does, do NOT generate a duplicate setter method.
                        boolean hasExistingSetter = false;
                        for (Tree member : ct.getMembers()) {
                            if (member instanceof MethodTree mt) {
                                String mName = mt.getName().toString();
                                if (mName.equalsIgnoreCase("set" + capitalized) || mName.equalsIgnoreCase("set" + fieldName)) {
                                    if (mt.getParameters().size() == 1) {
                                        VariableTree param = mt.getParameters().get(0);
                                        if (isTargetType(param.getType(), concreteTargetSimpleName, concreteTargetFqn)
                                                || isTargetType(param.getType(), interfaceSimpleName, interfaceFqn)) {
                                            hasExistingSetter = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // DEFECT-S1-04 Fix & Final Field Safety:
                        // A setter is only generated if:
                        // 1. No setter already exists for this dependency (avoids duplicate method collisions).
                        // 2. The field is NOT final (final fields cannot be assigned outside constructors).
                        if (!hasExistingSetter && !finalFieldNames.contains(fieldName)) {
                            sb.append("    public void set").append(capitalized).append("(").append(interfaceSimpleName).append(" ").append(fieldName).append(") {\n")
                              .append("        if (").append(fieldName).append(" == null) {\n")
                              .append("            throw new IllegalArgumentException(\"" ).append(fieldName).append(" must not be null\");\n")
                              .append("        }\n")
                              .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                              .append("    }\n\n");
                        }

                        // Only generate default factory method if safe no-arg constructor exists
                        if (hasSafeNoArgCtor) {
                            sb.append("    private static ").append(interfaceSimpleName).append(" ").append(defaultProviderMethod).append("() {\n")
                              .append("        try {\n")
                              .append("            return (").append(interfaceSimpleName).append(") Class.forName(\"").append(concreteTargetFqn).append("\").getDeclaredConstructor().newInstance();\n")
                              .append("        } catch (Exception e) {\n")
                              .append("            throw new IllegalStateException(\"Failed to instantiate default provider for ").append(interfaceSimpleName).append("\", e);\n")
                              .append("        }\n")
                              .append("    }\n");
                        }

                        int insertOffset = (int) classEnd - 1; // Before closing '}'
                        edits.add(new SourceEdit(insertOffset, insertOffset, sb.toString(),
                                "Add dependency injection constructor, setter, and safe default provider for " + interfaceSimpleName));
                    }
                }
            }
        }

        return new RewriteResult(callerSourceFile, edits, counts[0], counts[1], counts[2], counts[3], importAdded[0], hasSafeNoArgCtor, refactoringExplanation);
    }

    private boolean isTargetType(Tree typeTree, String simpleName, String fqn) {
        if (typeTree instanceof IdentifierTree) {
            return ((IdentifierTree) typeTree).getName().contentEquals(simpleName);
        } else if (typeTree instanceof MemberSelectTree) {
            String qName = typeTree.toString();
            return qName.equals(fqn) || qName.endsWith("." + simpleName);
        } else if (typeTree instanceof ParameterizedTypeTree) {
            ParameterizedTypeTree ptt = (ParameterizedTypeTree) typeTree;
            for (Tree arg : ptt.getTypeArguments()) {
                if (isTargetType(arg, simpleName, fqn)) {
                    return true;
                }
            }
        } else if (typeTree instanceof ArrayTypeTree) {
            return isTargetType(((ArrayTypeTree) typeTree).getType(), simpleName, fqn);
        }
        return false;
    }

    /**
     * Inspects the concrete provider class to determine if it has an accessible parameterless constructor.
     */
    private boolean hasAccessibleNoArgConstructor(Path providerSourceFile, String targetSimpleName, String targetFqn) {
        if (providerSourceFile != null && Files.exists(providerSourceFile)) {
            try {
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
                Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjects(providerSourceFile.toFile());
                JavacTask task = (JavacTask) compiler.getTask(null, fm, null, null, null, fos);
                CompilationUnitTree pcu = task.parse().iterator().next();
                fm.close();

                for (Tree def : pcu.getTypeDecls()) {
                    if (def instanceof ClassTree ct && ct.getSimpleName().toString().equals(targetSimpleName)) {
                        if (ct.getModifiers() != null && ct.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.ABSTRACT)) {
                            return false;
                        }
                        boolean hasAnyConstructor = false;
                        boolean hasNoArgConstructor = false;
                        for (Tree member : ct.getMembers()) {
                            if (member instanceof MethodTree mt && mt.getName().toString().equals("<init>")) {
                                hasAnyConstructor = true;
                                if (mt.getParameters().isEmpty()) {
                                    ModifiersTree mods = mt.getModifiers();
                                    if (mods == null || !mods.getFlags().contains(javax.lang.model.element.Modifier.PRIVATE)) {
                                        hasNoArgConstructor = true;
                                    }
                                }
                            }
                        }
                        // In Java, if no explicit constructor is declared, a public no-arg constructor is provided by javac.
                        if (!hasAnyConstructor) {
                            return true;
                        }
                        return hasNoArgConstructor;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Reflection fallback if class is loaded in runtime environment
        try {
            Class<?> cls = Class.forName(targetFqn);
            if (java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                return false;
            }
            java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
            int mods = ctor.getModifiers();
            return java.lang.reflect.Modifier.isPublic(mods) || !java.lang.reflect.Modifier.isPrivate(mods);
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable ignored) {}

        // If provider file does not exist on disk and class is not loaded, assume default constructor is available
        return true;
    }

    /**
     * Attempts to resolve the concrete provider source file from project structure.
     */
    private static Path resolveProviderSourceFile(Path callerSourceFile, String concreteTargetSimpleName) {
        if (callerSourceFile == null) return null;
        try {
            Path parent = callerSourceFile.getParent();
            if (parent != null) {
                Path sibling = parent.resolve(concreteTargetSimpleName + ".java");
                if (Files.exists(sibling)) return sibling;

                Path dir = parent;
                for (int i = 0; i < 6 && dir != null; i++) {
                    if (Files.exists(dir.resolve("pom.xml")) || Files.exists(dir.resolve("src"))) {
                        try (var walk = Files.walk(dir, 8)) {
                            var match = walk.filter(p -> p.getFileName().toString().equals(concreteTargetSimpleName + ".java"))
                                            .findFirst();
                            if (match.isPresent()) return match.get();
                        } catch (Exception ignored) {}
                        break;
                    }
                    dir = dir.getParent();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
