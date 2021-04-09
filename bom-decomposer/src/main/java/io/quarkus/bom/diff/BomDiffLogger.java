package io.quarkus.bom.diff;

import io.quarkus.bom.diff.BomDiff.VersionChange;
import io.quarkus.devtools.messagewriter.MessageWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import org.eclipse.aether.graph.Dependency;

public class BomDiffLogger implements BomDiffReportGenerator {

    public static Config config() {
        return new BomDiffLogger().new Config();
    }

    public class Config {
        private Config() {
        }

        public Config logger(MessageWriter logger) {
            log = logger;
            return this;
        }

        public void report(BomDiff bomDiff) {
            BomDiffLogger.this.report(bomDiff);
        }
    }

    private MessageWriter log;
    private NumberFormat numberFormat;

    @Override
    public void report(BomDiff bomDiff) {
        log().info("Managed Dependencies Comparison Report");
        log().info("Main BOM: " + bomDiff.mainBom() + " (" + bomDiff.mainBomSize() + " managed dependencies)");
        log().info("Compared to BOM: " + bomDiff.toBom() + " (" + bomDiff.toBomSize() + " managed dependencies)");

        final int matchingTotal = bomDiff.matching().size();
        log.info("Matching dependencies: " + matchingTotal + " (" + format(matchingTotal * 100 / bomDiff.mainBomSize()) + "%)");

        if (bomDiff.hasDowngraded()) {
            final int total = bomDiff.downgraded().size();
            log().info("Downgraded dependencies: " + total + " (" + percentage(total, bomDiff.mainBomSize()) + "%)");
            for (VersionChange d : bomDiff.downgraded()) {
                log().info("  " + d.from().getArtifact() + " -> " + d.to().getArtifact().getVersion());
            }
        }

        if (bomDiff.hasUpgraded()) {
            final int total = bomDiff.upgraded().size();
            log().info("Upgraded dependencies: " + total + " (" + percentage(total, bomDiff.mainBomSize()) + "%)");
            for (VersionChange d : bomDiff.upgraded()) {
                log().info("  " + d.from().getArtifact() + " -> " + d.to().getArtifact().getVersion());
            }
        }

        if (bomDiff.hasExtra()) {
            final int total = bomDiff.extra().size();
            log().info("Extra dependencies: " + total + " (" + percentage(total, bomDiff.mainBomSize()) + "%)");
            for (Dependency d : bomDiff.extra()) {
                log().info("  " + d.getArtifact());
            }
        }

        if (bomDiff.hasMissing()) {
            final int total = bomDiff.missing().size();
            log().info("Missing dependencies: " + total + " (" + percentage(total, bomDiff.toBomSize()) + "%)");
            for (Dependency d : bomDiff.missing()) {
                log().info("  " + d.getArtifact());
            }
        }
    }

    private String percentage(long part, long whole) {
        return format(((double) part * 100) / whole);
    }

    private String format(double d) {
        return numberFormat().format(d);
    }

    private NumberFormat numberFormat() {
        if (numberFormat == null) {
            final NumberFormat numberFormat = NumberFormat.getInstance();
            numberFormat.setMaximumFractionDigits(1);
            numberFormat.setRoundingMode(RoundingMode.HALF_DOWN);
            this.numberFormat = numberFormat;
        }
        return numberFormat;
    }

    private MessageWriter log() {
        return log == null ? log = MessageWriter.debug() : log;
    }
}
