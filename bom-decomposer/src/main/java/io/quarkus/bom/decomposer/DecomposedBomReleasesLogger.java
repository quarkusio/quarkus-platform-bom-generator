package io.quarkus.bom.decomposer;

import io.quarkus.bom.decomposer.ProjectDependency.UpdateStatus;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.domino.scm.ScmRepository;
import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomReleasesLogger extends NoopDecomposedBomVisitor {

    public class Config {

        private Config() {
        }

        public Config logger(MessageWriter logger) {
            log = logger;
            return this;
        }

        public Config defaultLogLevel(Level level) {
            logLevel = level;
            return this;
        }

        public Config conflictLogLevel(Level level) {
            conflictLogLevel = level;
            return this;
        }

        public Config resolvableConflictLogLevel(Level level) {
            resolvableConflictLogLevel = level;
            return this;
        }

        public DecomposedBomReleasesLogger build() {
            return DecomposedBomReleasesLogger.this;
        }
    }

    public static Config config() {
        return new DecomposedBomReleasesLogger().new Config();
    }

    public static Config config(boolean skipOriginsWithSingleRelease) {
        return new DecomposedBomReleasesLogger(skipOriginsWithSingleRelease).new Config();
    }

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    enum Conflict {
        NONE,
        CONFLICT,
        RESOLVABLE,
    }

    public DecomposedBomReleasesLogger() {
        super();
    }

    public DecomposedBomReleasesLogger(boolean skipOriginsWithSingleRelease) {
        super(skipOriginsWithSingleRelease);
    }

    private MessageWriter log;
    private Level logLevel = Level.INFO;
    private Level conflictLogLevel;
    private Level resolvableConflictLogLevel;
    private Conflict conflict = Conflict.NONE;
    private final StringBuilder buf = new StringBuilder();
    private int originCounter;
    private int releaseCounter;
    private int artifactCounter;
    private int originWithConflictCounter;
    private int resolvableConflictCounter;
    private int unresolvableConflictCounter;

    private MessageWriter logger() {
        return log == null ? log = MessageWriter.info() : log;
    }

    private StringBuilder buf() {
        buf.setLength(0);
        return buf;
    }

    @Override
    public void enterBom(Artifact bomArtifact) {
        log(buf().append("Multi Module Project Releases Detected Among The Managed Dependencies of ").append(bomArtifact));
        if (skipOriginsWithSingleRelease) {
            log("(release origins with a single release were filtered out)");
        }
    }

    @Override
    public boolean enterReleaseOrigin(ScmRepository releaseOrigin, int versions) {
        final boolean result = super.enterReleaseOrigin(releaseOrigin, versions);
        if (result) {
            if (versions > 1) {
                conflict = Conflict.CONFLICT;
                ++originWithConflictCounter;
            }
            ++originCounter;
            log(buf().append("Origin: ").append(releaseOrigin));
        }
        return result;
    }

    @Override
    public void leaveReleaseOrigin(ScmRepository releaseOrigin) throws BomDecomposerException {
        super.leaveReleaseOrigin(releaseOrigin);
        conflict = Conflict.NONE;
    }

    @Override
    public void visitProjectRelease(ProjectRelease release) {
        ++releaseCounter;
        log("  " + release.id().getValue());
        for (ProjectDependency dep : release.dependencies()) {
            this.artifactCounter++;
            final StringBuilder buf = buf();
            buf.append("    ").append(dep);
            if (dep.isUpdateAvailable()) {
                buf.append(" -> ").append(dep.availableUpdate().artifact().getVersion());
            }
            if (dep.updateStatus() != UpdateStatus.UNKNOWN && conflict == Conflict.CONFLICT) {
                if (dep.updateStatus() == UpdateStatus.AVAILABLE) {
                    ++resolvableConflictCounter;
                    conflict = Conflict.RESOLVABLE;
                } else {
                    ++unresolvableConflictCounter;
                }
            }
            log(buf);
            if (conflict == Conflict.RESOLVABLE) {
                conflict = Conflict.CONFLICT;
            }
        }
    }

    @Override
    public void leaveBom() throws BomDecomposerException {
        if (originCounter == 0) {
            return;
        }
        Level level = totalLogLevel();
        log(level, "TOTAL");
        log(level, buf().append("  Release origins:                ").append(originCounter));
        if (originWithConflictCounter > 0) {
            log(level, buf().append("  Release origins with conflicts: ").append(originWithConflictCounter));
        }
        log(level, buf().append("  Release versions:               ").append(releaseCounter));
        log(level, buf().append("  Artifacts:                      ").append(artifactCounter));
        if (resolvableConflictCounter > 0) {
            log(level, buf().append("  Resolvable version conflicts:   ").append(resolvableConflictCounter));
        }
        if (unresolvableConflictCounter > 0) {
            log(level, buf().append("  Unresolvable version conflicts: ").append(unresolvableConflictCounter));
        }
        if (level == Level.ERROR) {
            throw new BomDecomposerException("There have been version conflicts, please refer to the messages logged above");
        }
    }

    private Level totalLogLevel() {
        Level level = resolvableConflictCounter > 0 ? resolvableConflictLogLevel : null;
        level = originWithConflictCounter > 0 ? higherLevel(conflictLogLevel, level) : level;
        return higherLevel(level, logLevel);
    }

    private Level higherLevel(Level l1, Level l2) {
        if (l1 == Level.ERROR || l2 == Level.ERROR) {
            return Level.ERROR;
        }
        if (l1 == Level.WARN || l2 == Level.WARN) {
            return Level.WARN;
        }
        if (l1 == Level.INFO || l2 == Level.INFO) {
            return Level.INFO;
        }
        return l2 == null ? l1 : l2;
    }

    private Level conflictLogLevel() {
        return conflictLogLevel == null ? logLevel : conflictLogLevel;
    }

    private Level resolvableConflictLogLevel() {
        return resolvableConflictLogLevel == null ? conflictLogLevel() : resolvableConflictLogLevel;
    }

    private void log(Object msg) {
        switch (conflict) {
            case RESOLVABLE:
                log(resolvableConflictLogLevel(), msg);
                break;
            case CONFLICT:
                log(conflictLogLevel(), msg);
                break;
            default:
                log(logLevel, msg);
        }
    }

    private void log(Level level, Object msg) {
        switch (level) {
            case DEBUG:
                logger().debug(msg == null ? "null" : msg.toString());
                break;
            case INFO:
                logger().info(msg == null ? "null" : msg.toString());
                break;
            case ERROR:
                logger().error(msg == null ? "null" : msg.toString());
                break;
            default:
                logger().warn(msg == null ? "null" : msg.toString());
        }
    }
}
