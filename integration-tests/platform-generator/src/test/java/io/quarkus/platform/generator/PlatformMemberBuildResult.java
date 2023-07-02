package io.quarkus.platform.generator;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.catalog.ExtensionCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.model.Model;

public class PlatformMemberBuildResult {

    public static PlatformMemberBuildResult load(Path memberModuleDir) {
        return new PlatformMemberBuildResult(memberModuleDir);
    }

    private final Path moduleDir;
    private String name;
    private ExtensionCatalog extensionCatalog;
    private Model bom;
    private Set<ArtifactCoords> constraints;

    private PlatformMemberBuildResult(Path moduleDir) {
        this.moduleDir = moduleDir;
    }

    public String getName() {
        if (name == null) {
            final Model model;
            try {
                model = ModelUtils.readModel(moduleDir.resolve("pom.xml"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            name = model.getName();
            if (name == null) {
                name = model.getArtifactId();
            } else if (name.startsWith("Quarkus Platform - ")) {
                name = name.substring("Quarkus Platform - ".length());
                var i = name.lastIndexOf('-');
                if (i > 0) {
                    name = name.substring(0, i - 1);
                }
            }
        }
        return name;
    }

    public boolean isCore() {
        return moduleDir.getFileName().toString().equals(PlatformGeneratorConstants.QUARKUS);
    }

    public boolean isUniverse() {
        return moduleDir.getFileName().toString().endsWith(PlatformGeneratorConstants.UNIVERSE);
    }

    public ExtensionCatalog getExtensionCatalog() {
        if (extensionCatalog == null) {
            var targetDir = moduleDir.resolve(PlatformGeneratorConstants.DESCRIPTOR).resolve("target");
            if (!Files.exists(targetDir)) {
                throw new IllegalStateException(targetDir + " does not exist");
            }
            try (Stream<Path> stream = Files.list(targetDir)) {
                var i = stream.iterator();
                while (i.hasNext()) {
                    var f = i.next();
                    if (f.getFileName().toString().endsWith(".json")) {
                        extensionCatalog = ExtensionCatalog.fromFile(f);
                        break;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (extensionCatalog == null) {
                throw new IllegalStateException("Failed to locate JSON descriptor under " + targetDir);
            }
        }
        return extensionCatalog;
    }

    public Model getBom() {
        if (bom == null) {
            var pomXml = moduleDir.resolve(PlatformGeneratorConstants.BOM).resolve("pom.xml");
            if (!Files.exists(pomXml)) {
                throw new IllegalStateException(pomXml + " does not exist");
            }
            try {
                bom = ModelUtils.readModel(pomXml);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return bom;
    }

    public String getVersion() {
        return getBom().getVersion();
    }

    public String getQuarkusCoreVersion() {
        return getExtensionCatalog().getQuarkusCoreVersion();
    }

    public boolean containsConstraint(ArtifactCoords coords) {
        if (constraints == null) {
            var dm = getBom().getDependencyManagement();
            if (dm == null) {
                constraints = Set.of();
            } else {
                constraints = new HashSet<>(dm.getDependencies().size());
                for (var d : dm.getDependencies()) {
                    constraints.add(ArtifactCoords.of(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType(),
                            d.getVersion()));
                }
            }
        }
        return constraints.contains(coords);
    }
}
