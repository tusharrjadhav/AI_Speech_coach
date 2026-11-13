# Speech Coach prototype

An Android-first, local speech-practice experiment. Record up to two minutes, play
back the recording, transcribe it on device, edit the transcript, and review
estimated pace and possible fillers. Optional Gemma 4 suggestions focus on the
transcript's wording, not how the speaker sounded.

## Run on Android

Open the project in Android Studio with JDK 17 or newer and Android SDK 36, or run:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 9 (API 28) or newer is required. Grant microphone access when starting
a recording. Recording stops when the app leaves the screen; the latest completed
recording can be played or deleted. A new successful recording replaces the
previous one. The transcript is editable during a session but is not yet saved
across app restarts.

The app does not download models automatically. On a suitable device, choose:

- **Speech recognition:** 615 MB, required for MediaPipe transcription.
- **Gemma 4 coaching:** 2.59 GB, optional; requires substantial RAM and can be
  slow or unsupported on a given device. Its runtime is packaged for 64-bit
  ARM and x86 devices only. Basic recording and analysis do not depend on it.

Downloads use HTTPS, support retrying partial downloads, and are SHA-256 verified
before use. Hugging Face receives the normal network request when downloading.
Recording, transcription, analysis and coaching run locally with no cloud
fallback. Google's [MediaPipe privacy notice](https://developers.google.com/edge/mediapipe/solutions/privacy)
says audio input is processed on device but MediaPipe sends API performance and
usage metrics to Google. The prototype asks for agreement before transcription;
review the notice and consent requirements before distribution.

The models and audio use app-private storage and Android backup is disabled. A
model remains installed until the app's data is cleared or the app is removed.
Use Wi-Fi and ensure sufficient free storage for models and runtime caches.

Pace uses simple word boundaries suited to English and divides the word count
by the entire recording duration, including silence.
Possible filler counts currently match the English words “um” and “uh” in the
transcript, not the audio. Recognition may omit or invent fillers, so a zero
count does not prove that none were spoken. Edit the transcript to recalculate
the estimates; AI suggestions can also be wrong.

## Structure

- `shared`: Kotlin Multiplatform contracts, analysis, and adaptive Compose
  Multiplatform UI. Android, iOS and desktop targets are configured; only
  Android has an application and platform adapters so far.
- `app`: Android application, microphone recording, playback, permissions and
  user-initiated model downloads.
- `transcription-mediapipe`: Android MediaPipe audio transcription adapter.
- `coaching-gemma`: optional Android LiteRT-LM text-coaching adapter.

Adapters implement interfaces in `shared`, so other speech recognition and
coaching implementations can be added without changing the analysis rules.
The iOS and desktop targets are foundations, not finished applications.

Run the analysis and model-download tests with
`./gradlew :shared:desktopTest :app:testDebugUnitTest`.

## Model attribution

The speech model is a quantized LiteRT-LM version of NVIDIA's
[Parakeet-TDT-0.6B-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3),
prepared by [LiteRT Community](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3).
It is licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/).
The app downloads the pinned quantized model, not the original weights.

The optional [Gemma 4 E2B LiteRT-LM model](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
is published under Apache License 2.0. Review the linked model cards and
licenses before distribution.
