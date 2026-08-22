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

// --- Audio: one-handle class (silent backend) ---

Audio *Audio_0(void) {
    return NULL;
}

Audio *Audio_load(const char *path) {
    (void)path;
    return NULL;
}

Audio *Audio_withRate(double sampleRate) {
    (void)sampleRate;
    return NULL;
}

Audio *Audio_2(double sampleRate, int channels) {
    (void)sampleRate;
    (void)channels;
    return NULL;
}

void Audio_play(Audio *a) {
    (void)a;
}

void Audio_pause(Audio *a) {
    (void)a;
}

void Audio_stop(Audio *a) {
    (void)a;
}

bool Audio_isPlaying(Audio *a) {
    (void)a;
    return false;
}

void Audio_setGain(Audio *a, float gain) {
    (void)a;
    (void)gain;
}

float Audio_getGain(Audio *a) {
    (void)a;
    return 0.0f;
}

void Audio_setPitch(Audio *a, float pitch) {
    (void)a;
    (void)pitch;
}

float Audio_getPitch(Audio *a) {
    (void)a;
    return 1.0f;
}

void Audio_setLooping(Audio *a, bool looping) {
    (void)a;
    (void)looping;
}

bool Audio_getLooping(Audio *a) {
    (void)a;
    return false;
}

void Audio_setSampleRate(Audio *a, double rate) {
    (void)a;
    (void)rate;
}

double Audio_getSampleRate(Audio *a) {
    (void)a;
    return 0.0;
}

void Audio_setChannels(Audio *a, int channels) {
    (void)a;
    (void)channels;
}

int Audio_getChannels(Audio *a) {
    (void)a;
    return 0;
}

void Audio_free(Audio *a) {
    (void)a;
}
