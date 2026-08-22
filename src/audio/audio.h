#ifndef AUDIO_AUDIO_H
#define AUDIO_AUDIO_H

#include <stdbool.h>
#include <stdint.h>

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
// self-describing; NULL on failure.
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

#endif
