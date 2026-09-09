package zone.vao.claimo;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class ClaimoLoader implements PluginLoader {

    private static final String LIBRARIES = "libraries.txt";
    private static final String CENTRAL = "https://maven-central.storage-download.googleapis.com/maven2";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", central()).build());
        for (String coordinates : libraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        classpathBuilder.addLibrary(resolver);
    }

    private String central() {
        try {
            return MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR;
        } catch (NoSuchFieldError error) {
            return CENTRAL;
        }
    }

    private List<String> libraries() {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(LIBRARIES);
        if (resource == null) {
            throw new IllegalStateException("Claimo is missing its " + LIBRARIES + " resource.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read " + LIBRARIES + ".", exception);
        }
    }
}
