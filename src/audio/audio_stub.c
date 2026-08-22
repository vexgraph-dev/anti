#include "audio/audio.h"

// audio/audio_stub.c — the silence backend for platforms without the Cocoa
// seam yet. Same contract, zero sound: every handle fails to allocate and
// every control is a no-op, so callers stay identical across platforms.

#include "annotation/incomplete.h"

;;INCOMPLETE // Windows/X11 audio backends land here; until then this platform
;;INCOMPLETE // is silent by design.

bool Audio_init(void) {
    return false;
}

void Audio_shutdown(void) {
}

bool Audio_isReady(void) {
    return false;
}

AudioClip *AudioClip_load(const char *path) {
    (void)path;
    return NULL;
}

AudioClip *AudioClip_tone(float frequencyHz, float seconds) {
    (void)frequencyHz;
    (void)seconds;
    return NULL;
}

void AudioClip_free(AudioClip *clip) {
    (void)clip;
}

float AudioClip_seconds(AudioClip *clip) {
    (void)clip;
    return 0.0f;
}

AudioVoice *AudioVoice_new(void) {
    return NULL;
}

void AudioVoice_free(AudioVoice *voice) {
    (void)voice;
}

void AudioVoice_setClip(AudioVoice *voice, AudioClip *clip) {
    (void)voice;
    (void)clip;
}

void AudioVoice_setGain(AudioVoice *voice, float gain) {
    (void)voice;
    (void)gain;
}

void AudioVoice_setPitch(AudioVoice *voice, float pitch) {
    (void)voice;
    (void)pitch;
}

void AudioVoice_setLooping(AudioVoice *voice, bool looping) {
    (void)voice;
    (void)looping;
}

void AudioVoice_play(AudioVoice *voice) {
    (void)voice;
}

void AudioVoice_pause(AudioVoice *voice) {
    (void)voice;
}

void AudioVoice_stop(AudioVoice *voice) {
    (void)voice;
}

bool AudioVoice_isPlaying(AudioVoice *voice) {
    (void)voice;
    return false;
}
