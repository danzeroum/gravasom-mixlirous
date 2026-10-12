// Ponte JNI do motor de transcrição (F7, ADR-013/017): carregar o modelo whisper.cpp, transcrever
// PCM 16 kHz mono e devolver "t0_ms\tt1_ms\ttexto\n" por segmento (mesmo contrato do spike ADR-013).
// Duas entradas de áudio: jfloatArray (testes/clipes curtos) e ByteBuffer DIRETO de shorts 16 bits
// (produção — 10 min de áudio = 19 MB nativos, em vez de 384 MB de heap Java num FloatArray).
#include <jni.h>

#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {
// Executa whisper_full sobre amostras float e devolve os segmentos, ou null em falha.
jstring transcreverFloats(JNIEnv* env, whisper_context* ctx, const float* samples, int n, jint threads,
                          jstring language) {
  const char* lang = env->GetStringUTFChars(language, nullptr);
  whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  params.print_realtime = false;
  params.print_progress = false;
  params.print_timestamps = false;
  params.print_special = false;
  params.translate = false;
  params.language = lang;
  params.n_threads = threads > 0 ? threads : static_cast<int>(std::thread::hardware_concurrency());
  params.no_context = true;
  const int rc = whisper_full(ctx, params, samples, n);
  env->ReleaseStringUTFChars(language, lang);
  if (rc != 0) return nullptr;
  std::string out;
  const int segments = whisper_full_n_segments(ctx);
  for (int i = 0; i < segments; ++i) {
    out += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10) + "\t" + std::to_string(whisper_full_get_segment_t1(ctx, i) * 10) +
           "\t" + whisper_full_get_segment_text(ctx, i) + "\n";
  }
  return env->NewStringUTF(out.c_str());
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeInit(JNIEnv* env, jobject, jstring path) {
  const char* p = env->GetStringUTFChars(path, nullptr);
  whisper_context_params cparams = whisper_context_default_params();
  cparams.use_gpu = false;
  whisper_context* ctx = whisper_init_from_file_with_params(p, cparams);
  env->ReleaseStringUTFChars(path, p);
  return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeFree(JNIEnv*, jobject, jlong handle) {
  if (handle != 0) whisper_free(reinterpret_cast<whisper_context*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeTranscribeFloat(JNIEnv* env, jobject, jlong handle, jfloatArray pcm, jint threads,
                                                                jstring language) {
  auto* ctx = reinterpret_cast<whisper_context*>(handle);
  if (ctx == nullptr) return nullptr;
  const jsize n = env->GetArrayLength(pcm);
  jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
  jstring out = transcreverFloats(env, ctx, samples, n, threads, language);
  env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
  return out;
}

// Buffer direto de shorts 16 bits LE mono 16 kHz (o Kotlin pré-converte/reamostra; o C++ só converte s16->f32).
extern "C" JNIEXPORT jstring JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeTranscribeS16(JNIEnv* env, jobject, jlong handle, jobject buffer, jint nShorts,
                                                              jint threads, jstring language) {
  auto* ctx = reinterpret_cast<whisper_context*>(handle);
  if (ctx == nullptr || buffer == nullptr) return nullptr;
  auto* shorts = static_cast<const jshort*>(env->GetDirectBufferAddress(buffer));
  if (shorts == nullptr) return nullptr;
  const int n = nShorts > 0 ? nShorts : static_cast<int>(env->GetDirectBufferCapacity(buffer) / 2);
  std::vector<float> samples(static_cast<size_t>(n));
  for (int i = 0; i < n; ++i) samples[static_cast<size_t>(i)] = shorts[i] / 32768.f;
  return transcreverFloats(env, ctx, samples.data(), n, threads, language);
}

// Total de tokens produzidos na última transcrição (prova ADR-016: "tokensUsados").
extern "C" JNIEXPORT jint JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeContarTokens(JNIEnv*, jobject, jlong handle) {
  auto* ctx = reinterpret_cast<whisper_context*>(handle);
  if (ctx == nullptr) return 0;
  int total = 0;
  const int segments = whisper_full_n_segments(ctx);
  for (int i = 0; i < segments; ++i) total += whisper_full_n_tokens(ctx, i);
  return total;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gravasom_core_whisper_WhisperJni_nativeSystemInfo(JNIEnv* env, jobject) {
  return env->NewStringUTF(whisper_print_system_info());
}
