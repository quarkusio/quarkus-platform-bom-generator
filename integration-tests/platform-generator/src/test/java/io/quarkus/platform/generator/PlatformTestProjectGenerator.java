package io.quarkus.platform.generator;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.project.MavenModuleGenerator;
import io.quarkus.maven.project.MavenPluginConfigBuilder;
import io.quarkus.platform.generator.builder.MavenInvokerPlatformTestProjectBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PlatformTestProjectGenerator {

    public static final String DEFAULT_GROUP_ID = "org.acme.quarkus.platform";
    public static final String DEFAULT_VERSION = "1.0-SNAPSHOT";
    private static final String PLATFORM_GENERATOR_GROUP_ID = "io.quarkus";
    private static final String PLATFORM_GENERATOR_ARTIFACT_ID = "quarkus-platform-bom-maven-plugin";
    private static final String PLATFORM_GENERATOR_VERSION;

    private static final String PLATFORM_CONFIG_MODULE = "quarkus-platform-config";

    static {
        var projectVersion = System.getProperty("project.version");
        if (projectVersion == null) {
            throw new IllegalStateException("System property 'project.version' is not configured");
        }
        PLATFORM_GENERATOR_VERSION = projectVersion;
    }

    public static PlatformTestProjectGenerator newInstance() {
        return new PlatformTestProjectGenerator();
    }

    public static class PlatformMemberGeneratorConfig {

        private final PlatformTestProjectGenerator platformGenerator;
        private String name;
        private ArtifactCoords inputBom;
        private ArtifactCoords generatedBom;

        private PlatformMemberGeneratorConfig(PlatformTestProjectGenerator platformGenerator) {
            this.platformGenerator = Objects.requireNonNull(platformGenerator);
        }

        public PlatformMemberGeneratorConfig setName(String name) {
            this.name = name;
            return this;
        }

        public PlatformMemberGeneratorConfig setInputBom(ArtifactCoords bom) {
            this.inputBom = bom;
            return this;
        }

        public PlatformMemberGeneratorConfig setGeneratedBom(ArtifactCoords bom) {
            this.generatedBom = bom;
            return this;
        }

        public PlatformTestProjectGenerator getPlatformGenerator() {
            return platformGenerator;
        }

        public String getName() {
            return name == null ? inputBom.getArtifactId() : name;
        }
    }

    private String groupId = DEFAULT_GROUP_ID;
    private Path projectDir;
    private String generatorVersion = PLATFORM_GENERATOR_VERSION;
    private String platformKey = DEFAULT_GROUP_ID;
    private String platformVersion = "${project.version}";
    private boolean installUniversalBom;
    private String quarkusBomVersion = DEFAULT_VERSION;
    private boolean releaseOnlyChangedMembers;

    private final MavenModuleGenerator rootPom;
    private final MavenModuleGenerator quarkusParent;
    private final MavenModuleGenerator quarkusBom;
    private final List<PlatformMemberGeneratorConfig> memberConfigs = new ArrayList<>();

    private PlatformTestProjectGenerator() {
        rootPom = MavenModuleGenerator.newMultiModuleProject("acme-quarkus-platform-parent");
        rootPom.managePlugin("org.codehaus.mojo", "flatten-maven-plugin", "1.5.0");
        rootPom.managePlugin("org.apache.maven.plugins", "maven-resources-plugin", "3.3.1");
        quarkusParent = rootPom.addPomModule("quarkus-parent")
                .setScm("https://quarkus.io", quarkusBomVersion);
        quarkusBom = configureCoreProject(quarkusParent);
    }

    public MavenModuleGenerator getQuarkusBom() {
        return quarkusBom;
    }

    public PlatformTestProjectGenerator setQuarkusCoreVersion(String quarkusVersion) {
        this.quarkusBomVersion = quarkusVersion;
        quarkusParent.setScmTag(quarkusVersion);
        return this;
    }

    public MavenModuleGenerator configureProject(String groupId, String artifactId, String version) {
        return rootPom.addPomModule(artifactId + "-" + version)
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .setVersion(version);
    }

    public PlatformMemberGeneratorConfig configureMember(ArtifactCoords inputBom) {
        var member = new PlatformMemberGeneratorConfig(this)
                .setInputBom(inputBom);
        memberConfigs.add(member);
        return member;
    }

    public PlatformTestProjectGenerator addMember(ArtifactCoords inputBom) {
        configureMember(inputBom);
        return this;
    }

    public PlatformTestProjectGenerator addMember(MavenModuleGenerator inputBom) {
        configureMember(ArtifactCoords.pom(inputBom.getGroupId(), inputBom.getArtifactId(), inputBom.getVersion()));
        return this;
    }

    public PlatformTestProjectGenerator addMember(String name, MavenModuleGenerator inputBom) {
        configureMember(ArtifactCoords.pom(inputBom.getGroupId(), inputBom.getArtifactId(), inputBom.getVersion()))
                .setName(name);
        return this;
    }

    public PlatformTestProjectGenerator setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public PlatformTestProjectGenerator setPlatformGeneratorVersion(String version) {
        generatorVersion = Objects.requireNonNull(version, "version is null");
        return this;
    }

    public PlatformTestProjectGenerator setPlatformKey(String platformKey) {
        this.platformKey = Objects.requireNonNull(platformKey, "platform key is null");
        return this;
    }

    public PlatformTestProjectGenerator setPlatformVersion(String platformVersion) {
        this.platformVersion = Objects.requireNonNull(platformVersion,
                "platform version is null");
        return this;
    }

    public PlatformTestProjectGenerator setInstallUniversalBom(boolean installUniversalBom) {
        this.installUniversalBom = installUniversalBom;
        return this;
    }

    public PlatformTestProjectGenerator setReleaseOnlyChangedMembers(boolean releaseOnlyChangedMembers) {
        this.releaseOnlyChangedMembers = releaseOnlyChangedMembers;
        return this;
    }

    public PlatformTestProjectBuilder generateProject() {

        configurePlatformGenerator(rootPom.addPomModule(PLATFORM_CONFIG_MODULE));

        rootPom.generate(projectDir);

        return MavenInvokerPlatformTestProjectBuilder.getInstance()
                .setUseDefaultLocalRepositoryAsRemote(true)
                .setProjectDir(projectDir)
                .setPlatformModule(projectDir.resolve(PLATFORM_CONFIG_MODULE));
    }

    private MavenModuleGenerator configureCoreProject(MavenModuleGenerator coreProject) {
        coreProject.setGroupId("io.quarkus").setVersion(quarkusBomVersion);
        var quarkusBom = coreProject.addPomModule("quarkus-bom");

        var quarkusCore = coreProject.addQuarkusExtensionRuntimeModule("quarkus-core");
        quarkusBom.addVersionConstraint(quarkusCore);

        var descriptor = coreProject.addPomModule("quarkus-bom-quarkus-platform-descriptor");
        quarkusBom.addVersionConstraint(descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(), "json",
                descriptor.getVersion());
        descriptor.addPlugin(PLATFORM_GENERATOR_GROUP_ID, PLATFORM_GENERATOR_ARTIFACT_ID, generatorVersion)
                .addExecution("generate-platform-descriptor")
                .setPhase("process-resources")
                .configure()
                .setParameter("bomArtifactId", "quarkus-bom")
                .setParameter("resolveDependencyManagement", "true")
                .configure("processGroupIds").setParameter("groupId", "io.quarkus");
        return quarkusBom;
    }

    private void configurePlatformGenerator(MavenModuleGenerator platformModule) {

        platformModule.setProperty("platform.groupId", platformKey);
        platformModule.setProperty("platform.version", platformVersion);

        var managedPlatformGen = platformModule.managePlugin(PLATFORM_GENERATOR_GROUP_ID, PLATFORM_GENERATOR_ARTIFACT_ID,
                generatorVersion);
        var pluginConfig = managedPlatformGen.configure();
        var platformConfig = pluginConfig.configure("platformConfig");

        var universal = platformConfig.configure("universal");
        universal.setParameter("bom", "${platform.groupId}:acme-quarkus-universe-bom:${platform.version}");
        universal.setParameter("skipInstall", installUniversalBom ? "false" : "true");

        platformModule.setProperty("quarkus-bom.version", quarkusBomVersion);
        var core = platformConfig.configure("core");
        core.setParameter("name", "Core");
        core.setParameter("bom", "io.quarkus:quarkus-bom:${quarkus-bom.version}");
        var coreRelease = core.configure("release");

        var releaseConfig = platformConfig.configure("release");
        releaseConfig.setParameter("platformKey", "${platform.groupId}");
        releaseConfig.setParameter("version", "${platform.version}");
        if (releaseOnlyChangedMembers) {
            releaseConfig.setParameter("onlyChangedMembers", "true");
            coreRelease.setParameter("next", "${platform.groupId}:quarkus-bom:${quarkus-bom.version}");
        } else {
            coreRelease.setParameter("next", "${platform.groupId}:quarkus-bom:${platform.version}");
        }

        var bomGenerator = platformConfig.configure("bomGenerator");
        var excludedDeps = bomGenerator.configure("excludedDependencies");
        excludedDeps.setParameter("dependency",
                "io.quarkus:quarkus-bom-quarkus-platform-descriptor:${quarkus-bom.version}:json");
        excludedDeps.setParameter("dependency", "io.quarkus:quarkus-bom-quarkus-platform-properties::json");

        if (!memberConfigs.isEmpty()) {
            var members = platformConfig.configure("members");
            for (PlatformMemberGeneratorConfig memberConfig : memberConfigs) {
                configureMemberModule(memberConfig, members, platformModule);
            }
        }

        var platformGen = platformModule.addManagedPlugin(PLATFORM_GENERATOR_GROUP_ID, PLATFORM_GENERATOR_ARTIFACT_ID)
                .setExtensions(true)
                .setInherited(false);
        platformGen.addExecution("generate-platform-project")
                .setId("generate-platform-project")
                .setPhase("process-resources");
        platformGen.addExecution("invoke-platform-project")
                .setId("build-platform-project")
                .setPhase("process-resources");
    }

    private void configureMemberModule(PlatformMemberGeneratorConfig memberConfig, MavenPluginConfigBuilder members,
            MavenModuleGenerator platformModule) {
        Objects.requireNonNull(memberConfig.inputBom);
        var versionProperty = memberConfig.inputBom.getArtifactId() + ".version";
        platformModule.setProperty(versionProperty, memberConfig.inputBom.getVersion());

        var member = members.configureNew("member");
        member.setParameter("name", memberConfig.getName());
        member.setParameter("bom", memberConfig.inputBom.getGroupId() + ":" + memberConfig.inputBom.getArtifactId() + ":${"
                + versionProperty + "}");

        var release = member.configure("release");
        if (releaseOnlyChangedMembers) {
            release.setParameter("next", "${platform.groupId}:quarkus-"
                    + memberConfig.inputBom.getArtifactId()
                    + ":${" + versionProperty + "}");
        } else {
            release.setParameter("next",
                    "${platform.groupId}:quarkus-" + memberConfig.inputBom.getArtifactId() + ":${platform.version}");
        }
    }
}
