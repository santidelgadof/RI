package practicari;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    @Test
    void testProcessUrls() {
        // Definir la ruta del directorio de índice y el directorio de documentos
        Path docsDir = Paths.get("Doc/");

        // Leer las URL desde el archivo urls.txt en el directorio de recursos de prueba
        List<String> urls = readUrlsFromFile("src/test/resources/urls/sites.url");

        // Llamar al método processUrls de la clase WebIndexer y verificar que no se lance ninguna excepción
        assertDoesNotThrow(() -> WebIndexer.processUrls(urls));

        // Verificar que se hayan creado los archivos .loc para las páginas web descargadas
        for (String url : urls) {
            String fileName = url.substring(url.lastIndexOf('/') + 1).replaceAll("^(http://|https://)", "") + ".loc";
            Path locFilePath = docsDir.resolve(fileName);
            assertTrue(Files.exists(locFilePath), "No se ha creado el archivo .loc para la URL: " + url);

            // Verificar que el contenido de los archivos .loc no esté vacío
            try {
                assertNotEquals(0, Files.size(locFilePath), "El archivo .loc está vacío para la URL: " + url);
            } catch (IOException e) {
                fail("Error al obtener el tamaño del archivo .loc para la URL: " + url + ", " + e.getMessage());
            }
        }
    }

    private List<String> readUrlsFromFile(String filePath) {
        List<String> urls = null;
        try {
            Path path = Paths.get(filePath);
            urls = Files.readAllLines(path);
        } catch (IOException e) {
            fail("Error al leer el archivo de URLs: " + e.getMessage());
        }
        return urls;
    }
}
