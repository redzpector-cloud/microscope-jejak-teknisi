# Jejak Teknisi Microscope V3.8.3 — Build Fix

Perbaikan khusus GitHub Actions:
- Menghapus `android-actions/setup-android@v3` yang menyebabkan `Failed to find package 'tools'`.
- Menggunakan Android SDK bawaan GitHub Actions runner.
- Menyiapkan hanya `platform-tools`, `platforms;android-35`, dan `build-tools;35.0.0`.
- Java 17 dan Gradle 8.9 tetap digunakan.

Kode aplikasi V3.8.3 tidak diubah pada patch ini.
