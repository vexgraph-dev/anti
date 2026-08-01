package net;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;
import primitive.string;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

@Draft
@Intention("Pure native FFM libcurl HTTP client operating directly on raw long off-heap memory pointers with zero Java heap allocations")
public final class HTTPClient {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_HTTP_CLIENT;

    // libcurl Option Constants
    private static final int CURLOPT_URL = 10002;
    private static final int CURLOPT_WRITEDATA = 10001;
    private static final int CURLOPT_WRITEFUNCTION = 20011;
    private static final int CURLOPT_POSTFIELDS = 10015;
    private static final int CURLOPT_POSTFIELDSIZE = 60;
    private static final int CURLOPT_CUSTOMREQUEST = 10036;
    private static final int CURLOPT_HTTPHEADER = 10023;
    private static final int CURLOPT_TIMEOUT = 13;
    private static final int CURLOPT_FOLLOWLOCATION = 52;
    private static final int CURLOPT_SSL_VERIFYPEER = 64;
    private static final int CURLOPT_SSL_VERIFYHOST = 81;

    private static final int CURLINFO_RESPONSE_CODE = 2097154; // 0x200000 | 2

    private static final MethodHandle curl_easy_init;
    private static final MethodHandle curl_easy_setopt_ptr;
    private static final MethodHandle curl_easy_setopt_long;
    private static final MethodHandle curl_easy_perform;
    private static final MethodHandle curl_easy_cleanup;
    private static final MethodHandle curl_easy_getinfo_long;
    private static final MethodHandle curl_slist_append;
    private static final MethodHandle curl_slist_free_all;

    private static final MemorySegment writeCallbackStub;
    private static final boolean libcurlAvailable;

    static {
        MethodHandle init = null;
        MethodHandle setoptPtr = null;
        MethodHandle setoptLong = null;
        MethodHandle perform = null;
        MethodHandle cleanup = null;
        MethodHandle getinfoLong = null;
        MethodHandle slistAppend = null;
        MethodHandle slistFreeAll = null;
        MemorySegment stub = null;
        boolean available = false;

        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = findLibcurlSymbolLookup(linker);

            MemorySegment initSym = lookup.find("curl_easy_init").orElse(null);
            MemorySegment setoptSym = lookup.find("curl_easy_setopt").orElse(null);
            MemorySegment performSym = lookup.find("curl_easy_perform").orElse(null);
            MemorySegment cleanupSym = lookup.find("curl_easy_cleanup").orElse(null);
            MemorySegment getinfoSym = lookup.find("curl_easy_getinfo").orElse(null);
            MemorySegment slistAppendSym = lookup.find("curl_slist_append").orElse(null);
            MemorySegment slistFreeSym = lookup.find("curl_slist_free_all").orElse(null);

            if (initSym != null && setoptSym != null && performSym != null && cleanupSym != null) {
                init = linker.downcallHandle(initSym, FunctionDescriptor.of(ValueLayout.ADDRESS));
                setoptPtr = linker.downcallHandle(setoptSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                setoptLong = linker.downcallHandle(setoptSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
                perform = linker.downcallHandle(performSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                cleanup = linker.downcallHandle(cleanupSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                if (getinfoSym != null) {
                    getinfoLong = linker.downcallHandle(getinfoSym, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                }
                if (slistAppendSym != null) {
                    slistAppend = linker.downcallHandle(slistAppendSym, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                }
                if (slistFreeSym != null) {
                    slistFreeAll = linker.downcallHandle(slistFreeSym, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
                }

                MethodHandle writeCbMethod = MethodHandles.lookup().findStatic(
                        HTTPClient.class,
                        "writeCallback",
                        MethodType.methodType(long.class, MemorySegment.class, long.class, long.class, MemorySegment.class)
                );
                FunctionDescriptor writeCbDesc = FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS
                );
                stub = linker.upcallStub(writeCbMethod, writeCbDesc, Arena.global());
                available = true;
            }
        } catch (Throwable ignored) {}

        curl_easy_init = init;
        curl_easy_setopt_ptr = setoptPtr;
        curl_easy_setopt_long = setoptLong;
        curl_easy_perform = perform;
        curl_easy_cleanup = cleanup;
        curl_easy_getinfo_long = getinfoLong;
        curl_slist_append = slistAppend;
        curl_slist_free_all = slistFreeAll;
        writeCallbackStub = stub;
        libcurlAvailable = available;
    }

    private static SymbolLookup findLibcurlSymbolLookup(Linker linker) {
        String[] libNames = new String[] {
                "libcurl.4.dylib", "libcurl.dylib", "/usr/lib/libcurl.4.dylib",
                "libcurl.so.4", "libcurl.so",
                "libcurl-4.dll", "libcurl.dll", "curl"
        };
        for (String lib : libNames) {
            try {
                return SymbolLookup.libraryLookup(lib, Arena.global());
            } catch (Throwable ignored) {}
        }
        return SymbolLookup.loaderLookup().or(linker.defaultLookup());
    }

    // Upcall method invoked directly by libcurl native thread / C code
    public static long writeCallback(MemorySegment ptr, long size, long nmemb, MemorySegment userdata) {
        long totalBytes = size * nmemb;
        if (totalBytes <= 0L || userdata.address() == 0L) return totalBytes;

        long userStateAddr = userdata.address();
        long destPtr = ForeignMemory.getLong(userStateAddr);
        long currentLen = ForeignMemory.getLong(userStateAddr + 8L);
        long currentCap = ForeignMemory.getLong(userStateAddr + 16L);

        long requiredLen = currentLen + totalBytes;
        if (requiredLen > currentCap) {
            long newCap = Math.max(currentCap * 2L, requiredLen + 4096L);
            long newPtr = ForeignMemory.allocateNative(newCap);
            if (destPtr != 0L && currentLen > 0L) {
                ForeignMemory.copy(destPtr, newPtr, currentLen);
                ForeignMemory.freeNative(destPtr);
            }
            destPtr = newPtr;
            currentCap = newCap;
            ForeignMemory.putLong(userStateAddr, destPtr);
            ForeignMemory.putLong(userStateAddr + 16L, currentCap);
        }

        ForeignMemory.copy(ptr.address(), destPtr + currentLen, totalBytes);
        currentLen += totalBytes;
        ForeignMemory.putLong(userStateAddr + 8L, currentLen);
        ForeignMemory.putByte(destPtr + currentLen, (byte) 0); // null-terminator

        return totalBytes;
    }

    private HTTPClient() {}

    public static boolean isNativeLibcurlAvailable() {
        return libcurlAvailable;
    }

    // --- PURE OFF-HEAP NATIVE GET & POST ---

    public static long get(long urlPtr) {
        if (urlPtr == 0L) return 0L;
        return executeNative("GET", urlPtr, 0L, 0L);
    }

    public static long post(long urlPtr, long bodyPtr) {
        if (urlPtr == 0L) return 0L;
        return executeNative("POST", urlPtr, 0L, bodyPtr);
    }

    public static long request(long methodPtr, long urlPtr, long bodyPtr) {
        if (urlPtr == 0L) return 0L;
        String method = methodPtr != 0L ? string.get(methodPtr) : "GET";
        return executeNative(method, urlPtr, 0L, bodyPtr, 0L);
    }

    public static long request(long methodPtr, long urlPtr, long headersPtr, long bodyPtr) {
        if (urlPtr == 0L) return 0L;
        String method = methodPtr != 0L ? string.get(methodPtr) : "GET";
        return executeNative(method, urlPtr, headersPtr, bodyPtr, 0L);
    }

    public static long request(long methodPtr, long urlPtr, long headersPtr, long bodyPtr, long bodyLen) {
        if (urlPtr == 0L) return 0L;
        String method = methodPtr != 0L ? string.get(methodPtr) : "GET";
        return executeNative(method, urlPtr, headersPtr, bodyPtr, bodyLen);
    }

    // Direct string overloads allocating off-heap pointers
    public static long get(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        long urlPtr = string.allocate(urlStr);
        long res = get(urlPtr);
        string.free(urlPtr);
        return res;
    }

    public static long post(String urlStr, String bodyStr) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        long urlPtr = string.allocate(urlStr);
        long bodyPtr = bodyStr != null ? string.allocate(bodyStr) : 0L;
        long res = post(urlPtr, bodyPtr);
        string.free(urlPtr);
        if (bodyPtr != 0L) string.free(bodyPtr);
        return res;
    }

    public static long request(String method, String urlStr, String headersStr, String bodyStr) {
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        long urlPtr = string.allocate(urlStr);
        long headersPtr = headersStr != null ? string.allocate(headersStr) : 0L;
        long bodyPtr = bodyStr != null ? string.allocate(bodyStr) : 0L;
        long res = executeNative(method != null ? method : "GET", urlPtr, headersPtr, bodyPtr);
        string.free(urlPtr);
        if (headersPtr != 0L) string.free(headersPtr);
        if (bodyPtr != 0L) string.free(bodyPtr);
        return res;
    }

    // --- CORE NATIVE LIBCURL EXECUTION ENGINE ---

    private static long executeNative(String method, long urlPtr, long headersPtr, long bodyPtr) {
        return executeNative(method, urlPtr, headersPtr, bodyPtr, 0L);
    }

    private static long executeNative(String method, long urlPtr, long headersPtr, long bodyPtr, long bodyLen) {
        if (!libcurlAvailable || urlPtr == 0L) {
            return fallbackExecute(method, urlPtr, headersPtr, bodyPtr, bodyLen);
        }

        try {
            MemorySegment curl = (MemorySegment) curl_easy_init.invokeExact();
            if (curl.address() == 0L) return fallbackExecute(method, urlPtr, headersPtr, bodyPtr, bodyLen);

            // 1. Set URL pointer directly
            curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_URL, MemorySegment.ofAddress(urlPtr));

            // 2. Set Follow Location & SSL Settings
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_FOLLOWLOCATION, 1L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_TIMEOUT, 10L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_SSL_VERIFYPEER, 0L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_SSL_VERIFYHOST, 0L);

            // 3. Set Headers (including Content-Type: application/json for POST)
            MemorySegment slistHead = MemorySegment.NULL;
            if (headersPtr != 0L && curl_slist_append != null) {
                String headersStr = string.get(headersPtr);
                if (headersStr != null && !headersStr.isEmpty()) {
                    String[] lines = headersStr.split("\n");
                    try (Arena tempArena = Arena.ofConfined()) {
                        for (String line : lines) {
                            if (!line.trim().isEmpty()) {
                                MemorySegment lineSeg = tempArena.allocateFrom(line.trim());
                                slistHead = (MemorySegment) curl_slist_append.invokeExact(slistHead, lineSeg);
                            }
                        }
                    }
                }
            } else if ("POST".equalsIgnoreCase(method) && curl_slist_append != null) {
                try (Arena tempArena = Arena.ofConfined()) {
                    MemorySegment defaultHeaderSeg = tempArena.allocateFrom("Content-Type: application/json");
                    slistHead = (MemorySegment) curl_slist_append.invokeExact(slistHead, defaultHeaderSeg);
                }
            }

            if (slistHead.address() != 0L) {
                curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_HTTPHEADER, slistHead);
            }

            // 4. Set Custom Method & Post Fields
            long size = bodyLen > 0L ? bodyLen : (bodyPtr != 0L ? (long) string.length(bodyPtr) : 0L);
            if ("POST".equalsIgnoreCase(method)) {
                curl_easy_setopt_long.invokeExact(curl, CURLOPT_POSTFIELDSIZE, size);
                if (bodyPtr != 0L) {
                    curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_POSTFIELDS, MemorySegment.ofAddress(bodyPtr));
                }
            } else if (!"GET".equalsIgnoreCase(method)) {
                try (Arena tempArena = Arena.ofConfined()) {
                    MemorySegment methodSeg = tempArena.allocateFrom(method);
                    curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_CUSTOMREQUEST, methodSeg);
                }
                if (bodyPtr != 0L) {
                    curl_easy_setopt_long.invokeExact(curl, CURLOPT_POSTFIELDSIZE, size);
                    curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_POSTFIELDS, MemorySegment.ofAddress(bodyPtr));
                }
            }

            // 5. Setup Off-Heap Response State Buffer [destPtr (8B), currentLen (8B), capacity (8B)]
            long userStateAddr = ForeignMemory.allocateNative(24L);
            long initialCap = 4096L;
            long initialBuf = ForeignMemory.allocateNative(initialCap);

            ForeignMemory.putLong(userStateAddr, initialBuf);
            ForeignMemory.putLong(userStateAddr + 8L, 0L);
            ForeignMemory.putLong(userStateAddr + 16L, initialCap);

            // 6. Set Write Callback and User Data State Address
            curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_WRITEFUNCTION, writeCallbackStub);
            curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_WRITEDATA, MemorySegment.ofAddress(userStateAddr));

            // 7. Perform a native HTTP Request
            int res = (int) curl_easy_perform.invokeExact(curl);

            if (slistHead.address() != 0L && curl_slist_free_all != null) {
                curl_slist_free_all.invokeExact(slistHead);
            }
            curl_easy_cleanup.invokeExact(curl);

            long rawBuf = ForeignMemory.getLong(userStateAddr);
            if (res == 0) { // CURLE_OK
                long bytesLen = ForeignMemory.getLong(userStateAddr + 8L);
                ForeignMemory.freeNative(userStateAddr);

                if (rawBuf != 0L && bytesLen > 0L) {
                    long stringPtr = string.allocateUninitialized((int) bytesLen);
                    ForeignMemory.copy(rawBuf, stringPtr, (int) bytesLen);
                    ForeignMemory.putByte(stringPtr + bytesLen, (byte) 0);
                    ForeignMemory.freeNative(rawBuf);
                    return stringPtr;
                } else if (rawBuf != 0L) {
                    ForeignMemory.freeNative(rawBuf);
                }
            } else {
                if (rawBuf != 0L) ForeignMemory.freeNative(rawBuf);
                ForeignMemory.freeNative(userStateAddr);
            }
        } catch (Throwable t) {
            // Fallback if libcurl downcall encounters native issue
        }

        return fallbackExecute(method, urlPtr, headersPtr, bodyPtr, bodyLen);
    }

    private static long fallbackExecute(String method, long urlPtr, long headersPtr, long bodyPtr) {
        return fallbackExecute(method, urlPtr, headersPtr, bodyPtr, 0L);
    }

    private static long fallbackExecute(String method, long urlPtr, long headersPtr, long bodyPtr, long bodyLen) {
        String urlStr = string.get(urlPtr);
        if (urlStr == null || urlStr.isEmpty()) return 0L;
        URI uri = URI.create(urlStr);
        try(HttpClient fallbackClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build())
        {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);

            if (headersPtr != 0L) {
                String headersStr = string.get(headersPtr);
                if (headersStr != null && !headersStr.isEmpty()) {
                    String[] lines = headersStr.split("\n");
                    for (String line : lines) {
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            builder.header(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                        }
                    }
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                builder.header("Content-Type", "application/json");
            }

            if ("POST".equalsIgnoreCase(method) && bodyPtr != 0L) {
                long len = bodyLen > 0L ? bodyLen : (long) string.length(bodyPtr);
                byte[] bytes = new byte[(int) len];
                for (int i = 0; i < (int) len; i++) {
                    bytes[i] = ForeignMemory.getByte(bodyPtr + i);
                }
                builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            } else {
                builder.GET();
            }

            HttpResponse<byte[]> resp = fallbackClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return string.allocate(resp.body());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static int getStatus(long urlPtr) {
        if (urlPtr == 0L) return 500;
        if (!libcurlAvailable) {
            return fallbackStatus(string.get(urlPtr));
        }

        try {
            MemorySegment curl = (MemorySegment) curl_easy_init.invokeExact();
            if (curl.address() == 0L) return fallbackStatus(string.get(urlPtr));

            curl_easy_setopt_ptr.invokeExact(curl, CURLOPT_URL, MemorySegment.ofAddress(urlPtr));
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_FOLLOWLOCATION, 1L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_TIMEOUT, 10L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_SSL_VERIFYPEER, 0L);
            curl_easy_setopt_long.invokeExact(curl, CURLOPT_SSL_VERIFYHOST, 0L);

            int res = (int) curl_easy_perform.invokeExact(curl);
            int code = 500;
            if (res == 0 && curl_easy_getinfo_long != null) {
                long codeBuf = ForeignMemory.allocateNative(8L);
                curl_easy_getinfo_long.invokeExact(curl, CURLINFO_RESPONSE_CODE, MemorySegment.ofAddress(codeBuf));
                code = (int) ForeignMemory.getLong(codeBuf);
                ForeignMemory.freeNative(codeBuf);
            }
            curl_easy_cleanup.invokeExact(curl);
            return code != 0 ? code : 200;
        } catch (Throwable t) {
            return fallbackStatus(string.get(urlPtr));
        }
    }

    private static int fallbackStatus(String urlStr) {
        if (urlStr == null || urlStr.isEmpty())
            return 500;
        try (HttpClient fallbackClient = HttpClient.newBuilder().build()) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlStr)).GET().build();
            return fallbackClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            return 500;
        }
    }
}
