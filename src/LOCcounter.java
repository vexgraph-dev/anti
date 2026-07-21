import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

void main() {
    Path sourceDir = Paths.get("src");
    Path outputFile = Paths.get("code.txt");

    System.out.println("Counting lines of code in: " + sourceDir.toAbsolutePath());
    int[] totalLines = {0};
    int[] totalFiles = {0};

    try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
         Stream<Path> paths = Files.walk(sourceDir)) {
        
        paths.filter(Files::isRegularFile)
             .filter(path -> {
                 String fileName = path.toString().toLowerCase();
                 String fullPath = path.toString();

                 boolean isTargetFile = fileName.endsWith(".java") ||
                         fileName.endsWith(".vert") ||
                         fileName.endsWith(".comp") ||
                         fileName.endsWith(".txt") ||
                         fileName.endsWith(".frag");

                 boolean inResourcesDir = fullPath.contains("/resources/") ||
                         fullPath.contains("\\resources\\") ||
                         fullPath.contains("/resources") ||
                         fullPath.contains("\\resources");

                 boolean isScriptFile = fileName.contains("locounter.java") ||
                         fileName.contains("filecompiler.java");

                 return isTargetFile && !inResourcesDir && !isScriptFile;
             })
             .forEach(path -> {
                 try {
                     long linesCount = Files.lines(path).count();
                     totalLines[0] += linesCount;
                     totalFiles[0]++;

                     writer.write("{\n");
                     writer.write("type: uploaded file\n");
                     writer.write("fileName: " + path.toString().replace("\\", "/") + "\n");
                     writer.write("Lines of code: " + linesCount + "\n");
                     writer.write("}\n\n");

                     System.out.println(path.getFileName() + " -> Lines of code: " + linesCount);
                 } catch (IOException e) {
                     System.err.println("Could not read file: " + path);
                 }
             });

        writer.write("========================================\n");
        writer.write("Total files: " + totalFiles[0] + "\n");
        writer.write("Total lines of code: " + totalLines[0] + "\n");

        System.out.println("\nSUCCESS! Total files: " + totalFiles[0] + ", Total lines: " + totalLines[0]);
        System.out.println("Output written to: " + outputFile.toAbsolutePath());
    } catch (IOException e) {
        System.err.println("Critical Error walking directory tree: " + e.getMessage());
    }
}