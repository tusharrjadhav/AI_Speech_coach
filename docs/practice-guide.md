# Speech Coach practice and developer guide

See the [project overview](../README.md) for the tech stack and quick start.
Run the commands in this guide from the repository root.

An Android-first, local speech-practice app. Choose **Team update**, **Interview**,
or **Explain an idea**, then record up to two minutes. Transcribe on device or
enter the words yourself. Review the estimates, choose a focus, and get one
exercise for the next take. This loop does not require a model or an account.

Choose **Save attempt** to keep the recording, reviewed transcript, metrics,
exercise, and any AI advice or revision. In **Local history**, replay it or
choose **Practice this again**. After reviewing the next take, compare word
count, estimated pace, and possible fillers per 100 words side by side. Optional
Gemma suggestions use the reviewed text and estimates, not how the speaker sounded.
An optional rewrite highlights suggested changes without replacing the original.

The **Listen to suggestions** control reads the displayed AI advice using the
device's selected Android text-to-speech engine. It chooses an installed voice
for the device language that the engine marks as not requiring a network; if
none is available, install an offline voice in the device's text-to-speech
settings. Reading can be stopped and also stops when recording, playback, or
another operation starts, or when the app leaves the screen.

The app does not intentionally send suggestions to a cloud TTS service. However,
the installed TTS engine is a separate app, and its offline-voice flag does not
guarantee that the engine collects no diagnostics or other data. Check that
engine's privacy policy and settings.

## Run on Android

Open the project in Android Studio with JDK 17 or newer and Android SDK 36, or run:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 9 (API 28) or newer is required. Grant microphone access when starting
a recording. Recording stops when the app leaves the screen. The latest completed
recording is a **working draft**: another successful recording replaces it.
Unsaved text edits do not survive a restart. Saving the same recording again
updates its history entry; the saved snapshot reopens with its draft if that
recording is still current. History is independent of the draft: deleting one
does not delete the other. Saved attempts stay in app-private SQLite storage
until explicitly deleted or app data is cleared. A two-minute saved recording
uses about 3.8 MB, in addition to the active draft if it is the same take.

Under **Model settings → Appearance**, choose **System**, **Light**, or **Dark**.
The selection persists across restarts.

## Models and privacy

Open **Model settings** to download a model or browse for an existing local
`.litertlm` file. The app does not download models automatically. On a suitable
device, choose:

- **Speech recognition:** 615 MB, required for MediaPipe transcription.
- **Gemma 4 coaching:** 2.59 GB, optional; requires substantial RAM and can be
  slow or unsupported on a given device. Its runtime is packaged for 64-bit
  ARM and x86 devices only. Basic recording and analysis do not depend on it.

The picker accepts only the exact pinned files listed on the settings screen;
other model versions or formats are not supported. Imported files are copied to
app-private storage and checked against the same size and SHA-256 as downloads.
You need free space for the copy, even if the original already exists on the
device. A failed import does not replace an installed model or interrupt a
partially downloaded one. An import interrupted by app termination is removed
when the app next starts.

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
the estimates; AI suggestions and rewritten text can also be wrong, remove
important details, or change your meaning even when instructed not to. Review
every proposed change. Gemma receives the transcript and
its derived estimates and text advice, not the recording. When retrying the same
scenario and focus, the harness also supplies the prior exercise and the two
sets of estimates, not the full earlier transcript. Each AI suggestion
must quote an exact excerpt from the transcript or it is rejected. If the model does not return the
requested format, coaching reports an error and can be retried. This verifies
the excerpt only: advice, implied measurements and rewritten text may still be
incorrect. Changing the transcript or coaching focus clears previous suggestions
and comparisons. Local coaching accepts transcripts of up
to 6,000 characters; longer transcripts must be shortened explicitly.
The comparison highlights removed and added text and lists suggested wording
as before/after pairs. These are AI proposals, not verified transcription
corrections. If you approve a revision, select and copy it from the Improved
view; the original is not replaced. For exceptionally long changes, the
comparison may highlight a larger region and will say so.
The transcription adapter uses interim results to remove some duplicated
segment-boundary words and adds a short silent tail for decoding. It does not
deduplicate words indiscriminately: genuine repetitions matter for coaching.
Recognition and boundary merging can still be wrong, so review the transcript.

## Delivery metrics and coaching methods

There is no universal speaking rate or single score that proves a speech is
clear. This prototype shows observations rather than grading the speaker:

| Area | Available now | Limitations and practice |
| --- | --- | --- |
| Overall pace | Edited transcript word count divided by the full recording duration, in words per minute. | Includes silence, is sensitive to recognition and edits, and does not show speed changes or intelligibility. Try pausing between ideas and adapting to the audience. |
| Possible fillers | Counts “um” and “uh” and shows occurrences per 100 reviewed words. | Recognition can omit or invent them. Short takes exaggerate changes in density; fillers are normal. |
| Repeated phrases | Up to three repeated adjacent word pairs, with counts and examples from the transcript. | Repetition may be intentional. Listen back before changing the wording. |
| Section pace | WPM for coarse, nonoverlapping recognition sections when at least two have three words and last three seconds each. | Not word alignment. Hidden for manual or edited transcripts; silence within a section still counts. |
| Quiet gaps | Estimates interior gaps of at least 0.5 seconds between louder sounds; replay up to three of the longest. | Uses 20 ms signal-level windows. Background noise and microphone gain affect detection. These are not necessarily speech pauses. |
| Recording quality | Flags low signal or possible clipping. | Checks the recording, not the speaker’s vocal volume. Listen and adjust the microphone if needed. |
| Wording and structure | Goal-focused, transcript-anchored AI suggestions and an optional highlighted rewrite. | Matching an excerpt does not verify advice or preserve meaning. Review each proposed edit. |
| Pronunciation, pitch, confidence and intelligibility | Not scored. | Neither transcript metrics nor these signal checks establish how clearly someone spoke. |

[Microsoft Speaker Coach](https://support.microsoft.com/en-us/powerpoint/suggestions-from-speaker-coach)
reports pace variation, fillers and other delivery features and suggests pausing
and practicing. It offers 100–165 words per minute as guidance in its tested
presentation context, while acknowledging individual differences. Our overall
recording rate is not the same as its short-interval rate, so we do not apply
that range as a pass/fail threshold. A [clear-speech research review](https://europepmc.org/article/PMC/PMC2747755)
also notes that speaking rate alone does not consistently predict intelligibility.
[ASHA](https://www.asha.org/practice-portal/clinical-topics/fluency-disorders/)
notes that everyone is disfluent at times; this app does not diagnose speech
or language conditions.

Other production products report broader features than this prototype:
[Yoodli](https://yoodli.ai/use-cases/public-speaking) describes goal-oriented
practice and reports on pacing, fillers and structure;
[Orai](https://orai.com/product/) describes pace, fillers and practice trends;
[Oompf](https://oompf.app/about) inspired the short practice-and-retry loop.
Their product claims are not validation of this app's measurements, and this
prototype does not provide an overall delivery score, simulated role-play, or long-term trends.

## Structure

- `practice-core`: Kotlin Multiplatform contracts, transcript and PCM metrics,
  scenarios, exercises, retry comparison, and the coaching prompt/validation harness.
- `shared`: adaptive Compose Multiplatform practice, history, and settings screens.
- `audio-android`: microphone input, WAV decoding for analysis, seekable playback
  of draft/archived audio, and offline text-to-speech output.
- `history-local`: Android SQLite archive. Audio and snapshot are saved/deleted in
  one row; list queries never load recordings. Playback reads one bounded WAV
  without a cursor window or temporary audio file.
- `app`: Android entry point, practice state, permission handling, appearance
  persistence, and user-initiated model downloads.
- `transcription-mediapipe`: Android MediaPipe audio transcription adapter.
- `coaching-gemma`: optional Gemma advice/revision adapter. A `TextInference`
  interface separates model I/O and the LiteRT-LM engine lifecycle from coaching policy.

Adapters implement interfaces in `practice-core`, so other speech recognition, coaching,
and speech-output implementations can be added without changing the analysis
rules. The Android entry point supplies its speech-output adapter to the
ViewModel, while the shared UI passes the displayed suggestion text to it.
The iOS and desktop targets are foundations, not finished applications.
Module plugin and library versions are managed in `gradle/libs.versions.toml`;
the settings-level toolchain resolver is declared in `settings.gradle.kts`
because Gradle does not support catalog plugin aliases there.

## Testing

Run the metrics, retry harness (including fake inference), audio, transcription,
and model-download tests:

```sh
./gradlew :practice-core:desktopTest :coaching-gemma:testDebugUnitTest :audio-android:testDebugUnitTest :transcription-mediapipe:testDebugUnitTest :app:testDebugUnitTest
```

To run the real record/save/replay/retry/delete journey, use an **empty,
disposable emulator only**, with no user recordings or models. The opt-in UI
test records two short takes and enters synthetic transcripts; it does not run
ASR or Gemma. Replace `SERIAL` with that emulator's serial. Do not use a
personal device or clear an existing user's data to run this test.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
android run --device=SERIAL --apks=app/build/outputs/apk/debug/app-debug.apk
android install --device=SERIAL --apks=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk --install-options=-t
adb -s SERIAL shell pm grant com.example.speechcoach android.permission.RECORD_AUDIO
adb -s SERIAL shell am instrument -w -e speechCoachScratchDevice true com.example.speechcoach.test/androidx.test.runner.AndroidJUnitRunner
```

The opt-in `GemmaRevisionProbeTest` includes a synthetic-text probe of all three
coaching goals and excerpt validation. With the pinned coaching model installed
on the test device, it can be run with:

```sh
adb shell am instrument -w -e class 'com.example.speechcoach.GemmaRevisionProbeTest#generatesSuggestionsWithVerifiedExcerptsForEachGoal' -e speechCoachRevisionProbe true com.example.speechcoach.test/androidx.test.runner.AndroidJUnitRunner
```

The probe logs only its synthetic input and output. A pass checks the output
format and excerpts, not whether the advice or a rewrite is correct.

The Android instrumentation tests include an opt-in synthetic speech probe
that requires an installed offline voice for the device language. To run it
after installing the debug app and test APK on an emulator or device, use:

```sh
adb shell am instrument -w -e class com.example.speechcoach.AndroidSpeechOutputTest -e speechCoachTtsProbe true com.example.speechcoach.test/androidx.test.runner.AndroidJUnitRunner
```

### Experimental Gemma audio probe

The opt-in Android instrumentation probe tests the pinned models with a short
synthetic recording; it does not add audio coaching to the app. A passing test
checks recognizable words and nonempty responses, not suggestion quality or
grounding. It logs the transcript and suggestions, so use only synthetic audio.

On macOS, with a debug app and both models installed on the selected device:

```sh
say -v Samantha -r 150 -o /tmp/speechcoach-gemma-probe.aiff 'Our team completed the launch on time. Tomorrow we will review customer feedback.'
afconvert -f WAVE -d LEI16@16000 -c 1 /tmp/speechcoach-gemma-probe.aiff /tmp/speechcoach-gemma-source.wav
python3 -c 'import wave; a=wave.open("/tmp/speechcoach-gemma-source.wav","rb"); b=wave.open("/tmp/speechcoach-gemma-probe.wav","wb"); b.setparams(a.getparams()); b.writeframes(a.readframes(a.getnframes())); b.close(); a.close()'
adb push /tmp/speechcoach-gemma-probe.wav /data/local/tmp/speechcoach-gemma-probe.wav
adb shell run-as com.example.speechcoach cp /data/local/tmp/speechcoach-gemma-probe.wav cache/speechcoach-gemma-probe.wav
adb shell rm /data/local/tmp/speechcoach-gemma-probe.wav
./gradlew :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.speechcoach.GemmaAudioProbeTest -e speechCoachAudioProbe true com.example.speechcoach.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s GemmaAudioProbe:I '*:S'
```

Set `ANDROID_SERIAL` when more than one device is connected. The probe is skipped
without the explicit instrumentation argument. Afterward, remove its synthetic
fixture if it is no longer needed:

```sh
adb shell run-as com.example.speechcoach rm -f cache/speechcoach-gemma-probe.wav
rm -f /tmp/speechcoach-gemma-probe.aiff /tmp/speechcoach-gemma-source.wav /tmp/speechcoach-gemma-probe.wav
```

## Model attribution

The speech model is a quantized LiteRT-LM version of NVIDIA's
[Parakeet-TDT-0.6B-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3),
prepared by [LiteRT Community](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3).
It is licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/).
The app downloads the pinned quantized model, not the original weights.

The optional [Gemma 4 E2B LiteRT-LM model](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
is published under Apache License 2.0. Review the linked model cards and
licenses before distribution.
