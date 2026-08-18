package khronos.parser;

import khronos.parser.model.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Fast XML parser for Khronos Vulkan Registry (vk.xml).
 * Extracts enums, bitmasks, struct layouts, command prototypes, and populates VulkanRegistry.ini.
 */
public final class VulkanParser {

    private final Path xmlPath;
    private final Path registryPath;

    // Parsed models
    private final Map<String, VulkanType> types = new LinkedHashMap<>();
    private final Map<String, List<VulkanEnum>> enumGroups = new LinkedHashMap<>();
    private final Map<String, VulkanEnum> standaloneEnums = new LinkedHashMap<>();
    private final Map<String, VulkanStruct> structs = new LinkedHashMap<>();
    private final Map<String, VulkanCommand> commands = new LinkedHashMap<>();
    private final Map<String, VulkanExtension> extensions = new LinkedHashMap<>();

    // String Registry collector
    private final Set<String> stringPool = new LinkedHashSet<>();

    public VulkanParser(Path xmlPath, Path registryPath) {
        this.xmlPath = xmlPath;
        this.registryPath = registryPath;
    }

    public static void main(String[] args) {
        Path xml = args.length > 0 ? Path.of(args[0]) : Path.of("src/khronos/xml/vk.xml");
        Path reg = args.length > 1 ? Path.of(args[1]) : Path.of("src/khronos/parser/VulkanRegistry.ini");

        System.out.println("[VulkanParser] Reading " + xml + "...");
        long start = System.currentTimeMillis();

        VulkanParser parser = new VulkanParser(xml, reg);
        try {
            parser.parse();
            parser.saveRegistry();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[VulkanParser] Parsing completed in " + elapsed + "ms");
            parser.printSummary();

            // Self-test VulkanRegistry lookup
            VulkanRegistry.boot();
            System.out.println("[VulkanRegistry] Self-test: ID 1 => \"" + VulkanRegistry.getJavaString(1) + "\", ptr=" + VulkanRegistry.getPointer(1) + ", len=" + VulkanRegistry.getLength(1));
        } catch (Exception e) {
            System.err.println("[VulkanParser] Fatal error during parsing: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void parse() throws Exception {
        if (!Files.exists(xmlPath)) {
            throw new FileNotFoundException("vk.xml not found at: " + xmlPath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlPath.toFile());
        doc.getDocumentElement().normalize();

        Element registry = doc.getDocumentElement();

        parseTypes(registry);
        parseEnums(registry);
        parseCommands(registry);
        parseExtensions(registry);
        resolveStructLayouts();
    }

    private void parseTypes(Element registry) {
        NodeList typeNodes = registry.getElementsByTagName("type");
        for (int i = 0; i < typeNodes.getLength(); i++) {
            Element el = (Element) typeNodes.item(i);
            String category = el.getAttribute("category");
            String name = el.getAttribute("name");
            String alias = el.getAttribute("alias");
            String parent = el.getAttribute("parent");

            if (name.isEmpty()) {
                NodeList nameTags = el.getElementsByTagName("name");
                if (nameTags.getLength() > 0) {
                    name = nameTags.item(0).getTextContent().trim();
                }
            }

            if (name.isEmpty()) continue;

            boolean isHandle = "handle".equals(category);
            boolean isDispatchable = isHandle && !el.getTextContent().contains("VK_DEFINE_NON_DISPATCHABLE_HANDLE");
            boolean isBasetype = "basetype".equals(category);
            boolean isBitmask = "bitmask".equals(category);
            boolean isStruct = "struct".equals(category);
            boolean isUnion = "union".equals(category);
            boolean isEnum = "enum".equals(category);
            boolean isFuncpointer = "funcpointer".equals(category);

            types.put(name, new VulkanType(
                name, category, parent, el.getTextContent(), alias,
                isHandle, isDispatchable, isBasetype, isBitmask, isStruct, isUnion, isEnum, isFuncpointer
            ));

            if (isStruct || isUnion) {
                parseStructMembers(el, name, isUnion, alias);
            }
        }
    }

    private void parseStructMembers(Element el, String structName, boolean isUnion, String alias) {
        if (!alias.isEmpty()) {
            structs.put(structName, new VulkanStruct(structName, isUnion, alias, "", List.of(), 0, 0));
            return;
        }

        String structExtends = el.getAttribute("structextends");
        List<VulkanMember> memberList = new ArrayList<>();
        NodeList members = el.getElementsByTagName("member");

        for (int i = 0; i < members.getLength(); i++) {
            Element m = (Element) members.item(i);
            String fullText = m.getTextContent().trim();

            NodeList nameNodes = m.getElementsByTagName("name");
            String memberName = nameNodes.getLength() > 0 ? nameNodes.item(0).getTextContent().trim() : "";

            NodeList typeNodes = m.getElementsByTagName("type");
            String memberType = typeNodes.getLength() > 0 ? typeNodes.item(0).getTextContent().trim() : "";

            boolean isPointer = fullText.contains("*");
            int ptrCount = (int) fullText.chars().filter(ch -> ch == '*').count();
            boolean isConst = fullText.contains("const ");
            boolean isArray = fullText.contains("[");
            int arraySize = 1;
            String arrayEnum = null;

            if (isArray) {
                int start = fullText.indexOf('[');
                int end = fullText.indexOf(']', start);
                if (start != -1 && end != -1) {
                    String dim = fullText.substring(start + 1, end).trim();
                    try {
                        arraySize = Integer.parseInt(dim);
                    } catch (NumberFormatException nfe) {
                        arrayEnum = dim;
                    }
                }
            }

            String values = m.getAttribute("values");
            String len = m.getAttribute("len");

            memberList.add(new VulkanMember(
                memberName, memberType, fullText, isPointer, ptrCount, isConst,
                isArray, arraySize, arrayEnum, 0, 0, 0, values, len
            ));
        }

        structs.put(structName, new VulkanStruct(structName, isUnion, null, structExtends, memberList, 0, 0));
    }

    private void parseEnums(Element registry) {
        NodeList enumElements = registry.getElementsByTagName("enums");
        for (int i = 0; i < enumElements.getLength(); i++) {
            Element el = (Element) enumElements.item(i);
            String groupName = el.getAttribute("name");
            List<VulkanEnum> groupList = new ArrayList<>();

            NodeList enumNodes = el.getElementsByTagName("enum");
            for (int j = 0; j < enumNodes.getLength(); j++) {
                Element e = (Element) enumNodes.item(j);
                String name = e.getAttribute("name");
                String value = e.getAttribute("value");
                String bitpos = e.getAttribute("bitpos");
                String comment = e.getAttribute("comment");
                String alias = e.getAttribute("alias");
                String extendsEnum = e.getAttribute("extends");
                String extNumber = e.getAttribute("extnumber");
                String offset = e.getAttribute("offset");
                String dir = e.getAttribute("dir");

                VulkanEnum item = new VulkanEnum(
                    name, value.isEmpty() ? null : value, bitpos.isEmpty() ? null : bitpos,
                    comment.isEmpty() ? null : comment, !alias.isEmpty(), alias,
                    extendsEnum.isEmpty() ? null : extendsEnum, extNumber.isEmpty() ? null : extNumber,
                    offset.isEmpty() ? null : offset, dir.isEmpty() ? null : dir
                );

                if (!groupName.isEmpty() && !"API Constants".equals(groupName)) {
                    groupList.add(item);
                } else {
                    standaloneEnums.put(name, item);
                }

                stringPool.add(name);
            }

            if (!groupName.isEmpty() && !"API Constants".equals(groupName)) {
                enumGroups.put(groupName, groupList);
                stringPool.add(groupName);
            }
        }
    }

    private void parseCommands(Element registry) {
        NodeList commandNodes = registry.getElementsByTagName("command");
        for (int i = 0; i < commandNodes.getLength(); i++) {
            Element el = (Element) commandNodes.item(i);
            String alias = el.getAttribute("alias");
            String cmdName = el.getAttribute("name");

            if (!alias.isEmpty()) {
                commands.put(cmdName, new VulkanCommand(
                    cmdName, "void", List.of(), alias, "", "", "", "", "", false, false, true
                ));
                stringPool.add(cmdName);
                continue;
            }

            Element proto = (Element) el.getElementsByTagName("proto").item(0);
            if (proto == null) continue;

            String returnType = proto.getElementsByTagName("type").item(0).getTextContent().trim();
            String name = proto.getElementsByTagName("name").item(0).getTextContent().trim();

            List<VulkanParam> params = new ArrayList<>();
            NodeList paramNodes = el.getElementsByTagName("param");
            for (int p = 0; p < paramNodes.getLength(); p++) {
                Element paramEl = (Element) paramNodes.item(p);
                String pText = paramEl.getTextContent().trim();

                NodeList nList = paramEl.getElementsByTagName("name");
                String pName = nList.getLength() > 0 ? nList.item(0).getTextContent().trim() : "";

                NodeList tList = paramEl.getElementsByTagName("type");
                String pType = tList.getLength() > 0 ? tList.item(0).getTextContent().trim() : "";

                boolean isPtr = pText.contains("*");
                int ptrCount = (int) pText.chars().filter(ch -> ch == '*').count();
                boolean isConst = pText.contains("const ");
                boolean isOptional = "true".equals(paramEl.getAttribute("optional"));
                String len = paramEl.getAttribute("len");

                params.add(new VulkanParam(pName, pType, pText, isPtr, ptrCount, isConst, isOptional, len));
            }

            boolean isInstance = !params.isEmpty() && ("VkInstance".equals(params.get(0).type()) || "VkPhysicalDevice".equals(params.get(0).type()));
            boolean isDevice = !params.isEmpty() && ("VkDevice".equals(params.get(0).type()) || "VkQueue".equals(params.get(0).type()) || "VkCommandBuffer".equals(params.get(0).type()));
            boolean isGlobal = !isInstance && !isDevice;

            commands.put(name, new VulkanCommand(
                name, returnType, params, null, el.getAttribute("successcodes"),
                el.getAttribute("errorcodes"), el.getAttribute("queues"), el.getAttribute("renderpass"),
                el.getAttribute("cmdbufferlevel"), isInstance, isDevice, isGlobal
            ));

            stringPool.add(name);
        }
    }

    private void parseExtensions(Element registry) {
        NodeList extElements = registry.getElementsByTagName("extension");
        for (int i = 0; i < extElements.getLength(); i++) {
            Element el = (Element) extElements.item(i);
            String name = el.getAttribute("name");
            String number = el.getAttribute("number");
            String type = el.getAttribute("type");
            String author = el.getAttribute("author");
            String contact = el.getAttribute("contact");
            String platform = el.getAttribute("platform");
            String requires = el.getAttribute("requires");
            String promotedto = el.getAttribute("promotedto");
            String deprecatedby = el.getAttribute("deprecatedby");

            List<String> reqCommands = new ArrayList<>();
            List<String> reqTypes = new ArrayList<>();
            List<VulkanEnum> reqEnums = new ArrayList<>();

            NodeList requireNodes = el.getElementsByTagName("require");
            for (int r = 0; r < requireNodes.getLength(); r++) {
                Element req = (Element) requireNodes.item(r);

                NodeList cmds = req.getElementsByTagName("command");
                for (int c = 0; c < cmds.getLength(); c++) {
                    reqCommands.add(((Element) cmds.item(c)).getAttribute("name"));
                }

                NodeList tps = req.getElementsByTagName("type");
                for (int t = 0; t < tps.getLength(); t++) {
                    reqTypes.add(((Element) tps.item(t)).getAttribute("name"));
                }

                NodeList enums = req.getElementsByTagName("enum");
                for (int e = 0; e < enums.getLength(); e++) {
                    Element enumEl = (Element) enums.item(e);
                    String eName = enumEl.getAttribute("name");
                    String eVal = enumEl.getAttribute("value");
                    String eBitpos = enumEl.getAttribute("bitpos");
                    String eExtends = enumEl.getAttribute("extends");
                    String eOffset = enumEl.getAttribute("offset");
                    String eDir = enumEl.getAttribute("dir");

                    VulkanEnum ve = new VulkanEnum(
                        eName, eVal.isEmpty() ? null : eVal, eBitpos.isEmpty() ? null : eBitpos,
                        null, false, null, eExtends.isEmpty() ? null : eExtends,
                        number, eOffset.isEmpty() ? null : eOffset, eDir.isEmpty() ? null : eDir
                    );
                    reqEnums.add(ve);
                    stringPool.add(eName);
                }
            }

            extensions.put(name, new VulkanExtension(
                name, number, type, author, contact, platform, requires, promotedto, deprecatedby,
                reqCommands, reqTypes, reqEnums
            ));

            stringPool.add(name);
        }
    }

    private void resolveStructLayouts() {
        for (Map.Entry<String, VulkanStruct> entry : structs.entrySet()) {
            VulkanStruct s = entry.getValue();
            if (s.isAlias() || s.members().isEmpty()) continue;

            int currentOffset = 0;
            int structAlignment = 1;
            List<VulkanMember> computedMembers = new ArrayList<>();

            for (VulkanMember m : s.members()) {
                int memberSize = getPrimitiveSize(m);
                int memberAlign = getPrimitiveAlignment(m);

                if (memberAlign > structAlignment) {
                    structAlignment = memberAlign;
                }

                // C padding / alignment rule: offset must be aligned to member alignment
                if (currentOffset % memberAlign != 0) {
                    currentOffset += (memberAlign - (currentOffset % memberAlign));
                }

                computedMembers.add(new VulkanMember(
                    m.name(), m.type(), m.fullDeclaration(), m.isPointer(), m.pointerCount(),
                    m.isConst(), m.isArray(), m.arraySize(), m.arraySizeEnum(),
                    currentOffset, memberSize, memberAlign, m.values(), m.len()
                ));

                if (s.isUnion()) {
                    // Unions: all members start at offset 0
                    currentOffset = Math.max(currentOffset, memberSize);
                } else {
                    currentOffset += memberSize;
                }
            }

            // Total struct size must be padded to a multiple of its alignment
            if (currentOffset % structAlignment != 0) {
                currentOffset += (structAlignment - (currentOffset % structAlignment));
            }

            structs.put(entry.getKey(), new VulkanStruct(
                s.name(), s.isUnion(), s.aliasOf(), s.structExtends(),
                computedMembers, currentOffset, structAlignment
            ));
        }
    }

    private int getPrimitiveSize(VulkanMember m) {
        if (m.isPointer()) return 8; // 64-bit pointer
        int base = switch (m.type()) {
            case "uint8_t", "int8_t", "char" -> 1;
            case "uint16_t", "int16_t" -> 2;
            case "uint32_t", "int32_t", "float", "VkBool32", "VkFlags" -> 4;
            case "uint64_t", "int64_t", "double", "VkDeviceSize", "VkDeviceAddress" -> 8;
            default -> {
                if (types.containsKey(m.type()) && types.get(m.type()).isHandle()) yield 8;
                if (types.containsKey(m.type()) && types.get(m.type()).isBitmask()) yield 4;
                if (enumGroups.containsKey(m.type())) yield 4;
                if (structs.containsKey(m.type()) && structs.get(m.type()).totalSize() > 0) {
                    yield structs.get(m.type()).totalSize();
                }
                yield 8; // Default 64-bit word
            }
        };
        return m.isArray() ? base * m.arraySize() : base;
    }

    private int getPrimitiveAlignment(VulkanMember m) {
        if (m.isPointer()) return 8;
        return switch (m.type()) {
            case "uint8_t", "int8_t", "char" -> 1;
            case "uint16_t", "int16_t" -> 2;
            case "uint32_t", "int32_t", "float", "VkBool32", "VkFlags" -> 4;
            case "uint64_t", "int64_t", "double", "VkDeviceSize", "VkDeviceAddress" -> 8;
            default -> {
                if (types.containsKey(m.type()) && types.get(m.type()).isHandle()) yield 8;
                if (types.containsKey(m.type()) && types.get(m.type()).isBitmask()) yield 4;
                if (enumGroups.containsKey(m.type())) yield 4;
                if (structs.containsKey(m.type()) && structs.get(m.type()).alignment() > 0) {
                    yield structs.get(m.type()).alignment();
                }
                yield 8;
            }
        };
    }

    public void saveRegistry() throws IOException {
        Files.createDirectories(registryPath.getParent());

        List<String> sortedStrings = new ArrayList<>(stringPool);
        Collections.sort(sortedStrings);

        StringBuilder sb = new StringBuilder();
        sb.append("# ==============================================================================\n");
        sb.append("# Anti Engine — Vulkan String Registry (Auto-generated by VulkanParser)\n");
        sb.append("# ==============================================================================\n");
        sb.append("0=\"\"\n");

        int id = 1;
        for (String str : sortedStrings) {
            if (str.isEmpty()) continue;
            sb.append(id).append('=').append(str).append('\n');
            id++;
        }

        Files.writeString(registryPath, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[VulkanParser] Wrote " + (id - 1) + " string entries to " + registryPath);
    }

    public void printSummary() {
        System.out.println("--------------------------------------------------");
        System.out.println("  Types registered:      " + types.size());
        System.out.println("  Enum Groups:           " + enumGroups.size());
        System.out.println("  Standalone Enums:      " + standaloneEnums.size());
        System.out.println("  Structs & Unions:      " + structs.size());
        System.out.println("  Commands (Functions):  " + commands.size());
        System.out.println("  Extensions:            " + extensions.size());
        System.out.println("  Unique String Pool:    " + stringPool.size());
        System.out.println("--------------------------------------------------");
    }

    public Map<String, VulkanType> getTypes() { return types; }
    public Map<String, List<VulkanEnum>> getEnumGroups() { return enumGroups; }
    public Map<String, VulkanStruct> getStructs() { return structs; }
    public Map<String, VulkanCommand> getCommands() { return commands; }
    public Map<String, VulkanExtension> getExtensions() { return extensions; }
}
