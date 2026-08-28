#import <AVFoundation/AVFoundation.h>

#include <math.h>
#include <string.h>

#include "audio/audio.h"

#include "nio/mem.h"
#include "oop/type.h"

// audio/audio_cocoa.m — AVFoundation backend (the macOS half of audio.h).
//
// One shared AVAudioEngine; one AVAudioPlayerNode per voice. Clips are
// AVAudioPCMBuffers, either decoded from disk via AVAudioFile or synthesized
// sine waves for tests and chiptunes. Compiled with -fobjc-arc (see CMake).

static AVAudioEngine *s_engine = NULL;
static bool s_ready = false;

typedef struct AudioClip {
    AVAudioPCMBuffer *buffer;
    float seconds;
} AudioClip;

typedef struct AudioVoice {
    AVAudioPlayerNode *node;
    AudioClip *clip;
    bool active;      // a buffer was scheduled and play() pressed
    bool looping;
} AudioVoice;

// The engine refuses to start with zero attached nodes, so init only
// constructs it; the first voice attachment flips it live (ensureRunning).
static AVAudioEngine *sharedEngine(void) {
    if (!s_engine)
        s_engine = [[AVAudioEngine alloc] init];
    return s_engine;
}

static bool ensureRunning(void) {
    AVAudioEngine *engine = sharedEngine();
    if (!engine || engine.isRunning)
        return engine != NULL;
    __block bool ok = true;
    @try {
        ok = [engine startAndReturnError:NULL];
    } @catch (NSException *e) {
        ok = false;
    }
    return ok;
}

bool Audio_init(void) {
    @autoreleasepool {
        s_ready = sharedEngine() != NULL;
        return s_ready;
    }
}

void Audio_shutdown(void) {
    if (!s_ready)
        return;
    [s_engine stop];
    s_engine = NULL;
    s_ready = false;
}

bool Audio_isReady(void) {
    return s_ready;
}

static AudioClip *clipAlloc(float seconds) {
    AudioClip *clip = (AudioClip *)Memory_alloc(TYPE_AUDIO_CLIP_SINGLETON, sizeof(AudioClip));
    if (clip) {
        (*clip).buffer = NULL;
        (*clip).seconds = seconds;
    }
    return clip;
}

AudioClip *AudioClip_load(const char *path) {
    if (!path || !s_ready)
        return NULL;
    @autoreleasepool {
        NSString *nsPath = [NSString stringWithUTF8String:path];
        NSURL *url = [NSURL fileURLWithPath:nsPath];
        AVAudioFile *file = [[AVAudioFile alloc] initForReading:url error:NULL];
        if (!file)
            return NULL;

        AVAudioFormat *format = [file processingFormat];
        AVAudioFrameCount frames = (AVAudioFrameCount)[file length];
        if (frames == 0)
            return NULL;

        AVAudioPCMBuffer *buf = [[AVAudioPCMBuffer alloc]
            initWithPCMFormat:format frameCapacity:frames];
        if (!buf)
            return NULL;
        if (![file readIntoBuffer:buf error:NULL] || buf.frameLength == 0)
            return NULL;

        double secs = (double)frames / (double)format.sampleRate;
        AudioClip *clip = clipAlloc((float)secs);
        if (!clip)
            return NULL;
        (*clip).buffer = buf;
        return clip;
    }
}

AudioClip *AudioClip_tone(float frequencyHz, float seconds) {
    if (!s_ready || seconds <= 0.0f || frequencyHz <= 0.0f)
        return NULL;
    @autoreleasepool {
        AVAudioFormat *format = [[s_engine outputNode] outputFormatForBus:0];
        double rate = (double)format.sampleRate;
        AVAudioFrameCount frames = (AVAudioFrameCount)(rate * (double)seconds);

        AVAudioPCMBuffer *buf = [[AVAudioPCMBuffer alloc]
            initWithPCMFormat:format frameCapacity:frames];
        if (!buf)
            return NULL;
        buf.frameLength = frames;

        float *left = buf.floatChannelData[0];
        for (AVAudioFrameCount i = 0; i < frames; i++) {
            left[i] = sinf(2.0f * (float)M_PI * frequencyHz * (float)i / (float)rate);
        }
        // mono source into N channels
        int channels = (int)format.channelCount;
        for (int c = 1; c < channels; c++) {
            float *ch = buf.floatChannelData[c];
            memcpy(ch, left, frames * sizeof(float));
        }

        AudioClip *clip = clipAlloc(seconds);
        if (!clip)
            return NULL;
        (*clip).buffer = buf;
        return clip;
    }
}

void AudioClip_free(AudioClip *clip) {
    if (!clip)
        return;
    (*clip).buffer = NULL;
    Memory_free(clip);
}

float AudioClip_seconds(AudioClip *clip) {
    return clip ? (*clip).seconds : 0.0f;
}

AudioVoice *AudioVoice_new(void) {
    if (!s_ready)
        return NULL;
    @autoreleasepool {
        AudioVoice *v = (AudioVoice *)Memory_alloc(TYPE_AUDIO_VOICE_SINGLETON, sizeof(AudioVoice));
        if (!v)
            return NULL;
        (*v).node = [[AVAudioPlayerNode alloc] init];
        if (!(*v).node) {
            Memory_free(v);
            return NULL;
        }
        [s_engine attachNode:(*v).node];
        AVAudioFormat *out = [[s_engine outputNode] outputFormatForBus:0];
        [s_engine connect:(*v).node to:[s_engine mainMixerNode] format:out error:NULL];
        if (!ensureRunning()) {
            [s_engine detachNode:(*v).node];
            Memory_free(v);
            return NULL;
        }
        (*v).clip = NULL;
        (*v).active = false;
        (*v).looping = false;
        return v;
    }
}

void AudioVoice_free(AudioVoice *voice) {
    if (!voice)
        return;
    @autoreleasepool {
        [(*voice).node stop];
        [s_engine detachNode:(*voice).node];
    }
    (*voice).node = NULL;
    Memory_free(voice);
}

void AudioVoice_setClip(AudioVoice *voice, AudioClip *clip) {
    if (!voice)
        return;
    (*voice).clip = clip;
}

void AudioVoice_setGain(AudioVoice *voice, float gain) {
    if (!voice)
        return;
    [(*voice).node setVolume:gain];
}

void AudioVoice_setPitch(AudioVoice *voice, float pitch) {
    if (!voice || pitch <= 0.0f)
        return;
    [(*voice).node setRate:pitch];
}

void AudioVoice_setLooping(AudioVoice *voice, bool looping) {
    if (!voice)
        return;
    (*voice).looping = looping;
}

void AudioVoice_play(AudioVoice *voice) {
    if (!voice || !(*voice).clip)
        return;
    @autoreleasepool {
        AVAudioPCMBuffer *buf = (*(*voice).clip).buffer;
        AVAudioPlayerNodeBufferOptions opts = (*voice).looping
            ? AVAudioPlayerNodeBufferLoops
            : AVAudioPlayerNodeBufferInterrupts;
        [(*voice).node scheduleBuffer:buf atTime:nil options:opts completionHandler:nil];
        if (![(*voice).node playAndReturnError:NULL])
            return;
        (*voice).active = true;
    }
}

void AudioVoice_pause(AudioVoice *voice) {
    if (!voice)
        return;
    [(*voice).node pause];
}

void AudioVoice_stop(AudioVoice *voice) {
    if (!voice)
        return;
    [(*voice).node stop];
    (*voice).active = false;
}

bool AudioVoice_isPlaying(AudioVoice *voice) {
    if (!voice || !(*voice).active)
        return false;
    return (*voice).node != NULL && [(AVAudioPlayerNode *)(*voice).node isPlaying];
}

// --- Audio: one-handle class -------------------------------------------------

typedef struct Audio {
    AVAudioPCMBuffer *buffer;
    AVAudioPlayerNode *node;
    bool active;
    bool looping;
    float gain;
    float pitch;
    double rate;
    int channels;
} Audio;

static void audioInitFields(Audio *a, double rate, int channels) {
    (*a).buffer = NULL;
    (*a).node = NULL;
    (*a).active = false;
    (*a).looping = false;
    (*a).gain = 1.0f;
    (*a).pitch = 1.0f;
    (*a).rate = rate > 0.0 ? rate : 44100.0;
    (*a).channels = (channels == AUDIO_MONO || channels == AUDIO_STEREO) ? channels : AUDIO_STEREO;
}

static bool audioWireUp(Audio *a) {
    AVAudioPlayerNode *node = [[AVAudioPlayerNode alloc] init];
    if (!node)
        return false;
    [s_engine attachNode:node];
    AVAudioFormat *fmt = [[s_engine outputNode] outputFormatForBus:0];
    [s_engine connect:node to:[s_engine mainMixerNode] format:fmt error:NULL];
    if (!ensureRunning()) {
        [s_engine detachNode:node];
        return false;
    }
    (*a).node = node;
    return true;
}

Audio *Audio_0(void) {
    if (!s_ready)
        return NULL;
    @autoreleasepool {
        Audio *a = (Audio *)Memory_alloc(TYPE_AUDIO_SINGLETON, sizeof(Audio));
        if (!a)
            return NULL;
        audioInitFields(a, 44100.0, AUDIO_STEREO);
        return a;
    }
}

Audio *Audio_withRate(double sampleRate) {
    Audio *a = Audio_0();
    if (a)
        (*a).rate = sampleRate > 0.0 ? sampleRate : 44100.0;
    return a;
}

Audio *Audio_2(double sampleRate, int channels) {
    Audio *a = Audio_withRate(sampleRate);
    if (a && (channels == AUDIO_MONO || channels == AUDIO_STEREO))
        (*a).channels = channels;
    return a;
}

Audio *Audio_load(const char *path) {
    if (!path || !s_ready)
        return NULL;
    @autoreleasepool {
        NSURL *url = [NSURL fileURLWithPath:[NSString stringWithUTF8String:path]];
        AVAudioFile *file = [[AVAudioFile alloc] initForReading:url error:NULL];
        if (!file)
            return NULL;

        AVAudioFormat *format = [file processingFormat];
        AVAudioFrameCount frames = (AVAudioFrameCount)[file length];
        if (frames == 0)
            return NULL;

        AVAudioPCMBuffer *buf = [[AVAudioPCMBuffer alloc]
            initWithPCMFormat:format frameCapacity:frames];
        if (!buf)
            return NULL;
        if (![file readIntoBuffer:buf error:NULL] || buf.frameLength == 0)
            return NULL;

        Audio *a = Audio_0();
        if (!a)
            return NULL;
        (*a).buffer = buf;
        (*a).rate = format.sampleRate;
        (*a).channels = (int)format.channelCount;
        if (!audioWireUp(a)) {
            Memory_free(a);
            return NULL;
        }
        return a;
    }
}

void Audio_play(Audio *a) {
    if (!a || !(*a).buffer || !(*a).node)
        return;
    AVAudioPlayerNodeBufferOptions opts = (*a).looping
        ? AVAudioPlayerNodeBufferLoops
        : AVAudioPlayerNodeBufferInterrupts;
    [(*a).node scheduleBuffer:(*a).buffer atTime:nil options:opts completionHandler:nil];
    if (![(*a).node playAndReturnError:NULL])
        return;
    (*a).active = true;
}

void Audio_pause(Audio *a) {
    if (a && (*a).node)
        [(*a).node pause];
}

void Audio_stop(Audio *a) {
    if (!a || !(*a).node)
        return;
    [(*a).node stop];
    (*a).active = false;
}

bool Audio_isPlaying(Audio *a) {
    if (!a || !(*a).active || !(*a).node)
        return false;
    return [(AVAudioPlayerNode *)(*a).node isPlaying];
}

void Audio_setGain(Audio *a, float gain) {
    if (!a)
        return;
    (*a).gain = gain;
    if ((*a).node)
        (*a).node.volume = gain;
}

float Audio_getGain(Audio *a) {
    return a ? (*a).gain : 0.0f;
}

void Audio_setPitch(Audio *a, float pitch) {
    if (!a || pitch <= 0.0f)
        return;
    (*a).pitch = pitch;
    if ((*a).node)
        (*a).node.rate = pitch;
}

float Audio_getPitch(Audio *a) {
    return a ? (*a).pitch : 1.0f;
}

void Audio_setLooping(Audio *a, bool looping) {
    if (a)
        (*a).looping = looping;
}

bool Audio_getLooping(Audio *a) {
    return a ? (*a).looping : false;
}

void Audio_setSampleRate(Audio *a, double rate) {
    if (!a || rate <= 0.0)
        return;
    (*a).rate = rate;
}

double Audio_getSampleRate(Audio *a) {
    return a ? (*a).rate : 0.0;
}

void Audio_setChannels(Audio *a, int channels) {
    if (!a || (channels != AUDIO_MONO && channels != AUDIO_STEREO))
        return;
    (*a).channels = channels;
}

int Audio_getChannels(Audio *a) {
    return a ? (*a).channels : 0;
}

void Audio_free(Audio *a) {
    if (!a)
        return;
    @autoreleasepool {
        if ((*a).node) {
            [(*a).node stop];
            [s_engine detachNode:(*a).node];
        }
    }
    (*a).node = NULL;
    (*a).buffer = NULL;
    Memory_free(a);
}
