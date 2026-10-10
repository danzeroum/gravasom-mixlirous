// Gera as bindings UniFFI (Kotlin) a partir da biblioteca compilada:
//   cargo run --bin uniffi-bindgen -- generate \
//     --library ../target/debug/libmixlirous_mobile.so \
//     --language kotlin --out-dir ../../core/mixengine/src/main/kotlin
fn main() {
    uniffi::uniffi_bindgen_main()
}
