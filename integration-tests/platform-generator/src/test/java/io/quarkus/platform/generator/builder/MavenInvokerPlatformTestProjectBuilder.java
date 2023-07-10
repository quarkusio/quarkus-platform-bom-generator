package io.quarkus.platform.generator.builder;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.platform.generator.PlatformBuildResult;
import io.quarkus.platform.generator.PlatformTestProjectBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Writer;

public class MavenInvokerPlatformTestProjectBuilder implements PlatformTestProjectBuilder {

    public static MavenInvokerPlatformTestProjectBuilder getInstance() {
        return new MavenInvokerPlatformTestProjectBuilder();
    }

    private Path projectDir;
    private Path platformConfigModule;
    private Path localRepository;
    private boolean useDefaultLocalRepositoryAsRemote;

    public MavenInvokerPlatformTestProjectBuilder setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public MavenInvokerPlatformTestProjectBuilder setPlatformModule(Path projectDir) {
        this.platformConfigModule = projectDir;
        return this;
    }

    public MavenInvokerPlatformTestProjectBuilder setUseDefaultLocalRepositoryAsRemote(boolean defaultLocalRepoAsRemote) {
        this.useDefaultLocalRepositoryAsRemote = defaultLocalRepoAsRemote;
        return this;
    }

    @Override
    public PlatformBuildResult build(String... args) {

        final RunningInvoker invoker = new RunningInvoker(projectDir.toFile(), false);

        if (useDefaultLocalRepositoryAsRemote) {
            if (localRepository == null) {
                localRepository = projectDir.resolve("repository");
            }

            Path defaultSettingsXml = null;
            String quarkusMavenSettings = System.getProperty("maven.settings");
            if (quarkusMavenSettings != null) {
                defaultSettingsXml = Path.of(quarkusMavenSettings);
            } else {
                var sOption = BootstrapMavenOptions.newInstance().getOptionValue("s");
                if (sOption == null) {
                    defaultSettingsXml = Path.of(System.getProperty("user.home")).resolve(".m2").resolve("settings.xml");
                } else {
                    defaultSettingsXml = Path.of(sOption);
                }
            }

            Settings settings;
            if (Files.exists(defaultSettingsXml)) {
                try (BufferedReader reader = Files.newBufferedReader(defaultSettingsXml)) {
                    settings = new SettingsXpp3Reader().read(reader);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                settings = new Settings();
            }

            Path defaultLocalRepo;
            if (System.getProperties().contains("maven.repo.local")) {
                defaultLocalRepo = Path.of(System.getProperty("maven.repo.local"));
            } else if (settings.getLocalRepository() == null) {
                defaultLocalRepo = Path.of(System.getProperty("user.home")).resolve(".m2").resolve("repository");
            } else {
                defaultLocalRepo = Path.of(settings.getLocalRepository());
            }
            if (!Files.exists(defaultLocalRepo)) {
                throw new RuntimeException("Local Maven repository " + defaultLocalRepo + " does not exist");
            }

            final Profile profile = new Profile();
            profile.setId("qs-test-registry");
            settings.addProfile(profile);
            settings.addActiveProfile("qs-test-registry");

            final Repository repo;
            try {
                repo = configureRepo("original-local", defaultLocalRepo.toUri().toURL().toExternalForm());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            profile.addRepository(repo);
            profile.addPluginRepository(repo);

            settings.setLocalRepository(localRepository.toAbsolutePath().toString());
            try {
                Files.createDirectories(localRepository);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            var generatedSettings = localRepository.resolve("settings.xml");
            try (BufferedWriter writer = Files.newBufferedWriter(generatedSettings)) {
                new SettingsXpp3Writer().write(writer, settings);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            invoker.setSettings(generatedSettings);
            invoker.setLocalRepositoryDirectory(localRepository.toFile());
        }

        // install is the default to workaround the limitation in the workspace discovery
        // when it comes to including multiple versions of the same project in the same workspace
        var cmdArgs = args.length == 0 ? List.of("install") : List.of(args);
        final Properties props = new Properties();
        //props.setProperty("workspaceDiscovery", "true");
        try {
            invoker.execute(cmdArgs, Map.of(), props).getProcess().waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return PlatformBuildResult.load(platformConfigModule);
    }

    private static Repository configureRepo(String id, String url)
            throws MalformedURLException, BootstrapMavenException {
        final Repository repo = new Repository();
        repo.setId(id);
        repo.setLayout("default");
        repo.setUrl(url);
        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled(true);
        policy.setChecksumPolicy("ignore");
        policy.setUpdatePolicy("never");
        repo.setReleases(policy);
        repo.setSnapshots(policy);
        return repo;
    }
}
