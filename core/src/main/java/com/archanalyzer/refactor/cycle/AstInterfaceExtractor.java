package com.archanalyzer.refactor.cycle;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

import javax.lang.model.element.Modifier;
import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Phase 2: Compiler-Aware Interface Extractor using the Java Compiler Tree API.
 * Identifies public non-static methods, generic parameters, annotations, and thrown exceptions,
 * and extracts them into a clean, strongly-typed interface.
 */
public class AstInterfaceExtractor {

    public static class ExtractionResult {
        public final String interfaceName;
        public final String packageName;
        public final Path interfaceFile;
        public final String interfaceSource;
        public final List<String> extractedSignatures;
        public final SourceEdit classImplementsEdit;
        public final boolean reusedExisting;

        public ExtractionResult(String interfaceName, String packageName, Path interfaceFile,
                                String interfaceSource, List<String> extractedSignatures,
                                SourceEdit classImplementsEdit, boolean reusedExisting) {
            this.interfaceName = interfaceName;
            this.packageName = packageName;
            this.interfaceFile = interfaceFile;
            this.interfaceSource = interfaceSource;
            this.extractedSignatures = extractedSignatures;
            this.classImplementsEdit = classImplementsEdit;
            this.reusedExisting = reusedExisting;
        }
    }

    /**
     * Extracts an interface from the provider class file.
     */
    public ExtractionResult extractInterface(Path projectRoot, Path providerFile, String targetClassSimpleName) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("System Java compiler (javac) required at runtime for AST analysis.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, List.of("-proc:none"), null,
                fm.getJavaFileObjectsFromPaths(List.of(providerFile)));

        Iterable<? extends CompilationUnitTree> units;
        try {
            units = task.parse();
        } catch (Exception e) {
            throw new IOException("Failed to parse provider source: " + providerFile + " (" + e.getMessage() + ")", e);
        }

        Trees trees = Trees.instance(task);
        SourcePositions sp = trees.getSourcePositions();

        String packageName = "";
        List<String> imports = new ArrayList<>();
        List<String> methodSignatures = new ArrayList<>();
        ClassTree targetClassTree = null;
        CompilationUnitTree targetUnit = null;

        for (CompilationUnitTree unit : units) {
            if (unit.getPackageName() != null) {
                packageName = unit.getPackageName().toString();
            }
            for (ImportTree imp : unit.getImports()) {
                imports.add(imp.toString().trim());
            }
            for (Tree typeDecl : unit.getTypeDecls()) {
                if (typeDecl instanceof ClassTree ct && ct.getSimpleName().toString().equals(targetClassSimpleName)) {
                    targetClassTree = ct;
                    targetUnit = unit;
                    break;
                }
            }
        }

        if (targetClassTree == null) {
            throw new IOException("Target class '" + targetClassSimpleName + "' not found in " + providerFile);
        }

        // Check if class is an interface, enum, or record
        Tree.Kind kind = targetClassTree.getKind();
        if (kind == Tree.Kind.INTERFACE) {
            throw new IOException("Target '" + targetClassSimpleName + "' is already an interface.");
        }

        for (Tree member : targetClassTree.getMembers()) {
            if (member instanceof MethodTree mt && isPublicNonConstructor(mt)) {
                methodSignatures.add(buildAstMethodSignature(mt));
            }
        }

        if (methodSignatures.isEmpty()) {
            throw new IOException("No public non-static methods found on " + targetClassSimpleName + " to extract.");
        }

        String interfaceName = "I" + targetClassSimpleName;
        Path interfaceFile = providerFile.getParent().resolve(interfaceName + ".java");

        // Check if interface already exists
        boolean reused = false;
        if (Files.exists(interfaceFile)) {
            reused = true;
        }

        String interfaceSource = renderInterface(packageName, interfaceName, imports, methodSignatures);

        // Compute class header edit to add 'implements I...'
        String originalSource = Files.readString(providerFile, StandardCharsets.UTF_8);
        SourceEdit implementsEdit = computeImplementsEdit(originalSource, targetClassSimpleName, interfaceName, targetUnit, targetClassTree, sp);

        return new ExtractionResult(interfaceName, packageName, interfaceFile, interfaceSource, methodSignatures, implementsEdit, reused);
    }

    private boolean isPublicNonConstructor(MethodTree mt) {
        boolean isConstructor = mt.getName().toString().equals("<init>");
        boolean isPublic = mt.getModifiers().getFlags().contains(Modifier.PUBLIC);
        boolean isStatic = mt.getModifiers().getFlags().contains(Modifier.STATIC);
        return isPublic && !isConstructor && !isStatic;
    }

    private String buildAstMethodSignature(MethodTree mt) {
        StringBuilder sb = new StringBuilder("    ");

        // Generic type parameters (e.g. <T, R>)
        if (mt.getTypeParameters() != null && !mt.getTypeParameters().isEmpty()) {
            sb.append("<");
            List<String> tps = new ArrayList<>();
            for (TypeParameterTree tp : mt.getTypeParameters()) {
                tps.add(tp.toString());
            }
            sb.append(String.join(", ", tps)).append("> ");
        }

        // Return type
        sb.append(mt.getReturnType() != null ? mt.getReturnType().toString() : "void").append(" ");

        // Method name
        sb.append(mt.getName().toString()).append("(");

        // Parameters
        List<String> params = new ArrayList<>();
        for (VariableTree vt : mt.getParameters()) {
            StringBuilder paramSb = new StringBuilder();
            // Parameter annotations (e.g., @NotNull)
            if (vt.getModifiers() != null && vt.getModifiers().getAnnotations() != null) {
                for (AnnotationTree at : vt.getModifiers().getAnnotations()) {
                    paramSb.append(at.toString()).append(" ");
                }
            }
            paramSb.append(vt.getType().toString()).append(" ").append(vt.getName().toString());
            params.add(paramSb.toString());
        }
        sb.append(String.join(", ", params)).append(")");

        // Thrown exceptions
        if (mt.getThrows() != null && !mt.getThrows().isEmpty()) {
            List<String> throwsList = new ArrayList<>();
            for (ExpressionTree ex : mt.getThrows()) {
                throwsList.add(ex.toString());
            }
            sb.append(" throws ").append(String.join(", ", throwsList));
        }

        sb.append(";");
        return sb.toString();
    }

    private String renderInterface(String packageName, String interfaceName, List<String> imports, List<String> signatures) {
        StringBuilder sb = new StringBuilder();
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                sb.append(imp).append("\n");
            }
            sb.append("\n");
        }
        sb.append("/**\n")
          .append(" * Auto-generated interface for dependency inversion refactoring.\n")
          .append(" * Defines the extracted public contract of ").append(interfaceName.substring(1)).append(".\n")
          .append(" */\n");
        sb.append("public interface ").append(interfaceName).append(" {\n\n");
        for (String sig : signatures) {
            sb.append(sig).append("\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private SourceEdit computeImplementsEdit(String source, String className, String interfaceName,
                                            CompilationUnitTree unit, ClassTree ct, SourcePositions sp) {
        long start = sp.getStartPosition(unit, ct);
        if (start < 0 || start >= source.length()) {
            start = source.indexOf("class " + className);
        }

        // Find the opening brace of this class
        int braceIdx = source.indexOf('{', (int) start);
        if (braceIdx == -1) {
            return null;
        }

        String header = source.substring((int) start, braceIdx);
        // Check if already implements this interface
        if (header.contains(interfaceName)) {
            return null; // already implemented
        }

        int replaceStart;
        int replaceEnd;
        String replacement;

        if (header.contains("implements ")) {
            // Append to existing implements clause
            int implIdx = (int) start + header.indexOf("implements ") + "implements ".length();
            replaceStart = implIdx;
            replaceEnd = implIdx;
            replacement = interfaceName + ", ";
        } else {
            // Insert implements before opening brace
            int insertPos = braceIdx;
            // Trim back spaces before brace
            while (insertPos > start && Character.isWhitespace(source.charAt(insertPos - 1))) {
                insertPos--;
            }
            replaceStart = insertPos;
            replaceEnd = insertPos;
            replacement = " implements " + interfaceName;
        }

        return new SourceEdit(replaceStart, replaceEnd, replacement, "Add implements " + interfaceName);
    }
}
