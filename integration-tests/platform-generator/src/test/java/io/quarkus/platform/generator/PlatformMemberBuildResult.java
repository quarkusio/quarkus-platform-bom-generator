package io.quarkus.platform.generator;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    private Properties properties;
    private Model bom;
    private Set<ArtifactCoords> constraints;
    private String platformKey;
    private String platformStream;
    private String platformVersion;
    private Set<ArtifactCoords> members;

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

    public Properties getProperties() {
        if (properties == null) {
            var targetDir = moduleDir.resolve(PlatformGeneratorConstants.PROPERTIES).resolve("target");
            if (!Files.exists(targetDir)) {
                throw new IllegalStateException(targetDir + " does not exist");
            }
            try (Stream<Path> stream = Files.list(targetDir)) {
                var i = stream.iterator();
                while (i.hasNext()) {
                    var f = i.next();
                    if (f.getFileName().toString().endsWith(".properties")) {
                        try (BufferedReader reader = Files.newBufferedReader(f)) {
                            properties = new Properties();
                            properties.load(reader);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (properties == null) {
                throw new IllegalStateException("Failed to locate properties under " + targetDir);
            }
        }
        return properties;
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

    public Set<ArtifactCoords> getReleaseMembers() {
        if (members == null) {
            readReleaseInfo();
        }
        return members;
    }

    public String getPlatformKey() {
        if (members == null) {
            readReleaseInfo();
        }
        return platformKey;
    }

    public String getPlatformStream() {
        if (members == null) {
            readReleaseInfo();
        }
        return platformStream;
    }

    public String getPlatformVersion() {
        if (members == null) {
            readReleaseInfo();
        }
        return platformVersion;
    }

    private void readReleaseInfo() {
        var release = (Map) getExtensionCatalog().getMetadata().get("platform-release");
        if (release == null) {
            members = Set.of();
            return;
        }
        platformKey = (String) release.get("platform-key");
        platformStream = (String) release.get("stream");
        platformVersion = (String) release.get("version");
        List<String> members = (List<String>) release.get("members");
        if (members == null) {
            this.members = Set.of();
        } else {
            this.members = new HashSet<>(members.size());
            for (var s : members) {
                this.members.add(PlatformArtifacts.getBomArtifactForCatalog(ArtifactCoords.fromString(s)));
            }
        }

        if (!isUniverse()) {
            String infoProp = null;
            for (String name : getProperties().stringPropertyNames()) {
                if (name.startsWith("platform.release-info@")) {
                    infoProp = name;
                    var value = getProperties().getProperty(name);
                    var infoStr = name.substring("platform.release-info@".length());
                    var i = infoStr.indexOf('$');
                    if (i < 0) {
                        throw new IllegalStateException("Failed to locate '$' in " + name);
                    }
                    var s = infoStr.substring(0, i);
                    if (!platformKey.equals(s)) {
                        throw new IllegalStateException(
                                "Platform key " + platformKey + " from JSON does not match " + s + " from the properties");
                    }
                    var j = infoStr.indexOf('#', i + 1);
                    if (j < 0) {
                        throw new IllegalStateException("Failed to locate '#' in " + name);
                    }
                    s = infoStr.substring(i + 1, j);
                    if (!platformStream.equals(s)) {
                        throw new IllegalStateException(
                                "Platform stream " + platformStream + " from JSON does not match " + s
                                        + " from the properties");
                    }
                    s = infoStr.substring(j + 1);
                    if (!platformVersion.equals(s)) {
                        throw new IllegalStateException(
                                "Platform version " + platformVersion + " from JSON does not match " + s
                                        + " from the properties");
                    }
                    var arr = value.split(",");
                    var propMembers = new HashSet<ArtifactCoords>(arr.length);
                    for (var coords : arr) {
                        propMembers.add(ArtifactCoords.fromString(coords));
                    }
                    if (!this.members.equals(propMembers)) {
                        throw new IllegalStateException(
                                "Release members " + this.members + " from JSON do not match " + propMembers
                                        + " from the properties");
                    }
                    break;
                }
            }
            if (infoProp == null) {
                throw new IllegalStateException("Failed to locate platform release info among " + properties);
            }
        }
    }
}
