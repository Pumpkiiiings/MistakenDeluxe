package liric.mistaken;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class MistakenPluginLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        // Repositorios
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());
        resolver.addRepository(new RemoteRepository.Builder("papermc", "default", "https://repo.papermc.io/repository/maven-public/").build());
        resolver.addRepository(new RemoteRepository.Builder("triumphteam", "default", "https://repo.triumphteam.dev/repository/maven-releases/").build());
        resolver.addRepository(new RemoteRepository.Builder("codemc", "default", "https://repo.codemc.io/repository/maven-public/").build());

        // Kotlin
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.0.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1"), null));

        // Bases de datos
        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:5.1.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.mysql:mysql-connector-j:9.7.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.postgresql:postgresql:42.7.13"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.h2database:h2:2.2.224"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.45.3.0"), null));

        // Librerías de Bukkit
        resolver.addDependency(new Dependency(new DefaultArtifact("dev.triumphteam:triumph-gui:3.1.13"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("fr.skytasul:glowingentities:1.4.11"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.mojang:brigadier:1.2.9"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.slf4j:slf4j-simple:2.0.18"), null));

        classpathBuilder.addLibrary(resolver);
    }
}
