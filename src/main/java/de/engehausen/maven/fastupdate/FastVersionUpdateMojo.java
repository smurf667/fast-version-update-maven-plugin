package de.engehausen.maven.fastupdate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * <p>Wrapper plugin for the <a href="https://www.mojohaus.org/versions-maven-plugin/">Versions Maven Plugin</a>,
 * for performing updates to {@code pom.xml} files faster.
 * It does this by collecting actually declared and transitive dependencies and passing these as
 * includes and excludes, respectively. This avoids a potentially long list of checks for newer versions
 * to the transitive dependencies, which the Versions plugin apparently will look at otherwise.
 * <br>Parameters of the Mojo:</p>
 * <ul>
 *   <li>{@code goals} - the goals to execute on the Versions plugin; {@code -Dgoals=use-latest-versions,update-properties,update-parent} by default</li>
 *   <li>{@code versionsVersion} - the version of the Versions plugin; {@code -DversionsVersion=2.8.1} by default</li>
 * </ul>
 * <p>Parameters of the Versions plugin can be passed as well, e.g. {@code -DgenerateBackupPoms=false} or {@code -DrulesUri=...}</p>
 */
@Mojo(name = FastVersionUpdateMojo.NAME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FastVersionUpdateMojo extends AbstractMojo {

	/** Mojo name ({@code update}) */
	public static final String NAME = "update";
	/**
	 * {@code goals} parameter: The Versions plugin goals to invoke,
	 * {@code use-latest-versions,update-properties,update-parent} by default.
	 */
	public static final String PARAM_GOALS = "goals";
	/** {@code versionsVersion} parameter: The Versions plugin, {@code 2.8.1} by default. */
	public static final String PARAM_VERSIONS_VERSION = "versionsVersion";
	/** {@code dump} goal which will simply output the computed inclusions and exclusions. */
	public static final String GOAL_DUMP = "dump";
	/** {@code dumpFile} property to send dump to a file */
	public static final String PARAM_DUMP_FILE = "dumpFile";

	// Maven Versions plugin goals
	private static final String GOAL_UPDATE_PARENT = "update-parent";
	private static final String GOAL_UPDATE_PROPERTIES = "update-properties";
	private static final String GOAL_USE_LATEST_VERSIONS = "use-latest-versions";

	private static final String VERSIONS_PLUGIN_ARTIFACTID = "versions-maven-plugin";
	private static final String VERSIONS_PLUGIN_GROUPID = "org.codehaus.mojo";

	private static final String ELEMENT_EXCLUDES = "excludes";
	private static final String ELEMENT_EXCLUDE = "exclude";
	private static final String ELEMENT_INCLUDES = "includes";
	private static final String ELEMENT_INCLUDE = "include";

	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject mavenProject;

	@Parameter(defaultValue = "${session}", readonly = true)
	protected MavenSession mavenSession;

	@Component
	protected BuildPluginManager pluginManager;

	@Parameter(name = PARAM_VERSIONS_VERSION, property = PARAM_VERSIONS_VERSION, defaultValue = "2.8.1", required = false)
	protected String versionsVersion;

	@Parameter(name = PARAM_GOALS, property = PARAM_GOALS, defaultValue = GOAL_USE_LATEST_VERSIONS + "," + GOAL_UPDATE_PROPERTIES + "," + GOAL_UPDATE_PARENT, required = false)
	protected String goals;

	@Parameter(name = PARAM_DUMP_FILE, property = PARAM_DUMP_FILE, required = false)
	protected String dumpFile;

	protected DocumentBuilder documentBuilder;
	protected XPathExpression dependenciesExpr;
	protected XPathExpression dependencyManagementExpr;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() throws MojoExecutionException {
		if (!mavenProject.isExecutionRoot()) {
			// in multi-module builds, only process the root
			return;
		}
		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			documentBuilder = factory.newDocumentBuilder();
			final XPath xPath = XPathFactory.newInstance().newXPath();
			dependenciesExpr = xPath.compile("/project/dependencies/dependency");
			dependencyManagementExpr = xPath.compile("/project/dependencyManagement/dependencies/dependency");
		} catch (ParserConfigurationException | XPathExpressionException e) {
			throw new IllegalStateException(e);
		}

		final Set<String> transitiveDependencies = new HashSet<>();
		final Set<String> declaredDependencies = new HashSet<>();
		MavenProject current = mavenProject.getParent();
		if (current != null) {
			declaredDependencies.add(toCoordinate(current.getGroupId(), current.getArtifactId()));
			while (current != null) {
				addAllDependencies(transitiveDependencies, current.getDependencies());
				addAllDependencies(transitiveDependencies,
					Optional.ofNullable(current.getDependencyManagement())
					.map(management -> management.getDependencies())
					.orElse(null));
				current = current.getParent();
			}
		}
		collectDeclaredDependencies(declaredDependencies, mavenProject);
		final List<MavenProject> children = mavenProject.getCollectedProjects();
		if (children != null) {
			children.forEach(project -> collectDeclaredDependencies(declaredDependencies, project));
		}
		transitiveDependencies.removeAll(declaredDependencies);
		if (GOAL_DUMP.equals(goals)) {
			try {
				dump(declaredDependencies, transitiveDependencies);
			} catch (IOException e) {
				throw new MojoExecutionException("dump failed", e);
			}
		} else {
			final MojoExecutor.Element[] includeExcludeConfig = buildConfiguration(declaredDependencies, transitiveDependencies);
			final Map<String, MojoExecutor.Element[]> configurations = new HashMap<>();
			for (final String goal : new String[] {
				GOAL_USE_LATEST_VERSIONS,
				GOAL_UPDATE_PROPERTIES
			}) {
				configurations.put(goal, includeExcludeConfig);
			}
			configurations.put(GOAL_UPDATE_PROPERTIES, includeExcludeConfig);
			for (final String item : goals.split(",")) {
				final String goal = item.trim();
				runGoal(configurations.getOrDefault(goal, new MojoExecutor.Element[0]), goal);
			}
		}
	}

	protected void runGoal(final MojoExecutor.Element[] configuration, final String goal) throws MojoExecutionException {
		MojoExecutor.executeMojo(
			MojoExecutor.plugin(
				MojoExecutor.groupId(VERSIONS_PLUGIN_GROUPID),
				MojoExecutor.artifactId(VERSIONS_PLUGIN_ARTIFACTID),
				MojoExecutor.version(versionsVersion)
			),
			MojoExecutor.goal(goal),
			MojoExecutor.configuration(configuration),
			MojoExecutor.executionEnvironment(
				mavenProject,
				mavenSession,
				pluginManager)
		);
	}

	protected void dump(final Set<String> declaredDependencies, final Set<String> transitiveDependencies) throws IOException {
		final String in = "includes=" + declaredDependencies
			.stream()
			.sorted()
			.collect(Collectors.joining(","));
		final String ex = "excludes=" + transitiveDependencies
				.stream()
				.sorted()
				.collect(Collectors.joining(","));
		if (dumpFile != null) {
			final File target = new File(dumpFile);
			FileUtils.write(target, in, StandardCharsets.UTF_8, false);
			FileUtils.write(target, System.lineSeparator(), StandardCharsets.UTF_8, true);
			FileUtils.write(target, ex, StandardCharsets.UTF_8, true);
		} else {
			getLog().info(in);
			getLog().info(ex);
		}
	}

	protected MojoExecutor.Element[] buildConfiguration(final Set<String> declaredDependencies, final Set<String> transitiveDependencies) {
		final List<MojoExecutor.Element> config = new ArrayList<>();
		final String message = "== INCLUSIONS AND EXCLUSIONS ";
		getLog().debug(message + "=".repeat(80 - message.length()));
		if (!declaredDependencies.isEmpty()) {
			final MojoExecutor.Element[] includes = declaredDependencies
				.stream()
				.peek(coordinate -> logCoordinate(ELEMENT_INCLUDE, coordinate))
				.map(coordinate -> MojoExecutor.element(ELEMENT_INCLUDE, coordinate))
				.toArray(MojoExecutor.Element[]::new);
			config.add(MojoExecutor.element(ELEMENT_INCLUDES, includes));
		}
		if (!transitiveDependencies.isEmpty()) {
			final MojoExecutor.Element[] excludes = transitiveDependencies
				.stream()
				.peek(coordinate -> logCoordinate(ELEMENT_EXCLUDE, coordinate))
				.map(coordinate -> MojoExecutor.element(ELEMENT_EXCLUDE, coordinate))
				.toArray(MojoExecutor.Element[]::new);
			config.add(MojoExecutor.element(ELEMENT_EXCLUDES, excludes));
		}
		getLog().debug("=".repeat(80));
		return config.toArray(new MojoExecutor.Element[config.size()]);
	}

	protected void collectDeclaredDependencies(final Set<String> set, final MavenProject project) {
		try {
			final Document document = documentBuilder.parse(project.getFile());
			addAllDependencies(set, (NodeList) dependenciesExpr.evaluate(document, XPathConstants.NODESET));
			addAllDependencies(set, (NodeList) dependencyManagementExpr.evaluate(document, XPathConstants.NODESET));
		} catch (IOException | SAXException | XPathExpressionException e) {
			throw new IllegalStateException(e);
		}
	}

	protected void addAllDependencies(final Set<String> set, final List<Dependency> dependencies) {
		if (dependencies != null) {
			dependencies.stream()
				.map(dependency -> toCoordinate( dependency.getGroupId(), dependency.getArtifactId()))
				.forEach(coordinate -> set.add(coordinate));
		}
	}

	protected void addAllDependencies(final Set<String> set, final NodeList dependencies) {
		if (dependencies != null && dependencies.getLength() > 0) {
			for (int i = dependencies.getLength(); --i >= 0; ) {
				final Element element = (Element) dependencies.item(i);
				set.add(toCoordinate(
					toString(element, "groupId"),
					toString(element, "artifactId")
				));
			}
		}
	}

	protected void logCoordinate(final String message, final String coordinate) {
		if (getLog().isDebugEnabled()) {
			getLog().debug(String.format("%s %s", message, coordinate));
		}
	}

	private String toString(final Element element, final String tagName) {
		final NodeList list = element.getElementsByTagName(tagName);
		if (list != null && list.getLength() > 0) {
			return list.item(0).getTextContent();
		}
		return null;
	}

	private String toCoordinate(final String groupId, final String artifactId) {
		return String.format("%s:%s", groupId, artifactId);
	}

}