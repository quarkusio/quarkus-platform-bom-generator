package io.quarkus.platform.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class PlatformBuildResult {

    private static final List<String> MEMBER_DIR_CONTENT = List.of(PlatformGeneratorConstants.BOM,
            PlatformGeneratorConstants.DESCRIPTOR, PlatformGeneratorConstants.PROPERTIES);

    public static PlatformBuildResult load(Path platformProjectDir) {
        platformProjectDir = ensureGeneratedPlatformProjectDir(platformProjectDir);

        var members = new HashMap<String, PlatformMemberBuildResult>();
        PlatformMemberBuildResult core = null;
        PlatformMemberBuildResult universe = null;
        try (Stream<Path> stream = Files.list(platformProjectDir)) {
            var i = stream.iterator();
            while (i.hasNext()) {
                var dir = i.next();
                if (Files.isDirectory(dir) && isMemberModule(dir)) {
                    var member = PlatformMemberBuildResult.load(dir);
                    if (member.isCore()) {
                        core = member;
                    } else if (member.isUniverse()) {
                        universe = member;
                    } else {
                        members.put(member.getName(), member);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new PlatformBuildResult(core, members, universe);
    }

    private static Path ensureGeneratedPlatformProjectDir(Path platformProjectDir) {
        if (!platformProjectDir.getFileName().toString().equals(PlatformGeneratorConstants.GENERATED_PLATFORM_PROJECT)) {
            platformProjectDir = platformProjectDir.resolve(PlatformGeneratorConstants.GENERATED_PLATFORM_PROJECT);
            if (!Files.exists(platformProjectDir)) {
                throw new IllegalArgumentException(platformProjectDir + " does not appear to be a platform project directory");
            }
        }
        return platformProjectDir;
    }

    private static boolean isMemberModule(Path dir) {
        final Set<String> listing = new HashSet<>();
        try (Stream<Path> stream = Files.list(dir)) {
            var i = stream.iterator();
            while (i.hasNext()) {
                listing.add(i.next().getFileName().toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return listing.containsAll(MEMBER_DIR_CONTENT);
    }

    private final PlatformMemberBuildResult core;
    private final PlatformMemberBuildResult universe;
    private final Map<String, PlatformMemberBuildResult> members;

    private PlatformBuildResult(PlatformMemberBuildResult core,
            Map<String, PlatformMemberBuildResult> memberResults,
            PlatformMemberBuildResult universe) {
        this.core = core;
        this.members = memberResults;
        this.universe = universe;
    }

    public PlatformMemberBuildResult getCore() {
        return core;
    }

    public Collection<PlatformMemberBuildResult> getMembers() {
        return members.values();
    }

    public PlatformMemberBuildResult getMember(String name) {
        return members.get(name);
    }

    public PlatformMemberBuildResult getUniverse() {
        return universe;
    }
}
