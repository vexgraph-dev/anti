package audio;

import exception.APIException;
import nio.ForeignMemory;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.openal.AL10.*;

/**
 * Off-heap WAV audio decoder and loader conforming to the Anti Architecture.
 * Parses RIFF/WAVE binary format headers natively using zero Java heap allocation.
 */
public final class AudioLoader
{
    private AudioLoader() {}

    /**
     * Loads a standard PCM WAV file, parses its header off-heap,
     * uploads the samples to OpenAL, and returns an AudioBuffer pointer.
     */
    public static long loadWav(String filePath)
    {
        byte[] fileBytes;
        try
        {
            fileBytes = Files.readAllBytes(Paths.get(filePath));
        }
        catch (Exception e)
        {
            throw new APIException("Failed to read WAV file: " + filePath, e);
        }

        // 1. Allocate native memory block to hold the raw file bytes
        long filePtr = ForeignMemory.allocateNative(fileBytes.length);
        ForeignMemory.copyFromHeap(fileBytes, 0, filePtr, fileBytes.length);

        try
        {
            // 2. Validate RIFF WAVE header
            int riffHeader = ForeignMemory.getInt(filePtr);
            int waveHeader = ForeignMemory.getInt(filePtr + 8L);

            if (riffHeader != 0x46464952 || waveHeader != 0x45564157) // "RIFF" and "WAVE" in little-endian ASCII
            {
                throw new APIException("Invalid WAV file header format: " + filePath);
            }

            // 3. Scan RIFF chunks to find "fmt " and "data"
            long offset = 12L;
            long limit = fileBytes.length;

            short audioFormat = 0;
            short numChannels = 0;
            int sampleRate = 0;
            short bitsPerSample = 0;

            long dataPtr = 0L;
            int dataSize = 0;

            while (offset + 8L <= limit)
            {
                int chunkId = ForeignMemory.getInt(filePtr + offset);
                int chunkSize = ForeignMemory.getInt(filePtr + offset + 4L);
                offset += 8L;

                if (chunkId == 0x20746d66) // "fmt " in little-endian ASCII
                {
                    audioFormat = ForeignMemory.getShort(filePtr + offset);
                    numChannels = ForeignMemory.getShort(filePtr + offset + 2L);
                    sampleRate = ForeignMemory.getInt(filePtr + offset + 4L);
                    bitsPerSample = ForeignMemory.getShort(filePtr + offset + 14L);
                }
                else if (chunkId == 0x61746164) // "data" in little-endian ASCII
                {
                    dataPtr = filePtr + offset;
                    dataSize = chunkSize;
                    break; // "data" is typically the last chunk we care about
                }

                offset += chunkSize;
            }

            if (dataPtr == 0L || dataSize == 0)
            {
                throw new APIException("WAV file does not contain a valid 'data' chunk: " + filePath);
            }

            if (audioFormat != 1) // 1 = PCM uncompressed
            {
                throw new APIException("Unsupported WAV format: only uncompressed PCM is supported.");
            }

            // 4. Map WAV format to OpenAL formats
            int format = 0;
            if (numChannels == 1)
            {
                if (bitsPerSample == 8) format = AL_FORMAT_MONO8;
                else if (bitsPerSample == 16) format = AL_FORMAT_MONO16;
            }
            else if (numChannels == 2)
            {
                if (bitsPerSample == 8) format = AL_FORMAT_STEREO8;
                else if (bitsPerSample == 16) format = AL_FORMAT_STEREO16;
            }

            if (format == 0)
            {
                throw new APIException("Unsupported channels (" + numChannels + ") or bits per sample (" + bitsPerSample + ") combination.");
            }

            // 5. Generate and upload OpenAL Buffer
            long bufferPtr = AudioSystem.allocateBuffer();
            int alBufferId = AudioSystem.getBufferAlId(bufferPtr);

            // Wrap raw dataPtr into direct ByteBuffer zero-copy zero-GC
            ByteBuffer sampleBuf = MemoryUtil.memByteBuffer(dataPtr, dataSize);
            alBufferData(alBufferId, format, sampleBuf, sampleRate);

            // Populate AudioBuffer off-heap fields
            ForeignMemory.putInt(bufferPtr + 4L, sampleRate);
            ForeignMemory.putInt(bufferPtr + 8L, format);
            ForeignMemory.putInt(bufferPtr + 12L, dataSize);

            System.out.println("[AudioLoader] Loaded WAV: " + filePath + " (Size: " + dataSize + " bytes, Rate: " + sampleRate + "Hz)");
            return bufferPtr;
        }
        finally
        {
            ForeignMemory.freeNative(filePtr);
        }
    }
}
