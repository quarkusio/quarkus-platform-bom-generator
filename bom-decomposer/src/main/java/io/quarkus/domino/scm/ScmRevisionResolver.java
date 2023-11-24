package io.quarkus.domino.scm;

import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.ReleaseIdDetector;
import io.quarkus.bom.decomposer.ReleaseIdFactory;
import io.quarkus.bom.decomposer.Util;
import io.quarkus.bom.resolver.ArtifactResolver;
import io.quarkus.bom.resolver.ArtifactResolverProvider;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.dependency.GAV;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
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

public class ScmRevisionResolver {

    private final MessageWriter log;
    private final ArtifactResolver resolver;
    private final Collection<ReleaseIdDetector> releaseDetectors;
    private Set<ScmRevision> validatedReleaseIds;
    private HttpClient httpClient;
    private final ScmRevisionCache cache = new ScmRevisionCache();

    public ScmRevisionResolver(MavenArtifactResolver resolver) {
        this(ArtifactResolverProvider.get(resolver));
    }

    public ScmRevisionResolver(MavenArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this(ArtifactResolverProvider.get(resolver), releaseDetectors);
    }

    public ScmRevisionResolver(MavenArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors,
            MessageWriter log) {
        this(ArtifactResolverProvider.get(resolver), releaseDetectors, log);
    }

    public ScmRevisionResolver(ArtifactResolver resolver) {
        this(resolver, List.of());
    }

    public ScmRevisionResolver(ArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors) {
        this.resolver = Objects.requireNonNull(resolver);
        this.releaseDetectors = releaseDetectors;
        this.log = MessageWriter.info();
    }

    public ScmRevisionResolver(ArtifactResolver resolver, Collection<ReleaseIdDetector> releaseDetectors, MessageWriter log) {
        this.resolver = Objects.requireNonNull(resolver);
        this.releaseDetectors = releaseDetectors;
        this.log = log;
    }

    public ScmRevision resolveRevision(Artifact artifact, List<RemoteRepository> repos)
            throws BomDecomposerException, UnresolvableModelException {
        var gav = new GAV(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        var revision = cache.get(gav);
        if (revision == null) {
            for (ReleaseIdDetector releaseDetector : releaseDetectors) {
                revision = releaseDetector.detectReleaseId(this, artifact);
                if (revision != null) {
                    break;
                }
            }
            if (revision == null) {
                revision = readRevisionFromPom(artifact, repos);
                var cachedGroupIdRevision = cache.groupIdRevisions.get(artifact.getGroupId());
                if (cachedGroupIdRevision != null && cachedGroupIdRevision.getRepository().isUrl()) {
                    if (!revision.getRepository().isUrl()) {
                        revision = cachedGroupIdRevision;
                    } else if (revision.getKind().equals(ScmRevision.Kind.VERSION)
                            && !cachedGroupIdRevision.getKind().equals(ScmRevision.Kind.VERSION)
                            && revision.getValue().equals(cachedGroupIdRevision.getValue())) {
                        revision = cachedGroupIdRevision;
                    }
                }
            }
            cache.put(gav, revision);
        }
        return revision;
    }

    public ScmRevision readRevisionFromPom(Artifact artifact) throws BomDecomposerException {
        return readRevisionFromPom(artifact, List.of());
    }

    public ScmRevision readRevisionFromPom(Artifact artifact, List<RemoteRepository> repos) throws BomDecomposerException {
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

        Model model = readPom(artifact, repos);
        Model tmp;
        while (!hasScmInfo(model) && (tmp = workspaceParent(model, repos)) != null) {
            model = tmp;
        }
        return ReleaseIdFactory.forModel(model);
    }

    public ScmRevision validateTag(ScmRevision releaseId) {
        if (validatedReleaseIds == null) {
            validatedReleaseIds = new HashSet<>();
        }
        if (!validatedReleaseIds.add(releaseId)) {
            return releaseId;
        }
        String repoUrl = releaseId.getRepository().getId();
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
        repoUrl += releaseId.getValue();
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

        final Model parentModel = readPom(Util.parentArtifact(model), repos);

        final String scmOrigin = Util.getScmOrigin(model);
        if (scmOrigin != null) {
            final String scmTag = Util.getScmTag(model);
            return scmOrigin.equals(Util.getScmOrigin(parentModel))
                    && scmTag == null || scmTag.equals(Util.getScmTag(parentModel)) ? parentModel : null;
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

    public Model readPom(Artifact artifact) throws BomDecomposerException {
        return Util.model(resolver.resolve(Util.pom(artifact)).getArtifact().getFile());
    }

    public Model readPom(Artifact artifact, List<RemoteRepository> repos) throws BomDecomposerException {
        return Util.model(resolver.resolve(Util.pom(artifact), repos).getArtifact().getFile());
    }

    private static class ScmRevisionCache {
        private final Map<GAV, ScmRevision> gavRevisions = new WeakHashMap<>();
        private final Map<String, ScmRevision> groupIdRevisions = new HashMap<>();

        ScmRevision get(GAV gav) {
            return gavRevisions.get(gav);
        }

        void put(GAV gav, ScmRevision revision) {
            gavRevisions.put(gav, revision);
            groupIdRevisions.put(gav.getGroupId(), revision);
        }
    }
}
