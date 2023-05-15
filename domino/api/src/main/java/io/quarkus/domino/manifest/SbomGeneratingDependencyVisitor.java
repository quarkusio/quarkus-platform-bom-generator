package io.quarkus.domino.manifest;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.domino.DependencyTreeVisitor;
import io.quarkus.domino.ProductInfo;
import io.quarkus.domino.ProjectDependencyConfig;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;

public class SbomGeneratingDependencyVisitor implements DependencyTreeVisitor {

    private static final String DOMINO_VALIDATE_SBOM_TREES = "domino.validate.sbom.trees";

    private final SbomGenerator.Builder sbomGenerator;
    private final PurgingDependencyTreeVisitor treeBuilder = new PurgingDependencyTreeVisitor();

    private final boolean validateSbomTrees = Boolean.parseBoolean(System.getProperty(DOMINO_VALIDATE_SBOM_TREES));
    private final TreeRecorder validatingTreeRecorder = validateSbomTrees ? new TreeRecorder() : null;
    private final ProjectDependencyConfig config;

    public SbomGeneratingDependencyVisitor(MavenArtifactResolver resolver, Path outputFile, ProjectDependencyConfig config,
            boolean enableSbomTransformers) {
        sbomGenerator = SbomGenerator.builder()
                .setArtifactResolver(resolver)
                .setOutputFile(outputFile);
        if (config.getProductInfo() != null) {
            sbomGenerator.setProductInfo(config.getProductInfo());
        }
        this.config = config;
    }

    public SbomGeneratingDependencyVisitor(MavenArtifactResolver resolver, Path outputFile, ProductInfo productInfo,
            boolean enableSbomTransformers) {
        sbomGenerator = SbomGenerator.builder()
                .setArtifactResolver(resolver)
                .setOutputFile(outputFile)
                .setProductInfo(productInfo);
        config = null;
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
        final List<VisitedComponent> rootComponents = treeBuilder.getRoots();
        sbomGenerator.setTopComponents(rootComponents);

        if (sbomGenerator.getProductInfo() == null) {
            VisitedComponent mainComponent = null;
            if (rootComponents.size() == 1) {
                mainComponent = rootComponents.get(0);
            } else if (config.getProjectBom() != null) {
                for (VisitedComponent c : rootComponents) {
                    if (c.getArtifactCoords().equals(config.getProjectBom())) {
                        mainComponent = c;
                        break;
                    }
                }
            }
            if (mainComponent == null) {
                for (VisitedComponent c : rootComponents) {
                    if (!ArtifactCoords.TYPE_POM.equals(c.getArtifactCoords().getType())) {
                        if (mainComponent == null) {
                            mainComponent = c;
                        } else {
                            mainComponent = null;
                            break;
                        }
                    }
                }
            }

            if (mainComponent != null) {
                final ProductInfo.Mutable info = ProductInfo.builder()
                        .setGroup(mainComponent.getArtifactCoords().getGroupId())
                        .setName(mainComponent.getArtifactCoords().getArtifactId())
                        .setVersion(mainComponent.getArtifactCoords().getVersion())
                        .setPurl(mainComponent.getPurl().toString())
                        .setType(Component.Type.LIBRARY.toString());
                if (sbomGenerator.getArtifactResolver() != null
                        && sbomGenerator.getArtifactResolver().getMavenContext().getWorkspace() != null) {
                    var project = sbomGenerator.getArtifactResolver().getMavenContext().getWorkspace().getProject(
                            mainComponent.getArtifactCoords().getGroupId(), mainComponent.getArtifactCoords().getArtifactId());
                    if (project != null && mainComponent.getArtifactCoords().getVersion().equals(project.getVersion())) {
                        setPomInfo(info, project);
                    }
                }
                sbomGenerator.setProductInfo(info.build());
            } else if (sbomGenerator.getArtifactResolver() != null
                    && sbomGenerator.getArtifactResolver().getMavenContext().getCurrentProject() != null) {
                var project = sbomGenerator.getArtifactResolver().getMavenContext().getCurrentProject();
                final ProductInfo.Mutable info = ProductInfo.builder()
                        .setGroup(project.getGroupId())
                        .setName(project.getArtifactId())
                        .setVersion(project.getVersion())
                        .setPurl(PurgingDependencyTreeVisitor.getPurl(project.getAppArtifact()).toString())
                        .setType(Component.Type.LIBRARY.toString());
                setPomInfo(info, project);
                sbomGenerator.setProductInfo(info.build());
            }
        }

        final Bom bom = sbomGenerator.build().generate();

        if (validatingTreeRecorder != null) {
            System.out.println("Validating recorded trees");
            validatingTreeRecorder.afterAllRoots();
            final List<String> rootBomRefs = new ArrayList<>(rootComponents.size());
            for (VisitedComponent root : rootComponents) {
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

    private static void setPomInfo(ProductInfo.Mutable productInfo, LocalProject project) {
        productInfo.setDescription(getPomElement(project, p -> p.getRawModel().getDescription()));
    }

    private static <R> R getPomElement(LocalProject project, Function<LocalProject, R> func) {
        var p = project;
        while (p != null) {
            var r = func.apply(p);
            if (r != null) {
                return r;
            }
            p = p.getLocalParent();
        }
        return null;
    }

}
