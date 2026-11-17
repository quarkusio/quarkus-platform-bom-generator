package io.quarkus.bom.decomposer.maven;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.registry.catalog.Category;
import io.quarkus.registry.catalog.ExtensionCatalog;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CategoryOverridesTest {

    /**
     * An override for a category that already exists in the inherited catalog, but is not
     * the first category there, should be merged into it rather than added as a duplicate.
     */
    @Test
    public void overrideOfExistingNonFirstCategoryIsMerged() throws Exception {
        final ExtensionCatalog.Mutable platformJson = ExtensionCatalog.builder();
        // mimics how categories inherited from imported descriptors are added
        List.of(category("web", "Web", "Web stuff", Map.of()),
                category("automation", "Automation", "Core description",
                        Map.of("pinned", List.of("io.quarkus:quarkus-bar"), "core-only", "kept")))
                .forEach(c -> platformJson.addCategory(c.mutable()));

        final ExtensionCatalog overrides = readOverrides("existing-non-first-category.json");

        GeneratePlatformDescriptorJsonMojo.applyCategoryOverrides(platformJson, overrides.getCategories());

        assertThat(platformJson.getCategories()).extracting(Category::getId).containsExactly("web", "automation");
        final Category automation = platformJson.getCategories().get(1);
        assertThat(automation.getDescription()).isEqualTo("Platform description");
        assertThat(automation.getMetadata())
                .containsEntry("pinned", List.of("io.quarkus:quarkus-foo"))
                .containsEntry("core-only", "kept");
    }

    /**
     * When there are no inherited categories, the first overrides file populates the catalog.
     * A subsequent overrides file overriding one of those categories should be merged into it,
     * rather than failing because the categories read from the first file are not mutable.
     */
    @Test
    public void secondOverridesFileCanOverrideCategoryFromFirst() throws Exception {
        final ExtensionCatalog.Mutable platformJson = ExtensionCatalog.builder();

        final ExtensionCatalog first = readOverrides("first.json");
        final ExtensionCatalog second = readOverrides("second.json");

        GeneratePlatformDescriptorJsonMojo.applyCategoryOverrides(platformJson, first.getCategories());
        GeneratePlatformDescriptorJsonMojo.applyCategoryOverrides(platformJson, second.getCategories());

        assertThat(platformJson.getCategories()).extracting(Category::getId).containsExactly("integration");
        assertThat(platformJson.getCategories().get(0).getDescription()).isEqualTo("Second description");
    }

    private static Category category(String id, String name, String description, Map<String, Object> metadata) {
        return Category.builder().setId(id).setName(name).setDescription(description).setMetadata(metadata).build();
    }

    private static ExtensionCatalog readOverrides(String fileName) throws Exception {
        return ExtensionCatalog
                .fromFile(Path.of(CategoryOverridesTest.class.getResource("/category-overrides/" + fileName).toURI()));
    }
}
