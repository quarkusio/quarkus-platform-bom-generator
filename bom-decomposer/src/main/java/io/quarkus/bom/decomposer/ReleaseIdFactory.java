package io.quarkus.bom.decomposer;

import org.apache.maven.model.Model;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class ReleaseIdFactory {
	public static ReleaseId forModel(Model model) {
		final String version = ModelUtils.getVersion(model);
		final String scmOrigin = Util.getScmOrigin(model);
		if(scmOrigin != null) {
			final String scmTag = Util.getScmTag(model);
			return scmTag.isEmpty()
					|| "HEAD".equals(scmTag)
					|| !scmTag.contains(version) // sometimes it the tag could be '1.4.x' and the version '1.4.1', etc
					? create(ReleaseOrigin.Factory.scmConnection(scmOrigin), ReleaseVersion.Factory.version(version))
					: create(ReleaseOrigin.Factory.scmConnection(scmOrigin), ReleaseVersion.Factory.tag(scmTag));
		}
		return create(ReleaseOrigin.Factory.ga(ModelUtils.getGroupId(model), model.getArtifactId()), ReleaseVersion.Factory.version(version));
	}

	public static ReleaseId create(ReleaseOrigin origin, ReleaseVersion version) {
		return new DefaultReleaseId(origin, version);
	}
}