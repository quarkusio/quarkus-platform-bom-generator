package io.quarkus.bom.decomposer;

import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.GAV;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import org.apache.maven.model.Model;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;

public class ReleaseIdResolver {

    private final MessageWriter log;
    private final ArtifactResolver resolver;
    private final Collection<ReleaseIdDetector> releaseDetectors;
    private final boolean validateRepoTag;
    private final Map<ArtifactCoords, String> versionMapping;
    private Set<ReleaseId> validatedReleaseIds;
    private HttpClient httpClient;
    private final WeakHashMap<GAV, ReleaseId> releaseIdCache = new WeakHashMap<>();

    public ReleaseIdResolver(MavenArtifactResolver resolver) {
        this(ArtifactResolverProvider.get(resolver));
    }

    public ReleaseIdResolver(MavenArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this(ArtifactResolverProvider.get(resolver), releaseDetectors);
    }

    public ReleaseIdResolver(MavenArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors, MessageWriter log,
            boolean validateRepoTag, Map<ArtifactCoords, String> versionMapping) {
        this(ArtifactResolverProvider.get(resolver), releaseDetectors, log, validateRepoTag, versionMapping);
    }

    public ReleaseIdResolver(ArtifactResolver resolver) {
        this(resolver, List.of());
    }

    public ReleaseIdResolver(ArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this.resolver = Objects.requireNonNull(resolver);
        this.releaseDetectors = releaseDetectors;
        this.validateRepoTag = false;
        this.log = MessageWriter.info();
        this.versionMapping = Map.of();
    }

    public ReleaseIdResolver(ArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors, MessageWriter log,
            boolean validateRepoTag, Map<ArtifactCoords, String> versionMapping) {
        this.resolver = Objects.requireNonNull(resolver);
        this.releaseDetectors = releaseDetectors;
        this.validateRepoTag = validateRepoTag;
        this.log = log;
        this.versionMapping = versionMapping;
    }

    private Artifact getTargetArtifact(Artifact artifact) {
        if (versionMapping.isEmpty()) {
            return artifact;
        }
        final String v = versionMapping.get(ArtifactCoords.of(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier(), artifact.getExtension(), artifact.getVersion()));
        if (v == null) {
            return artifact;
        }
        return artifact.setVersion(v);
    }

    public ReleaseId releaseId(Artifact artifact, List<RemoteRepository> repos)
            throws BomDecomposerException, UnresolvableModelException {
        artifact = getTargetArtifact(artifact);
        var gav = new GAV(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        ReleaseId releaseId = releaseIdCache.get(gav);
        if (releaseId == null) {
            for (ReleaseIdDetector releaseDetector : releaseDetectors) {
                releaseId = releaseDetector.detectReleaseId(this, artifact);
                if (releaseId != null) {
                    break;
                }
            }
            if (releaseId == null) {
                releaseId = defaultReleaseId(artifact, repos);
            }
            if (validateRepoTag) {
                validateTag(releaseId);
            }
            releaseIdCache.put(gav, releaseId);
        }
        return releaseId;
    }

    public ReleaseId defaultReleaseId(Artifact artifact) throws BomDecomposerException {
        return defaultReleaseId(artifact, List.of());
    }

    public ReleaseId defaultReleaseId(Artifact artifact, List<RemoteRepository> repos) throws BomDecomposerException {
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

        Model model = model(artifact, repos);
        Model tmp;
        while (!hasScmInfo(model) && (tmp = workspaceParent(model, repos)) != null) {
            model = tmp;
        }
        return ReleaseIdFactory.forModel(model);
    }

    public ReleaseId validateTag(ReleaseId releaseId) {
        if (validatedReleaseIds == null) {
            validatedReleaseIds = new HashSet<>();
        }
        if (!validatedReleaseIds.add(releaseId)) {
            return releaseId;
        }
        String repoUrl = releaseId.origin().toString();
        if (!repoUrl.startsWith("https:") && !repoUrl.startsWith("http:")) {
            log.warn("Non-HTTP(s) origin " + repoUrl);
            return releaseId;
        }
        if (repoUrl.charAt(repoUrl.length() - 1) != '/') {
            repoUrl += "/";
        }
        if (repoUrl.contains("github.com")) {
            repoUrl += "releases/tag/";
        } else if (repoUrl.contains("gitlab.com")) {
            repoUrl += "-/tags/";
        }
        repoUrl += releaseId.version().asString();
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        try {
            final String tagUrl = repoUrl;
            httpClient.send(HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(repoUrl))
                    .timeout(Duration.ofSeconds(5))
                    .build(), r -> {
                        switch (r.statusCode()) {
                            case 200:
                            case 429:
                                break;
                            default:
                                log.warn("Got " + r.statusCode() + " response code validating " + tagUrl);
                        }
                        return BodySubscribers.discarding();
                    });
        } catch (Exception e) {
            log.warn("Invalid release tag " + repoUrl);
        }
        return releaseId;
    }

    private static boolean hasScmInfo(Model model) {
        return Util.getScmOrigin(model) != null;
    }

    private Model workspaceParent(Model model, List<RemoteRepository> repos) throws BomDecomposerException {
        if (model.getParent() == null) {
            return null;
        }

        final Model parentModel = model(Util.parentArtifact(model), repos);

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
        return Util.model(resolver.resolve(Util.pom(artifact)).getArtifact().getFile());
    }

    public Model model(Artifact artifact, List<RemoteRepository> repos) throws BomDecomposerException {
        return Util.model(resolver.resolve(Util.pom(artifact), repos).getArtifact().getFile());
    }
}
