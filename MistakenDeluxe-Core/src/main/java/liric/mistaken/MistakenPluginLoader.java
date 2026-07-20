package liric.mistaken;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import org.jetbrains.annotations.NotNull;
import revxrsal.zapper.DependencyManager;
import revxrsal.zapper.classloader.URLClassLoaderWrapper;
import revxrsal.zapper.util.ClassLoaderReader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Scanner;
import revxrsal.zapper.relocation.Relocation;
import revxrsal.zapper.repository.Repository;

@SuppressWarnings("UnstableApiUsage")
public class MistakenPluginLoader implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        URLClassLoaderWrapper wrapper = new URLClassLoaderWrapper() {
            @Override
            public void addURL(@NotNull URL url) {
                try {
                    classpathBuilder.addLibrary(new JarLibrary(Path.of(url.toURI())));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        try {
            File dataFolder = new File("plugins/Mistaken");
            dataFolder.mkdirs();
            File libraries = new File(dataFolder, "libraries-v3");
            if (!libraries.exists()) {
                System.out.println("[Mistaken] Downloading dependencies... Please wait a few seconds.");
            }

            DependencyManager dependencyManager = new DependencyManager(libraries, wrapper);

            try (InputStream stream = ClassLoaderReader.getResource("zapper/repositories.txt")) {
                if (stream != null) {
                    try (Scanner scanner = new Scanner(stream)) {
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty()) {
                                dependencyManager.repository(Repository.maven(line));
                            }
                        }
                    }
                }
            }

            try (InputStream stream = ClassLoaderReader.getResource("zapper/dependencies.txt")) {
                if (stream != null) {
                    try (Scanner scanner = new Scanner(stream)) {
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty()) {
                                dependencyManager.dependency(line);
                            }
                        }
                    }
                }
            }

            try (InputStream stream = ClassLoaderReader.getResource("zapper/relocations.txt")) {
                if (stream != null) {
                    try (Scanner scanner = new Scanner(stream)) {
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty() && line.contains(":")) {
                                String[] parts = line.split(":");
                                dependencyManager.relocate(new Relocation(parts[0], parts[1]));
                            }
                        }
                    }
                }
            }

            dependencyManager.load();
            System.out.println("[Mistaken] Dependencies loaded successfully via PluginLoader.");
        } catch (Throwable t) {
            System.out.println("[Mistaken] Failed to load dependencies via Zapper!");
            t.printStackTrace();
        }
    }
}
