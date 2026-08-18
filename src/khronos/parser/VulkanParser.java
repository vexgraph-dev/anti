package khronos.parser;

import annotation.Draft;
import annotation.Intention;
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
 * Converts XML definitions into zero-String primitive integer models and populates VulkanRegistry.ini.
 */
@Draft
@Intention("Build-time parser for vk.xml to generate primitive-ID models and VulkanRegistry.ini")
public final class VulkanParser {

    private final Path xmlPath;
    private final Path registryPath;

    // String Registry Interner (0 = "")
    private final Map<String, Integer> stringToId = new LinkedHashMap<>();
    private final List<String> idToString = new ArrayList<>();

    // Parsed Models with Primitive int IDs
    private final Map<Integer, VulkanType> types = new LinkedHashMap<>();
    private final Map<Integer, List<VulkanEnum>> enumGroups = new LinkedHashMap<>();
    private final Map<Integer, VulkanEnum> standaloneEnums = new LinkedHashMap<>();
    private final Map<Integer, VulkanStruct> structs = new LinkedHashMap<>();
    private final Map<Integer, VulkanCommand> commands = new LinkedHashMap<>();
    private final Map<Integer, VulkanExtension> extensions = new LinkedHashMap<>();

    public VulkanParser(Path xmlPath, Path registryPath) {
        this.xmlPath = xmlPath;
        this.registryPath = registryPath;
        registerString(""); // ID 0 is empty string sentinel
    }

    public int registerString(String str) {
        if (str == null) return 0;
        Integer existing = stringToId.get(str);
        if (existing != null) {
            return existing;
        }
        int id = idToString.size();
        stringToId.put(str, id);
        idToString.add(str);
        return id;
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

            int nameId = registerString(name);
            int catId = registerString(category);
            int parentId = registerString(parent);
            int aliasId = registerString(alias);

            boolean isHandle = "handle".equals(category);
            boolean isDispatchable = isHandle && !el.getTextContent().contains("VK_DEFINE_NON_DISPATCHABLE_HANDLE");
            boolean isBasetype = "basetype".equals(category);
            boolean isBitmask = "bitmask".equals(category);
            boolean isStruct = "struct".equals(category);
            boolean isUnion = "union".equals(category);
            boolean isEnum = "enum".equals(category);
            boolean isFuncpointer = "funcpointer".equals(category);

            types.put(nameId, new VulkanType(
                nameId, catId, parentId, aliasId,
                isHandle, isDispatchable, isBasetype, isBitmask, isStruct, isUnion, isEnum, isFuncpointer
            ));

            if (isStruct || isUnion) {
                parseStructMembers(el, nameId, isUnion, aliasId);
            }
        }
    }

    private void parseStructMembers(Element el, int structNameId, boolean isUnion, int aliasId) {
        if (aliasId > 0) {
            structs.put(structNameId, new VulkanStruct(structNameId, isUnion, aliasId, 0, List.of(), 0, 0));
            return;
        }

        int structExtendsId = registerString(el.getAttribute("structextends"));
        List<VulkanMember> memberList = new ArrayList<>();
        NodeList members = el.getElementsByTagName("member");

        for (int i = 0; i < members.getLength(); i++) {
            Element m = (Element) members.item(i);
            String fullText = m.getTextContent().trim();

            NodeList nameNodes = m.getElementsByTagName("name");
            String memberName = nameNodes.getLength() > 0 ? nameNodes.item(0).getTextContent().trim() : "";

            NodeList typeNodes = m.getElementsByTagName("type");
            String memberType = typeNodes.getLength() > 0 ? typeNodes.item(0).getTextContent().trim() : "";

            int memberNameId = registerString(memberName);
            int memberTypeId = registerString(memberType);

            boolean isPointer = fullText.contains("*");
            int ptrCount = (int) fullText.chars().filter(ch -> ch == '*').count();
            boolean isConst = fullText.contains("const ");
            boolean isArray = fullText.contains("[");
            int arraySize = 1;
            int arrayEnumId = 0;

            if (isArray) {
                int start = fullText.indexOf('[');
                int end = fullText.indexOf(']', start);
                if (start != -1 && end != -1) {
                    String dim = fullText.substring(start + 1, end).trim();
                    try {
                        arraySize = Integer.parseInt(dim);
                    } catch (NumberFormatException nfe) {
                        arrayEnumId = registerString(dim);
                    }
                }
            }

            int valuesId = registerString(m.getAttribute("values"));
            int lenId = registerString(m.getAttribute("len"));

            memberList.add(new VulkanMember(
                memberNameId, memberTypeId, isPointer, ptrCount, isConst,
                isArray, arraySize, arrayEnumId, 0, 0, 0, valuesId, lenId
            ));
        }

        structs.put(structNameId, new VulkanStruct(structNameId, isUnion, 0, structExtendsId, memberList, 0, 0));
    }

    private void parseEnums(Element registry) {
        NodeList enumElements = registry.getElementsByTagName("enums");
        for (int i = 0; i < enumElements.getLength(); i++) {
            Element el = (Element) enumElements.item(i);
            String groupName = el.getAttribute("name");
            int groupId = registerString(groupName);
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

                int nameId = registerString(name);
                int commentId = registerString(comment);
                int aliasOfId = registerString(alias);
                int extendsEnumId = registerString(extendsEnum);

                long numVal = 0L;
                if (!value.isEmpty()) {
                    try {
                        numVal = value.startsWith("0x") || value.startsWith("0X")
                            ? Long.parseUnsignedLong(value.substring(2), 16)
                            : Long.parseLong(value);
                    } catch (NumberFormatException ignored) {}
                } else if (!bitpos.isEmpty()) {
                    try {
                        numVal = 1L << Integer.parseInt(bitpos);
                    } catch (NumberFormatException ignored) {}
                } else if (!offset.isEmpty()) {
                    try {
                        long ext = !extNumber.isEmpty() ? Long.parseLong(extNumber) : 0L;
                        long off = Long.parseLong(offset);
                        numVal = 1000000000L + (ext - 1L) * 1000L + off;
                        if ("-".equals(dir)) numVal = -numVal;
                    } catch (NumberFormatException ignored) {}
                }

                int extNum = !extNumber.isEmpty() ? Integer.parseInt(extNumber) : 0;
                int off = !offset.isEmpty() ? Integer.parseInt(offset) : 0;

                VulkanEnum item = new VulkanEnum(
                    nameId, numVal, commentId, !alias.isEmpty(), aliasOfId, extendsEnumId,
                    extNum, off, "-".equals(dir)
                );

                if (!groupName.isEmpty() && !"API Constants".equals(groupName)) {
                    groupList.add(item);
                } else {
                    standaloneEnums.put(nameId, item);
                }
            }

            if (!groupName.isEmpty() && !"API Constants".equals(groupName)) {
                enumGroups.put(groupId, groupList);
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
                int nameId = registerString(cmdName);
                int aliasId = registerString(alias);
                commands.put(nameId, new VulkanCommand(
                    nameId, 0, List.of(), aliasId, 0, 0, 0, 0, 0, false, false, true
                ));
                continue;
            }

            Element proto = (Element) el.getElementsByTagName("proto").item(0);
            if (proto == null) continue;

            String returnType = proto.getElementsByTagName("type").item(0).getTextContent().trim();
            String name = proto.getElementsByTagName("name").item(0).getTextContent().trim();

            int nameId = registerString(name);
            int retTypeId = registerString(returnType);

            List<VulkanParam> params = new ArrayList<>();
            NodeList paramNodes = el.getElementsByTagName("param");
            for (int p = 0; p < paramNodes.getLength(); p++) {
                Element paramEl = (Element) paramNodes.item(p);
                String pText = paramEl.getTextContent().trim();

                NodeList nList = paramEl.getElementsByTagName("name");
                String pName = nList.getLength() > 0 ? nList.item(0).getTextContent().trim() : "";

                NodeList tList = paramEl.getElementsByTagName("type");
                String pType = tList.getLength() > 0 ? tList.item(0).getTextContent().trim() : "";

                int pNameId = registerString(pName);
                int pTypeId = registerString(pType);

                boolean isPtr = pText.contains("*");
                int ptrCount = (int) pText.chars().filter(ch -> ch == '*').count();
                boolean isConst = pText.contains("const ");
                boolean isOptional = "true".equals(paramEl.getAttribute("optional"));
                int lenId = registerString(paramEl.getAttribute("len"));

                params.add(new VulkanParam(pNameId, pTypeId, isPtr, ptrCount, isConst, isOptional, lenId));
            }

            String firstParamType = !params.isEmpty() ? idToString.get(params.get(0).typeId()) : "";
            boolean isInstance = "VkInstance".equals(firstParamType) || "VkPhysicalDevice".equals(firstParamType);
            boolean isDevice = "VkDevice".equals(firstParamType) || "VkQueue".equals(firstParamType) || "VkCommandBuffer".equals(firstParamType);
            boolean isGlobal = !isInstance && !isDevice;

            int successCodesId = registerString(el.getAttribute("successcodes"));
            int errorCodesId = registerString(el.getAttribute("errorcodes"));
            int queuesId = registerString(el.getAttribute("queues"));
            int renderPassId = registerString(el.getAttribute("renderpass"));
            int cmdLevelId = registerString(el.getAttribute("cmdbufferlevel"));

            commands.put(nameId, new VulkanCommand(
                nameId, retTypeId, params, 0, successCodesId, errorCodesId, queuesId,
                renderPassId, cmdLevelId, isInstance, isDevice, isGlobal
            ));
        }
    }

    private void parseExtensions(Element registry) {
        NodeList extElements = registry.getElementsByTagName("extension");
        for (int i = 0; i < extElements.getLength(); i++) {
            Element el = (Element) extElements.item(i);
            String name = el.getAttribute("name");
            String numberStr = el.getAttribute("number");
            int number = !numberStr.isEmpty() ? Integer.parseInt(numberStr) : 0;

            int nameId = registerString(name);
            int typeId = registerString(el.getAttribute("type"));
            int authorId = registerString(el.getAttribute("author"));
            int contactId = registerString(el.getAttribute("contact"));
            int platformId = registerString(el.getAttribute("platform"));
            int requiresId = registerString(el.getAttribute("requires"));
            int promotedToId = registerString(el.getAttribute("promotedto"));
            int deprecatedById = registerString(el.getAttribute("deprecatedby"));

            List<Integer> reqCmdList = new ArrayList<>();
            List<Integer> reqTypeList = new ArrayList<>();
            List<VulkanEnum> reqEnums = new ArrayList<>();

            NodeList requireNodes = el.getElementsByTagName("require");
            for (int r = 0; r < requireNodes.getLength(); r++) {
                Element req = (Element) requireNodes.item(r);

                NodeList cmds = req.getElementsByTagName("command");
                for (int c = 0; c < cmds.getLength(); c++) {
                    reqCmdList.add(registerString(((Element) cmds.item(c)).getAttribute("name")));
                }

                NodeList tps = req.getElementsByTagName("type");
                for (int t = 0; t < tps.getLength(); t++) {
                    reqTypeList.add(registerString(((Element) tps.item(t)).getAttribute("name")));
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

                    int eNameId = registerString(eName);
                    int eExtendsId = registerString(eExtends);

                    long numVal = 0L;
                    if (!eVal.isEmpty()) {
                        try {
                            numVal = eVal.startsWith("0x") || eVal.startsWith("0X")
                                ? Long.parseUnsignedLong(eVal.substring(2), 16)
                                : Long.parseLong(eVal);
                        } catch (NumberFormatException ignored) {}
                    } else if (!eBitpos.isEmpty()) {
                        try {
                            numVal = 1L << Integer.parseInt(eBitpos);
                        } catch (NumberFormatException ignored) {}
                    } else if (!eOffset.isEmpty()) {
                        try {
                            long ext = number;
                            long off = Long.parseLong(eOffset);
                            numVal = 1000000000L + (ext - 1L) * 1000L + off;
                            if ("-".equals(eDir)) numVal = -numVal;
                        } catch (NumberFormatException ignored) {}
                    }

                    int off = !eOffset.isEmpty() ? Integer.parseInt(eOffset) : 0;

                    reqEnums.add(new VulkanEnum(
                        eNameId, numVal, 0, false, 0, eExtendsId, number, off, "-".equals(eDir)
                    ));
                }
            }

            int[] reqCmds = reqCmdList.stream().mapToInt(Integer::intValue).toArray();
            int[] reqTps = reqTypeList.stream().mapToInt(Integer::intValue).toArray();

            extensions.put(nameId, new VulkanExtension(
                nameId, number, typeId, authorId, contactId, platformId, requiresId,
                promotedToId, deprecatedById, reqCmds, reqTps, reqEnums
            ));
        }
    }

    private void resolveStructLayouts() {
        for (Map.Entry<Integer, VulkanStruct> entry : structs.entrySet()) {
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

                if (currentOffset % memberAlign != 0) {
                    currentOffset += (memberAlign - (currentOffset % memberAlign));
                }

                computedMembers.add(new VulkanMember(
                    m.nameId(), m.typeId(), m.isPointer(), m.pointerCount(),
                    m.isConst(), m.isArray(), m.arraySize(), m.arrayEnumId(),
                    currentOffset, memberSize, memberAlign, m.valuesId(), m.lenId()
                ));

                if (s.isUnion()) {
                    currentOffset = Math.max(currentOffset, memberSize);
                } else {
                    currentOffset += memberSize;
                }
            }

            if (currentOffset % structAlignment != 0) {
                currentOffset += (structAlignment - (currentOffset % structAlignment));
            }

            structs.put(entry.getKey(), new VulkanStruct(
                s.nameId(), s.isUnion(), s.aliasOfId(), s.structExtendsId(),
                computedMembers, currentOffset, structAlignment
            ));
        }
    }

    private int getPrimitiveSize(VulkanMember m) {
        if (m.isPointer()) return 8;
        String typeStr = idToString.get(m.typeId());
        int base = switch (typeStr) {
            case "uint8_t", "int8_t", "char" -> 1;
            case "uint16_t", "int16_t" -> 2;
            case "uint32_t", "int32_t", "float", "VkBool32", "VkFlags" -> 4;
            case "uint64_t", "int64_t", "double", "VkDeviceSize", "VkDeviceAddress" -> 8;
            default -> {
                if (types.containsKey(m.typeId()) && types.get(m.typeId()).isHandle()) yield 8;
                if (types.containsKey(m.typeId()) && types.get(m.typeId()).isBitmask()) yield 4;
                if (enumGroups.containsKey(m.typeId())) yield 4;
                if (structs.containsKey(m.typeId()) && structs.get(m.typeId()).totalSize() > 0) {
                    yield structs.get(m.typeId()).totalSize();
                }
                yield 8;
            }
        };
        return m.isArray() ? base * m.arraySize() : base;
    }

    private int getPrimitiveAlignment(VulkanMember m) {
        if (m.isPointer()) return 8;
        String typeStr = idToString.get(m.typeId());
        return switch (typeStr) {
            case "uint8_t", "int8_t", "char" -> 1;
            case "uint16_t", "int16_t" -> 2;
            case "uint32_t", "int32_t", "float", "VkBool32", "VkFlags" -> 4;
            case "uint64_t", "int64_t", "double", "VkDeviceSize", "VkDeviceAddress" -> 8;
            default -> {
                if (types.containsKey(m.typeId()) && types.get(m.typeId()).isHandle()) yield 8;
                if (types.containsKey(m.typeId()) && types.get(m.typeId()).isBitmask()) yield 4;
                if (enumGroups.containsKey(m.typeId())) yield 4;
                if (structs.containsKey(m.typeId()) && structs.get(m.typeId()).alignment() > 0) {
                    yield structs.get(m.typeId()).alignment();
                }
                yield 8;
            }
        };
    }

    public void saveRegistry() throws IOException {
        Files.createDirectories(registryPath.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("# ==============================================================================\n");
        sb.append("# Anti Engine — Vulkan String Registry (Auto-generated by VulkanParser)\n");
        sb.append("# ==============================================================================\n");

        for (int id = 0; id < idToString.size(); id++) {
            String str = idToString.get(id);
            if (id == 0) {
                sb.append("0=\"\"\n");
            } else {
                sb.append(id).append('=').append(str).append('\n');
            }
        }

        Files.writeString(registryPath, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[VulkanParser] Wrote " + idToString.size() + " string entries to " + registryPath);
    }

    public void printSummary() {
        System.out.println("--------------------------------------------------");
        System.out.println("  Types registered:      " + types.size());
        System.out.println("  Enum Groups:           " + enumGroups.size());
        System.out.println("  Standalone Enums:      " + standaloneEnums.size());
        System.out.println("  Structs & Unions:      " + structs.size());
        System.out.println("  Commands (Functions):  " + commands.size());
        System.out.println("  Extensions:            " + extensions.size());
        System.out.println("  Unique String Pool:    " + idToString.size());
        System.out.println("--------------------------------------------------");
    }

    public Map<Integer, VulkanType> getTypes() { return types; }
    public Map<Integer, List<VulkanEnum>> getEnumGroups() { return enumGroups; }
    public Map<Integer, VulkanStruct> getStructs() { return structs; }
    public Map<Integer, VulkanCommand> getCommands() { return commands; }
    public Map<Integer, VulkanExtension> getExtensions() { return extensions; }
    public List<String> getIdToString() { return idToString; }
}
