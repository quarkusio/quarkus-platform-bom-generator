package io.quarkus.bom.decomposer;

import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.apache.maven.model.Model;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class ReleaseIdResolver {

    private final ArtifactResolver resolver;
    private Collection<ReleaseIdDetector> releaseDetectors;

    public ReleaseIdResolver(MavenArtifactResolver resolver) {
        this(ArtifactResolverProvider.get(resolver));
    }

    public ReleaseIdResolver(MavenArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this(ArtifactResolverProvider.get(resolver), releaseDetectors);
    }

    public ReleaseIdResolver(ArtifactResolver resolver) {
        this(resolver, List.of());
    }

    public ReleaseIdResolver(ArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this.resolver = Objects.requireNonNull(resolver);
        this.releaseDetectors = releaseDetectors;
    }

    public ReleaseId releaseId(Artifact artifact) throws BomDecomposerException, UnresolvableModelException {
        for (ReleaseIdDetector releaseDetector : releaseDetectors) {
            final ReleaseId releaseId = releaseDetector.detectReleaseId(this, artifact);
            if (releaseId != null) {
                return releaseId;
            }
        }

        return defaultReleaseId(artifact);
    }

    public ReleaseId defaultReleaseId(Artifact artifact) throws BomDecomposerException {
        /* @formatter:off
        final ModelSource ms = modelResolver.resolveModel(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion());

        final Model effectiveModel;
        try (InputStream is = ms.getInputStream()) {
            effectiveModel = ModelUtils.readModel(is);
        } catch (IOException e) {
            throw new BomDecomposerException("Failed to read model from " + ms.getLocation(), e);
        }

        if (effectiveModel.getScm() != null) {
            return ReleaseIdFactory.forModel(effectiveModel);
        }
        @formatter:on */

        Model model = model(artifact);
        Model tmp;
        while (!hasScmTag(model) && (tmp = workspaceParent(model)) != null) {
            model = tmp;
        }
        return ReleaseIdFactory.forModel(model);
    }

    private static boolean hasScmTag(Model model) {
        final String scmOrigin = Util.getScmOrigin(model);
        if (scmOrigin == null) {
            return false;
        }
        final String scmTag = Util.getScmTag(model);
        return !scmTag.isEmpty() && !"HEAD".equals(scmTag);
    }

    private Model workspaceParent(Model model) throws BomDecomposerException {
        if (model.getParent() == null) {
            return null;
        }

        final Model parentModel = model(Util.parentArtifact(model));

        if (Util.getScmOrigin(model) != null) {
            return Util.getScmOrigin(model).equals(Util.getScmOrigin(parentModel))
                    && Util.getScmTag(model).equals(Util.getScmTag(parentModel)) ? parentModel : null;
        }

        if (model.getParent().getRelativePath().isEmpty()) {
            return null;
        }

        if (model.getVersion() == null
                || model.getParent().getRelativePath() != null && !model.getParent().getRelativePath().startsWith("../pom.xml") // unfortunately that's the default
                || ModelUtils.getGroupId(parentModel).equals(ModelUtils.getGroupId(model))
                        && ModelUtils.getVersion(parentModel).equals(ModelUtils.getVersion(model))) {
            return parentModel;
        }

        if (parentModel.getModules().isEmpty()) {
            return null;
        }
        for (String path : parentModel.getModules()) {
            final String dirName = Paths.get(path).getFileName().toString();
            if (model.getArtifactId().contains(dirName)) {
                return parentModel;
            }
        }
        return null;
    }

    public Model model(Artifact artifact) throws BomDecomposerException {
        return Util.model(resolve(Util.pom(artifact)).getFile());
    }

    private Artifact resolve(Artifact artifact) throws BomDecomposerException {
        return resolver.resolve(artifact).getArtifact();
    }

    public static void main(String[] args) throws Exception {

        System.out.println("hello");

        ReleaseIdResolver idResolver = new ReleaseIdResolver(MavenArtifactResolver.builder().build());
        ReleaseId releaseId = idResolver.releaseId(new DefaultArtifact("io.quarkus", "quarkus-spring-api", "jar", "5.2.SP7"));
        System.out.println("RELEASE ID " + releaseId);
    }
}
