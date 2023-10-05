package io.quarkus.domino;

import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.SimpleDependencyGraphTransformationContext;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.gradle.GradleActionOutcome;
import io.quarkus.domino.gradle.GradleDependency;
import io.quarkus.domino.gradle.GradleModuleDependencies;
import io.quarkus.domino.gradle.GradleProjectDependencies;
import io.quarkus.domino.gradle.GradleProjectDependencyResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictIdSorter;
import org.eclipse.aether.util.graph.transformer.ConflictMarker;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

public class GradleProjectReader {

    public static Map<ArtifactCoords, DependencyNode> resolveModuleDependencies(Path projectDir,
            boolean java8, String javaHome, MavenArtifactResolver mavenResolver, MessageWriter log) {
        final Collection<GradleModuleDependencies> modules = resolveDirtyTrees(projectDir, java8, javaHome, log);
        final Map<ArtifactCoords, DependencyNode> result = new HashMap<>(modules.size());
        for (GradleModuleDependencies module : modules) {
            final DependencyNode moduleNode = createNode(new Dependency(
                    new DefaultArtifact(module.getGroup(), module.getName(), ArtifactCoords.TYPE_JAR, module.getVersion()),
                    "runtime"));
            moduleNode.setChildren(toMavenDeps(module.getDependencies()));
            try {
                final DependencyNode node = converge(mavenResolver.getSession(), moduleNode);
                final Artifact a = node.getArtifact();
                final ArtifactCoords coords = ArtifactCoords.of(a.getGroupId(), a.getArtifactId(),
                        a.getClassifier(), a.getExtension(), a.getVersion());
                result.put(coords, node);
                //log(node, 0);
            } catch (AppModelResolverException e) {
                throw new RuntimeException("Failed to converge Gradle dependency tree for module " + module, e);
            }
        }
        return result;
    }

    private static Collection<GradleModuleDependencies> resolveDirtyTrees(Path projectDir,
            boolean java8,
            String javaHome,
            MessageWriter log) {
        log.debug("Loading project %s", projectDir);
        final ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir.toFile())
                //.useGradleVersion(gradleVersion)
                .connect();
        final Path dominoInitScript = generateDominoInitScript(projectDir);
        try {
            final BuildActionExecuter<GradleProjectDependencies> actionExecuter = connection
                    .action(new GradleProjectDependencyResolver())
                    .withArguments("--init-script=" + dominoInitScript, "-PskipAndroid=true")
                    .setStandardOutput(log == null ? System.out : new MessageWriterOutputStream(log));

            if ((javaHome == null || javaHome.isEmpty()) && java8) {
                javaHome = System.getenv("JAVA8_HOME");
                if (javaHome == null) {
                    throw new IllegalArgumentException(
                            "Gradle Java 8 option was enabled but JAVA8_HOME environment variable was not set");
                }
            }
            if (javaHome != null && !javaHome.isEmpty()) {
                final File jh = new File(javaHome);
                if (!jh.isDirectory()) {
                    throw new IllegalArgumentException("Provided Java home directory " + jh + " does not exist");
                }
                actionExecuter.setJavaHome(jh);
            }
            final GradleActionOutcome<GradleProjectDependencies> outcome = GradleActionOutcome.of();
            actionExecuter.run(outcome);
            return outcome.getResult().getModules();
        } finally {
            connection.close();
            dominoInitScript.toFile().deleteOnExit();
        }
    }

    private static Path generateDominoInitScript(Path projectDir) {
        final Path dominoInitScript = projectDir.resolve("domino-init.gradle");
        try (PrintStream out = new PrintStream(Files.newOutputStream(dominoInitScript))) {
            out.println("initscript {");
            out.println("    repositories {");
            out.println("        mavenCentral()");
            out.println("        mavenLocal()");
            out.println("        maven { url 'https://repo.gradle.org/gradle/libs-releases' }");
            out.println("    }");
            out.println("    dependencies {");
            out.println(
                    "        classpath \"io.quarkus.domino:io.quarkus.domino.gradle.plugin:" + DominoInfo.VERSION + "\"");
            out.println("    }");
            out.println("}");
            out.println("allprojects {");
            out.println("    apply plugin: io.quarkus.domino.gradle.DependencyPlugin");
            out.println("}");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist Gradle init script " + dominoInitScript, e);
        }
        return dominoInitScript;
    }

    private static void log(DependencyNode node, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) {
            sb.append("  ");
        }
        if (depth == 0) {
            sb.append("MODULE: ");
        }
        sb.append(node.getArtifact());
        System.out.println(sb);
        sb = null;
        for (DependencyNode c : node.getChildren()) {
            log(c, depth + 1);
        }
    }

    private static DependencyNode converge(RepositorySystemSession session, DependencyNode root)
            throws AppModelResolverException {
        final DependencyGraphTransformationContext context = new SimpleDependencyGraphTransformationContext(session);
        try {
            // add conflict IDs to the added deployments
            root = new ConflictMarker().transformGraph(root, context);
            // resolves version conflicts
            root = new ConflictIdSorter().transformGraph(root, context);
            root = session.getDependencyGraphTransformer().transformGraph(root, context);
        } catch (RepositoryException e) {
            throw new AppModelResolverException("Failed to normalize the dependency graph", e);
        }
        return root;
    }

    private static List<DependencyNode> toMavenDeps(List<GradleDependency> deps) {
        final List<DependencyNode> result = new ArrayList<>(deps.size());
        for (GradleDependency d : deps) {
            final DefaultDependencyNode node = createNode(new Dependency(
                    new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType(), d.getVersion()),
                    "runtime"));
            result.add(node);
            node.setChildren(toMavenDeps(d.getDependencies()));
        }
        return result;
    }

    private static DefaultDependencyNode createNode(Dependency dep) {
        final DefaultDependencyNode node = new DefaultDependencyNode(dep);
        final MavenArtifactVersion v = new MavenArtifactVersion(dep.getArtifact().getVersion());
        node.setVersion(v);
        node.setVersionConstraint(new MavenArtifactVersionConstraint(v));
        return node;
    }
}
