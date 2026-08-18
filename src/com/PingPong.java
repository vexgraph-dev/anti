package com;

import annotation.Draft;
import annotation.Intention;
import net.HTTPClient;
import net.HTTPServer;
import primitive.string;

import thread.Atomic;

import nio.StringLookup;
/**
 * High-performance, off-heap Ping-Pong communication example.
 * Bootstraps an asynchronous local HTTPServer, polls for /ping requests,
 * executes an HTTP GET downcall via libcurl HTTPClient, and processes the "pong" response.
 */
@Draft
@Intention("Entry point example for com (communication) using DOD HTTPServer and libcurl HTTPClient")
public final class PingPong
{
    private static final int PORT = 12345;
    private static final String PING_URL = StringLookup.getJavaString(1196) + PORT + StringLookup.getJavaString(1197);

    private PingPong() {}

    /**
     * Executes the Ping-Pong communication loop.
     * Returns true if the transaction completes successfully with a "pong" response.
     */
    public static boolean runDemo()
    {
        System.out.println(StringLookup.getJavaString(1198) + PORT);

        // 1. Initialize HTTPServer instance handle off-heap
        long serverPtr = HTTPServer.invoke(PORT);
        if (!HTTPServer.start(serverPtr))
        {
            System.err.println(StringLookup.getJavaString(1199) + PORT);
            HTTPServer.free(serverPtr);
            return false;
        }

        System.out.println(StringLookup.getJavaString(1200) + HTTPServer.isRunning(serverPtr));

        // 2. Start a background thread to poll for HTTP requests
        long running = Atomic.allocateBool(true);
        Thread serverThread = Thread.ofPlatform().name(StringLookup.getJavaString(1201)).unstarted(() -> {
            while (Atomic.getBool(running))
            {
                long reqPtr = HTTPServer.pollRequest(serverPtr);
                if (reqPtr != 0L)
                {
                    long uriPtr = HTTPServer.getRequestUri(reqPtr);
                    String uri = string.get(uriPtr);

                    if (StringLookup.getJavaString(1197).equals(uri))
                    {
                        // Respond with "pong"
                        HTTPServer.sendResponse(reqPtr, 200, StringLookup.getJavaString(1202));
                    }
                    else
                    {
                        HTTPServer.sendResponse(reqPtr, 404, StringLookup.getJavaString(79));
                    }
                }
                
                // Small sleep to avoid hogging CPU while polling
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e)
                {
                    break;
                }
            }
        });
        serverThread.start();

        boolean success = false;
        long resPtr = 0L;
        try
        {
            // Give server a fraction of a second to spin up
            Thread.sleep(50);

            System.out.println(StringLookup.getJavaString(1203) + PING_URL);

            // 3. Perform a GET request to the local server
            resPtr = HTTPClient.get(PING_URL);
            if (resPtr != 0L)
            {
                String responseText = string.get(resPtr);
                System.out.println(StringLookup.getJavaString(1204) + responseText);
                
                if (StringLookup.getJavaString(1202).equals(responseText))
                {
                    success = true;
                }
            }
            else
            {
                System.err.println(StringLookup.getJavaString(1205));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            // 4. Tear down server poll and clean up native handles
            Atomic.setBool(running, false);
            try
            {
                serverThread.join();
            }
            catch (InterruptedException ignored) {}

            Atomic.free(running);
            HTTPServer.free(serverPtr);
            if (resPtr != 0L)
            {
                string.free(resPtr);
            }
        }

        return success;
    }
}
