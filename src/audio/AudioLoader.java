package audio;

import annotation.Draft;
import exception.APIException;
import nio.ForeignMemory;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.openal.AL10.*;

import nio.StringLookup;
/**
 * Off-heap WAV audio decoder and loader conforming to the Anti Architecture.
 * Parses RIFF/WAVE binary format headers natively using zero Java heap allocation.
 */
@Draft
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
            throw new APIException(StringLookup.getJavaString(592) + filePath, e);
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
                throw new APIException(StringLookup.getJavaString(593) + filePath);
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
                throw new APIException(StringLookup.getJavaString(594) + filePath);
            }

            if (audioFormat != 1) // 1 = PCM uncompressed
            {
                throw new APIException(StringLookup.getJavaString(595));
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
                throw new APIException(StringLookup.getJavaString(596) + numChannels + StringLookup.getJavaString(597) + bitsPerSample + StringLookup.getJavaString(598));
            }

            // 5. Generate and upload OpenAL Buffer
            long bufferPtr = AudioSystem.allocateBuffer();
            int alBufferId = AudioSystem.getBufferAlId(bufferPtr);

            // Wrap raw dataPtr into direct ByteBuffer zero-copy zero-GC
            ByteBuffer sampleBuf = MemoryUtil.memByteBuffer(dataPtr, dataSize);
            alBufferData(alBufferId, format, sampleBuf, sampleRate);

            // Populate AudioBuffer off-heap fields
            ForeignMemory.setInt(bufferPtr + 4L, sampleRate);
            ForeignMemory.setInt(bufferPtr + 8L, format);
            ForeignMemory.setInt(bufferPtr + 12L, dataSize);

            System.out.println(StringLookup.getJavaString(599) + filePath + StringLookup.getJavaString(600) + dataSize + StringLookup.getJavaString(601) + sampleRate + StringLookup.getJavaString(602));
            return bufferPtr;
        }
        finally
        {
            ForeignMemory.freeNative(filePtr);
        }
    }
}
