/*
 * #%L
 * Alta Maven Plugin
 * %%
 * Copyright (C) 2014 - 2015 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.alta;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.repository.RepositorySystem;

import com.github.veithen.alta.template.EvaluationException;
import com.github.veithen.alta.template.InvalidTemplateException;
import com.github.veithen.alta.template.Property;
import com.github.veithen.alta.template.PropertyGroup;
import com.github.veithen.alta.template.Template;
import com.github.veithen.alta.template.TemplateCompiler;

public abstract class AbstractGenerateMojo extends AbstractMojo {
    private static final TemplateCompiler<Context> templateCompiler;
    
    static {
        templateCompiler = new TemplateCompiler<Context>();
        PropertyGroup<Context,Artifact> artifactGroup = new PropertyGroup<Context,Artifact>(Artifact.class) {
            @Override
            public Artifact prepare(Context context) throws EvaluationException {
                return context.getArtifact();
            }
        };
        artifactGroup.addProperty("artifactId", new Property<Artifact>() {
            public String evaluate(Artifact artifact) {
                return artifact.getArtifactId();
            }
        });
        artifactGroup.addProperty("groupId", new Property<Artifact>() {
            public String evaluate(Artifact artifact) {
                return artifact.getGroupId();
            }
        });
        artifactGroup.addProperty("version", new Property<Artifact>() {
            public String evaluate(Artifact artifact) {
                return artifact.getVersion();
            }
        });
        artifactGroup.addProperty("file", new Property<Artifact>() {
            public String evaluate(Artifact artifact) {
                return artifact.getFile().getPath();
            }
        });
        artifactGroup.addProperty("url", new Property<Artifact>() {
            public String evaluate(Artifact artifact) {
                try {
                    return artifact.getFile().toURI().toURL().toString();
                } catch (MalformedURLException ex) {
                    throw new Error("Unexpected exception", ex);
                }
            }
        });
        templateCompiler.setDefaultPropertyGroup(artifactGroup);
        PropertyGroup<Context,Bundle> bundleGroup = new PropertyGroup<Context,Bundle>(Bundle.class) {
            @Override
            public Bundle prepare(Context context) throws EvaluationException {
                File file = context.getArtifact().getFile();
                try {
                    return extractBundleMetadata(file);
                } catch (IOException ex) {
                    throw new EvaluationException("Failed to read " + file, ex);
                }
            }
        };
        bundleGroup.addProperty("symbolicName", new Property<Bundle>() {
            public String evaluate(Bundle bundle) {
                return bundle.getSymbolicName();
            }
        });
        templateCompiler.addPropertyGroup("bundle", bundleGroup);
    }
    
    /**
     * The destination name template.
     */
    @Parameter(required=true)
    private String name;
    
    /**
     * The template of the value to generate.
     */
    @Parameter(required=true)
    private String value;
    
    /**
     * The separator that should be used to join values when multiple artifacts map to the same
     * name. If no separator is configured, then duplicate names will result in an error. To
     * separate values by newlines, use the <code>line.separator</code> property.
     */
    @Parameter
    private String separator;
    
    @Parameter
    private DependencySet dependencySet;
    
    @Parameter
    private ArtifactItem[] artifacts;
    
    @Parameter
    private Repository[] repositories;
    
    @Component
    private RepositorySystem repositorySystem;
    
    @Component
    private ArtifactFactory factory;
    
    @Component
    private ArtifactCollector artifactCollector;
    
    @Component
    private ArtifactMetadataSource artifactMetadataSource;
    
    @Component
    private ArtifactResolver resolver;

    @Parameter(readonly=true, required=true, defaultValue="${project}")
    protected MavenProject project;
    
    @Parameter(readonly=true, required=true, defaultValue="${localRepository}")
    private ArtifactRepository localRepository;
    
    public final void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        Template<Context> nameTemplate;
        try {
            nameTemplate = templateCompiler.compile(name);
        } catch (InvalidTemplateException ex) {
            throw new MojoExecutionException("Invalid destination name template", ex);
        }
        Template<Context> valueTemplate;
        try {
            valueTemplate = templateCompiler.compile(value);
        } catch (InvalidTemplateException ex) {
            throw new MojoExecutionException("Invalid value template", ex);
        }
        List<Artifact> resolvedArtifacts = new ArrayList<Artifact>();
        
        if (dependencySet != null) {
            if (log.isDebugEnabled()) {
                log.debug("Resolving project dependencies in scope " + dependencySet.getScope());
            }
            ArtifactFilter filter = new ScopeArtifactFilter(dependencySet.getScope());
            Set<Artifact> artifacts;
            try {
                artifacts = MavenMetadataSource.createArtifacts(factory, project.getDependencies(), null, filter, project);
            } catch (InvalidDependencyVersionException ex) {
                throw new MojoExecutionException("Failed to collect project dpendencies", ex);
            }
            // Note: dependencies are always resolved from the repositories declared in the POM, never
            // from repositories declared in the plugin configuration
            try {
                resolvedArtifacts.addAll(resolver.resolveTransitively(artifacts, project.getArtifact(), project.getRemoteArtifactRepositories(), localRepository, artifactMetadataSource).getArtifacts());
            } catch (ArtifactResolutionException ex) {
                throw new MojoExecutionException("Unable to resolve artifact", ex);
            } catch (ArtifactNotFoundException ex) {
                throw new MojoExecutionException("Artifact not found", ex);
            }
        }
        
        if (artifacts != null && artifacts.length != 0) {
            List<ArtifactRepository> pomRepositories = project.getRemoteArtifactRepositories();
            List<ArtifactRepository> effectiveRepositories;
            if (repositories != null && repositories.length > 0) {
                effectiveRepositories = new ArrayList<ArtifactRepository>(pomRepositories.size() + repositories.length);
                effectiveRepositories.addAll(pomRepositories);
                for (Repository repository : repositories) {
                    try {
                        effectiveRepositories.add(repositorySystem.buildArtifactRepository(repository));
                    } catch (InvalidRepositoryException ex) {
                        throw new MojoExecutionException("Invalid repository", ex);
                    }
                }
            } else {
                effectiveRepositories = pomRepositories;
            }
            for (ArtifactItem artifactItem : artifacts) {
                VersionRange version;
                try {
                    version = VersionRange.createFromVersionSpec(artifactItem.getVersion());
                } catch (InvalidVersionSpecificationException ex) {
                    throw new MojoExecutionException("Invalid version specified for artifact " + artifactItem.getGroupId() + ":" + artifactItem.getArtifactId(), ex);
                }
                Artifact artifact = factory.createDependencyArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(),
                        version, artifactItem.getType(), artifactItem.getClassifier(), Artifact.SCOPE_COMPILE);
                try {
                    // Find an appropriate version in the specified version range
                    ArtifactResolutionResult artifactResolutionResult = artifactCollector.collect(Collections.singleton(artifact), project.getArtifact(), localRepository, effectiveRepositories, artifactMetadataSource, null, Collections.EMPTY_LIST);
                    artifact = ((ResolutionNode)artifactResolutionResult.getArtifactResolutionNodes().iterator().next()).getArtifact();
                    
                    // Download the artifact
                    resolver.resolve(artifact, effectiveRepositories, localRepository);
                } catch (ArtifactResolutionException ex) {
                    throw new MojoExecutionException("Unable to resolve artifact", ex);
                } catch (ArtifactNotFoundException ex) {
                    throw new MojoExecutionException("Artifact not found", ex);
                }
                resolvedArtifacts.add(artifact);
            }
        }
        
        Map<String,String> result = new HashMap<String,String>();
        for (Artifact artifact : resolvedArtifacts) {
            if (log.isDebugEnabled()) {
                log.debug("Processing artifact " + artifact.getId());
            }
            Context context = new Context(artifact);
            try {
                String name = nameTemplate.evaluate(context);
                if (log.isDebugEnabled()) {
                    log.debug("name = " + name);
                }
                if (name == null) {
                    continue;
                }
                String value = valueTemplate.evaluate(context);
                if (log.isDebugEnabled()) {
                    log.debug("value = " + value);
                }
                if (value == null) {
                    continue;
                }
                String currentValue = result.get(name);
                if (currentValue == null) {
                    currentValue = value;
                } else if (separator == null) {
                    throw new MojoExecutionException("No separator configured");
                } else {
                    currentValue = currentValue + separator + value;
                }
                result.put(name, currentValue);
            } catch (EvaluationException ex) {
                throw new MojoExecutionException("Failed to process artifact " + artifact.getId(), ex);
            }
        }
        process(result);
    }
    
    static Bundle extractBundleMetadata(File file) throws IOException {
        Manifest manifest = null;
        InputStream in = new FileInputStream(file);
        try {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                    manifest = new Manifest(zip);
                    break;
                }
            }
        } finally {
            in.close();
        }
        if (manifest != null) {
            String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
            if (symbolicName != null) {
                int idx = symbolicName.indexOf(';');
                if (idx != -1) {
                    symbolicName = symbolicName.substring(0, idx);
                }
                return new Bundle(symbolicName.trim());
            }
        }
        return null;
    }
    
    protected abstract void process(Map<String,String> result) throws MojoExecutionException, MojoFailureException;
}
