#ifndef AUDIO_AUDIO_H
#define AUDIO_AUDIO_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "c23/constructor.h"

// audio/audio.h — platform-agnostic audio API.
//
// The implementation is audio_cocoa.m (AVAudioEngine) on macOS and
// audio_stub.c (silence) elsewhere — the same seam pattern as window/.
// C callers never see Core Audio: opaque handles, load or synthesize a clip,
// route it through a voice, press play.
//
// Surface mirrors legacy audio/AudioSystem.java + AudioSource.java:
// pitch/gain/looping/play/pause/stop/isPlaying, minus the OpenAL ids.

// Opaque handles; contents live in the backend file.
typedef struct AudioClip AudioClip;
typedef struct AudioVoice AudioVoice;

bool Audio_init(void);        // start the shared output engine; idempotent
void Audio_shutdown(void);
bool Audio_isReady(void);

// Clips: decoded media (wav/mp3/m4a/aiff) or synthesized tone. Blocks are
// self-describing; nullptr on failure.
AudioClip *AudioClip_load(const char *path);
AudioClip *AudioClip_tone(float frequencyHz, float seconds);
void AudioClip_free(AudioClip *clip);
float AudioClip_seconds(AudioClip *clip);

// Voices: one player channel each. A voice holds at most one clip reference;
// re-set clips freely between plays.
AudioVoice *AudioVoice_new(void);
void AudioVoice_free(AudioVoice *voice);
void AudioVoice_setClip(AudioVoice *voice, AudioClip *clip);
void AudioVoice_setGain(AudioVoice *voice, float gain);    // 0.0 .. 1.0+
void AudioVoice_setPitch(AudioVoice *voice, float pitch);  // 1.0 = native rate
void AudioVoice_setLooping(AudioVoice *voice, bool looping);
void AudioVoice_play(AudioVoice *voice);
void AudioVoice_pause(AudioVoice *voice);
void AudioVoice_stop(AudioVoice *voice);
bool AudioVoice_isPlaying(AudioVoice *voice);

// --- Audio: the one-handle class (constructor-overloaded) -------------------
//
//   Audio();                    // defaults — 44100 Hz, stereo, silent
//   Audio("hit.wav");           // decoded file, ready to play
//   Audio(22050);               // custom rate
//   Audio(48000, AUDIO_MONO);   // rate + channel tag
//
// One-arg calls resolve by TYPE: strings load files, numbers set the rate.

#define AUDIO_MONO   1
#define AUDIO_STEREO 2

typedef struct Audio Audio;

Audio *Audio_0(void);
Audio *Audio_load(const char *path);
Audio *Audio_withRate(double sampleRate);
Audio *Audio_2(double sampleRate, int channels);

#define Audio_1(a) _Generic((a),                    \
    char *: Audio_load,                             \
    const char *: Audio_load,                       \
    default: Audio_withRate)(a)

#define Audio(...) CONSTRUCTOR_DISPATCH(Audio, __VA_ARGS__)

// --- lifecycle ---
void Audio_play(Audio *a);       // schedules and plays (loops if looping)
void Audio_pause(Audio *a);
void Audio_stop(Audio *a);
bool Audio_isPlaying(Audio *a);
void Audio_free(Audio *a);       // final — detached and released

// --- setters / getters (the X in Audio_setX / Audio_getX) ---
void Audio_setGain(Audio *a, float gain);
float Audio_getGain(Audio *a);
void Audio_setPitch(Audio *a, float pitch);
float Audio_getPitch(Audio *a);
void Audio_setLooping(Audio *a, bool looping);
bool Audio_getLooping(Audio *a);
void Audio_setSampleRate(Audio *a, double rate);
double Audio_getSampleRate(Audio *a);
void Audio_setChannels(Audio *a, int channels);
int Audio_getChannels(Audio *a);

#endif
