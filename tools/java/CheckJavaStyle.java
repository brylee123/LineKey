// SPDX-License-Identifier: Apache-2.0
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Syntax-only style checks: no Android SDK, third-party parser, or project compilation required.
 */
public final class CheckJavaStyle extends TreeScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final String source;
    private int violations;

    private CheckJavaStyle(final CompilationUnitTree unit, final SourcePositions positions)
            throws Exception {
        this.unit = unit;
        this.positions = positions;
        source = Files.readString(Path.of(unit.getSourceFile().toUri()), StandardCharsets.UTF_8);
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Path.of(args[0]);
        final List<Path> paths = new ArrayList<>();
        for (final String folder : List.of("app/src", "core/src", "tools/java")) {
            try (final Stream<Path> files = Files.walk(root.resolve(folder))) {
                paths.addAll(
                        files.filter((final Path path) -> path.toString().endsWith(".java"))
                                .toList());
            }
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK, not a JRE.");
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        int failures = 0;
        try (final StandardJavaFileManager manager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            final JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    manager,
                                    diagnostics,
                                    List.of("-proc:none", "--release", "17"),
                                    null,
                                    manager.getJavaFileObjectsFromPaths(paths));
            final Iterable<? extends CompilationUnitTree> units = task.parse();
            final SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (final CompilationUnitTree unit : units) {
                final CheckJavaStyle checker = new CheckJavaStyle(unit, positions);
                checker.scan(unit, null);
                failures += checker.violations;
            }
        }
        for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic);
                failures++;
            }
        }
        if (failures != 0) {
            throw new IllegalStateException(failures + " Java style violations");
        }
        System.out.println("PASS: Java style rules across " + paths.size() + " files");
    }

    private void fail(final Tree tree, final String message) {
        final long offset = positions.getStartPosition(unit, tree);
        final long line = unit.getLineMap().getLineNumber(Math.max(0, offset));
        System.err.println(unit.getSourceFile().getName() + ":" + line + ": " + message);
        violations++;
    }

    private void finalBinding(final VariableTree variable) {
        if (!variable.getModifiers().getFlags().contains(Modifier.FINAL)) {
            fail(variable, "Declare this parameter or resource final");
        }
    }

    private void braced(final StatementTree statement) {
        if (statement != null && !(statement instanceof BlockTree)) {
            fail(statement, "Use braces around the control-flow body");
        }
    }

    @Override
    public Void visitImport(final ImportTree node, final Void unused) {
        if (node.getQualifiedIdentifier().toString().endsWith(".*")) {
            fail(node, "Use explicit imports");
        }
        return super.visitImport(node, unused);
    }

    @Override
    public Void visitVariable(final VariableTree node, final Void unused) {
        final int start = (int) positions.getStartPosition(unit, node);
        final int end = (int) positions.getEndPosition(unit, node);
        if (start >= 0
                && end > start
                && source.substring(start, end).matches("(?s)^(?:final\\s+)?var\\s+.*")) {
            fail(node, "Use an explicit type instead of var");
        }
        return super.visitVariable(node, unused);
    }

    @Override
    public Void visitMethod(final MethodTree node, final Void unused) {
        for (final VariableTree parameter : node.getParameters()) {
            finalBinding(parameter);
        }
        return super.visitMethod(node, unused);
    }

    @Override
    public Void visitLambdaExpression(final LambdaExpressionTree node, final Void unused) {
        for (final VariableTree parameter : node.getParameters()) {
            finalBinding(parameter);
            if (parameter.getType() == null) {
                fail(parameter, "Use an explicit lambda parameter type");
            }
        }
        return super.visitLambdaExpression(node, unused);
    }

    @Override
    public Void visitCatch(final CatchTree node, final Void unused) {
        finalBinding(node.getParameter());
        return super.visitCatch(node, unused);
    }

    @Override
    public Void visitTry(final TryTree node, final Void unused) {
        for (final Tree resource : node.getResources()) {
            if (resource instanceof final VariableTree variable) {
                finalBinding(variable);
            }
        }
        return super.visitTry(node, unused);
    }

    @Override
    public Void visitIf(final IfTree node, final Void unused) {
        braced(node.getThenStatement());
        if (!(node.getElseStatement() instanceof IfTree)) {
            braced(node.getElseStatement());
        }
        return super.visitIf(node, unused);
    }

    @Override
    public Void visitForLoop(final ForLoopTree node, final Void unused) {
        braced(node.getStatement());
        return super.visitForLoop(node, unused);
    }

    @Override
    public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void unused) {
        braced(node.getStatement());
        return super.visitEnhancedForLoop(node, unused);
    }

    @Override
    public Void visitWhileLoop(final WhileLoopTree node, final Void unused) {
        braced(node.getStatement());
        return super.visitWhileLoop(node, unused);
    }

    @Override
    public Void visitDoWhileLoop(final DoWhileLoopTree node, final Void unused) {
        braced(node.getStatement());
        return super.visitDoWhileLoop(node, unused);
    }
}
