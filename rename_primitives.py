import os
import re

dir_path = "src/primitive"
for file in os.listdir(dir_path):
    if not file.endswith(".java") or file == "Bool.java":
        continue
    filepath = os.path.join(dir_path, file)
    with open(filepath, "r") as f:
        content = f.read()
    
    primitive = file[:-5] # e.g. Int, Float
    if primitive == "string":
        primitive = "String"
    
    # 1. get/set -> getPrimitive / setPrimitive
    # Look for "public static <type> get("
    content = re.sub(r'public static ([\w<>]+) get\(', f'public static \\1 get{primitive}(', content)
    content = re.sub(r'public static void set\(', f'public static void set{primitive}(', content)
    
    # 2. getUnsafe/setUnsafe -> getUnsafePrimitive / setUnsafePrimitive
    content = re.sub(r'public static ([\w<>]+) getUnsafe\(', f'public static \\1 getUnsafe{primitive}(', content)
    content = re.sub(r'public static void setUnsafe\(', f'public static void setUnsafe{primitive}(', content)

    # 3. getVolatile/setVolatile -> getVolatilePrimitive / setVolatilePrimitive
    content = re.sub(r'public static ([\w<>]+) getVolatile\(', f'public static \\1 getVolatile{primitive}(', content)
    content = re.sub(r'public static void setVolatile\(', f'public static void setVolatile{primitive}(', content)

    # 4. getUnsafeVolatile/setUnsafeVolatile -> getUnsafeVolatilePrimitive / setUnsafeVolatilePrimitive
    content = re.sub(r'public static ([\w<>]+) getUnsafeVolatile\(', f'public static \\1 getUnsafeVolatile{primitive}(', content)
    content = re.sub(r'public static void setUnsafeVolatile\(', f'public static void setUnsafeVolatile{primitive}(', content)

    # Handle the compound parts in IntFloat, IntDouble, etc.
    # getIntPartVolatile -> getVolatileIntPart
    content = re.sub(r'public static ([\w<>]+) get([A-Z]\w+)PartVolatile\(', r'public static \1 getVolatile\2Part(', content)
    content = re.sub(r'public static void set([A-Z]\w+)PartVolatile\(', r'public static void setVolatile\1Part(', content)
    
    # unsafeGetIntPart -> getUnsafeIntPart
    content = re.sub(r'public static ([\w<>]+) unsafeGet([A-Z]\w+)Part\(', r'public static \1 getUnsafe\2Part(', content)
    content = re.sub(r'public static void unsafeSet([A-Z]\w+)Part\(', r'public static void setUnsafe\1Part(', content)

    # unsafeVolatileGetIntPart -> getUnsafeVolatileIntPart
    content = re.sub(r'public static ([\w<>]+) unsafeVolatileGet([A-Z]\w+)Part\(', r'public static \1 getUnsafeVolatile\2Part(', content)
    content = re.sub(r'public static void unsafeVolatileSet([A-Z]\w+)Part\(', r'public static void setUnsafeVolatile\1Part(', content)
    
    # Also fix anything like getAndSet -> getAndSetPrimitive (wait, the user said get/set)
    # Let's also check if compareAndSet exists
    content = re.sub(r'public static ([\w<>]+) getAndSet\(', f'public static \\1 getAndSet{primitive}(', content)
    content = re.sub(r'public static boolean compareAndSet\(', f'public static boolean compareAndSet{primitive}(', content)

    # Let's save the file
    with open(filepath, "w") as f:
        f.write(content)
