void main()
{
    Path sourceDir = Paths.get("src");
    Path outputFile = Paths.get("code.txt");

    IO.println("Scanning directory: " + sourceDir.toAbsolutePath());
    try(BufferedWriter writer = Files.newBufferedWriter(outputFile);
        Stream<Path> paths = Files.walk(sourceDir)) {
        paths.filter(Files::isRegularFile)
                .filter(path ->
                {
                    String fileName = path.toString().toLowerCase();
                    String fullPath = path.toString();

                    // target which file names
                    boolean isTargetFile = fileName.endsWith(".java") ||
                            fileName.endsWith(".vert") ||
                            fileName.endsWith(".comp") ||
                            fileName.endsWith(".txt") ||
                            fileName.endsWith(".frag");


                    // exclude files here:
                    boolean inResourcesDir = fullPath.contains("\\resources\\") ||
                            fullPath.contains("/resources/") ||
                            fullPath.contains("\\resources") ||
                            fullPath.contains("/resources");

                    return isTargetFile && !inResourcesDir;
                })
                .forEach(path ->
                {
                    try {
                        writer.write("{\n");
                        writer.write("type: uploaded file\n");
                        writer.write("fileName: " + path.toString().replace("\\", "/") + "\n");
                        writer.write("fullContent:\n");
                        Files.readAllLines(path).forEach(line ->
                        {
                            try {
                                writer.write(line + "\n");
                            }
                            catch(IOException e) {
                                System.err.println("Failed to write line for: " + path);
                            }
                        });

                        writer.write("}\n\n");
                        IO.println("Appended: " + path.getFileName());

                    }
                    catch(IOException e) {
                        System.err.println("Could not read file: " + path);
                    }
                });

        IO.println("\nSUCCESS! All codebase files compiled into -> " + outputFile.toAbsolutePath());
    }
    catch(IOException e) {
        System.err.println("Critical Error walking the directory tree: " + e.getMessage());
    }
}