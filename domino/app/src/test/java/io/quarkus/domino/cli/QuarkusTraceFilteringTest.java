package io.quarkus.domino.cli;

import io.quarkus.domino.cli.Quarkus.TreeNode;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.util.Set;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class QuarkusTraceFilteringTest {

    private static TreeNode node(String artifactId, boolean matched) {
        return new TreeNode(new DefaultArtifact("org.test", artifactId, "", "jar", "1.0"), matched);
    }

    private static ArtifactCoords coords(String artifactId) {
        return ArtifactCoords.of("org.test", artifactId, "", "jar", "1.0");
    }

    @Test
    public void matchedRootIsSource() {
        // root itself matches the pattern
        var root = node("root", true);
        Assertions.assertTrue(Quarkus.isSourceRoot(root, Set.of()));
    }

    @Test
    public void directPathToMatchIsSource() {
        // root → lib → matched (no top-level intermediates)
        var root = node("root", false);
        var lib = node("lib", false);
        var matched = node("matched", true);
        lib.addChild(matched);
        root.addChild(lib);

        Assertions.assertTrue(Quarkus.isSourceRoot(root, Set.of(coords("root"))));
    }

    @Test
    public void pathThroughTopLevelIsNotSource() {
        // root → top-level → matched
        var root = node("root", false);
        var topLevel = node("top-level", false);
        var matched = node("matched", true);
        topLevel.addChild(matched);
        root.addChild(topLevel);

        Assertions.assertFalse(Quarkus.isSourceRoot(root, Set.of(coords("root"), coords("top-level"))));
    }

    @Test
    public void mixedPathsIsSource() {
        // root → top-level → matched (blocked)
        // root → lib → matched (direct)
        var root = node("root", false);

        var topLevel = node("top-level", false);
        var matched1 = node("matched1", true);
        topLevel.addChild(matched1);
        root.addChild(topLevel);

        var lib = node("lib", false);
        var matched2 = node("matched2", true);
        lib.addChild(matched2);
        root.addChild(lib);

        Assertions.assertTrue(Quarkus.isSourceRoot(root, Set.of(coords("root"), coords("top-level"))));
    }

    @Test
    public void noMatchIsNotSource() {
        // root → lib (no matched descendants)
        var root = node("root", false);
        var lib = node("lib", false);
        root.addChild(lib);

        Assertions.assertFalse(Quarkus.isSourceRoot(root, Set.of()));
    }

    @Test
    public void deepChainWithoutTopLevelIsSource() {
        // root → a → b → c → matched
        var root = node("root", false);
        var a = node("a", false);
        var b = node("b", false);
        var c = node("c", false);
        var matched = node("matched", true);
        c.addChild(matched);
        b.addChild(c);
        a.addChild(b);
        root.addChild(a);

        Assertions.assertTrue(Quarkus.isSourceRoot(root, Set.of(coords("root"))));
    }

    @Test
    public void multipleTopLevelsOnPathIsNotSource() {
        // root → top1 → top2 → matched
        var root = node("root", false);
        var top1 = node("top1", false);
        var top2 = node("top2", false);
        var matched = node("matched", true);
        top2.addChild(matched);
        top1.addChild(top2);
        root.addChild(top1);

        var topLevels = Set.of(coords("root"), coords("top1"), coords("top2"));
        Assertions.assertFalse(Quarkus.isSourceRoot(root, topLevels));
    }

    @Test
    public void singleSourceOnPath() {
        // root → source → matched
        var root = node("root", false);
        var source = node("source", false);
        var matched = node("matched", true);
        source.addChild(matched);
        root.addChild(source);

        var sources = Quarkus.getSourcesOnPath(root, Set.of(coords("source")));
        Assertions.assertEquals(1, sources.size());
        Assertions.assertTrue(sources.contains(coords("source")));
    }

    @Test
    public void multipleSourcesOnPath() {
        // root → source1 → matched1
        // root → source2 → matched2
        var root = node("root", false);

        var source1 = node("source1", false);
        var matched1 = node("matched1", true);
        source1.addChild(matched1);
        root.addChild(source1);

        var source2 = node("source2", false);
        var matched2 = node("matched2", true);
        source2.addChild(matched2);
        root.addChild(source2);

        var sources = Quarkus.getSourcesOnPath(root, Set.of(coords("source1"), coords("source2")));
        Assertions.assertEquals(2, sources.size());
        Assertions.assertTrue(sources.contains(coords("source1")));
        Assertions.assertTrue(sources.contains(coords("source2")));
    }

    @Test
    public void chainedDependentsToSource() {
        // root → dependent → source → matched
        // dependent is NOT in sourceCoords, so traversal continues past it
        var root = node("root", false);
        var dependent = node("dependent", false);
        var source = node("source", false);
        var matched = node("matched", true);
        source.addChild(matched);
        dependent.addChild(source);
        root.addChild(dependent);

        var sources = Quarkus.getSourcesOnPath(root, Set.of(coords("source")));
        Assertions.assertEquals(1, sources.size());
        Assertions.assertTrue(sources.contains(coords("source")));
    }

    @Test
    public void noSourcesOnPath() {
        // root → lib → matched (lib is not a source)
        var root = node("root", false);
        var lib = node("lib", false);
        var matched = node("matched", true);
        lib.addChild(matched);
        root.addChild(lib);

        var sources = Quarkus.getSourcesOnPath(root, Set.of());
        Assertions.assertTrue(sources.isEmpty());
    }
}
