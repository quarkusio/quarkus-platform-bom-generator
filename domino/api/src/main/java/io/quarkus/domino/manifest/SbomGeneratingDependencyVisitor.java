package io.quarkus.domino.manifest;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.domino.DependencyTreeVisitor;
import io.quarkus.domino.ProductInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cyclonedx.model.Bom;

public class SbomGeneratingDependencyVisitor implements DependencyTreeVisitor {

    private static final String DOMINO_VALIDATE_SBOM_TREES = "domino.validate.sbom.trees";

    private final SbomGenerator.Builder sbomGenerator;
    private final PurgingDependencyTreeVisitor treeBuilder;

    private final boolean validateSbomTrees;
    private final TreeRecorder validatingTreeRecorder;

    public SbomGeneratingDependencyVisitor(MavenArtifactResolver resolver, Path outputFile, ProductInfo productInfo,
            boolean enableSbomTransformers) {
        sbomGenerator = SbomGenerator.builder()
                .setArtifactResolver(resolver)
                .setOutputFile(outputFile)
                .setProductInfo(productInfo);
        treeBuilder = new PurgingDependencyTreeVisitor();
        validateSbomTrees = Boolean.parseBoolean(System.getProperty(DOMINO_VALIDATE_SBOM_TREES));
        validatingTreeRecorder = validateSbomTrees ? new TreeRecorder() : null;
    }

    @Override
    public void beforeAllRoots() {
        treeBuilder.beforeAllRoots();
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.beforeAllRoots();
        }
    }

    @Override
    public void afterAllRoots() {
        treeBuilder.afterAllRoots();
        sbomGenerator.setTopComponents(treeBuilder.getRoots());
        final Bom bom = sbomGenerator.build().generate();

        if (validatingTreeRecorder != null) {
            System.out.println("Validating recorded trees");
            validatingTreeRecorder.afterAllRoots();
            final List<String> rootBomRefs = new ArrayList<>(treeBuilder.getRoots().size());
            for (VisitedComponent root : treeBuilder.getRoots()) {
                if (root.getBomRef() == null) {
                    throw new IllegalStateException("bom-ref is missing for " + root.getArtifactCoords().toCompactCoords());
                }
                rootBomRefs.add(root.getBomRef());
            }
            var originalRoots = validatingTreeRecorder.getRoots();
            final Map<String, TreeNode> bomRoots = new HashMap<>(originalRoots.size());
            SbomDependencyTreeReader.readTrees(bom, rootBomRefs).forEach(n -> bomRoots.put(n.name, n));
            for (TreeNode originalRoot : originalRoots) {
                System.out.println("Validating dependencies of " + originalRoot.name);
                var bomRoot = bomRoots.get(originalRoot.name);
                if (bomRoot == null) {
                    throw new IllegalStateException(
                            "Failed to locate " + originalRoot.name + " among the SBOM components: " + bomRoots.keySet());
                }
                originalRoot.isIdentical(bomRoot);
            }
        }
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        treeBuilder.enterRootArtifact(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.enterRootArtifact(visit);
        }
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        treeBuilder.leaveRootArtifact(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.leaveRootArtifact(visit);
        }
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        treeBuilder.enterDependency(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.enterDependency(visit);
        }
    }

    @Override
    public void leaveDependency(DependencyVisit visit) {
        treeBuilder.leaveDependency(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.leaveDependency(visit);
        }
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
        treeBuilder.enterParentPom(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.enterParentPom(visit);
        }
    }

    @Override
    public void leaveParentPom(DependencyVisit visit) {
        treeBuilder.leaveParentPom(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.leaveParentPom(visit);
        }
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
        treeBuilder.enterBomImport(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.enterBomImport(visit);
        }
    }

    @Override
    public void leaveBomImport(DependencyVisit visit) {
        treeBuilder.leaveBomImport(visit);
        if (validatingTreeRecorder != null) {
            validatingTreeRecorder.leaveBomImport(visit);
        }
    }
}
