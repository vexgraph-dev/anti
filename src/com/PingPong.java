package com;

import annotation.Draft;
import annotation.Intention;
import net.HTTPClient;
import net.HTTPServer;
import primitive.string;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String PING_URL = "http://127.0.0.1:" + PORT + "/ping";

    private PingPong() {}

    /**
     * Executes the Ping-Pong communication loop.
     * Returns true if the transaction completes successfully with a "pong" response.
     */
    public static boolean runDemo()
    {
        System.out.println("[Com Demo] Initializing PingPong Server on port: " + PORT);

        // 1. Initialize HTTPServer instance handle off-heap
        long serverPtr = HTTPServer.invoke(PORT);
        if (!HTTPServer.start(serverPtr))
        {
            System.err.println("[Com Demo ERROR] Failed to start HTTPServer on port: " + PORT);
            HTTPServer.free(serverPtr);
            return false;
        }

        System.out.println("[Com Demo] Server running: " + HTTPServer.isRunning(serverPtr));

        // 2. Start a background thread to poll for HTTP requests
        AtomicBoolean running = new AtomicBoolean(true);
        Thread serverThread = Thread.ofPlatform().name("PingPong-Server-Poll").unstarted(() -> {
            while (running.get())
            {
                long reqPtr = HTTPServer.pollRequest(serverPtr);
                if (reqPtr != 0L)
                {
                    long uriPtr = HTTPServer.getRequestUri(reqPtr);
                    String uri = string.get(uriPtr);

                    if ("/ping".equals(uri))
                    {
                        // Respond with "pong"
                        HTTPServer.sendResponse(reqPtr, 200, "pong");
                    }
                    else
                    {
                        HTTPServer.sendResponse(reqPtr, 404, "Not Found");
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

            System.out.println("[Com Demo] Dispatching native libcurl GET request to: " + PING_URL);

            // 3. Perform a GET request to the local server
            resPtr = HTTPClient.get(PING_URL);
            if (resPtr != 0L)
            {
                String responseText = string.get(resPtr);
                System.out.println("[Com Demo] Response received: " + responseText);
                
                if ("pong".equals(responseText))
                {
                    success = true;
                }
            }
            else
            {
                System.err.println("[Com Demo ERROR] libcurl returned null response.");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            // 4. Tear down server poll and clean up native handles
            running.set(false);
            try
            {
                serverThread.join();
            }
            catch (InterruptedException ignored) {}

            HTTPServer.free(serverPtr);
            if (resPtr != 0L)
            {
                string.free(resPtr);
            }
        }

        return success;
    }
}
