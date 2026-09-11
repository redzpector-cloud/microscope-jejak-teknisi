# Jejak Teknisi Microscope

Versi 3.0.1 — build stabil untuk GitHub Actions.

## Perbaikan utama
- Manajemen bitmap Freeze dibatasi agar lebih aman terhadap OutOfMemory.
- OCR menggunakan gambar yang dibatasi maksimal 2048 px.
- Google Lens/chooser dicek dengan `resolveActivity()` sehingga tidak menyebabkan crash saat aplikasi tujuan tidak tersedia.
- Capture Camera2 enhancement dibuat opsional/fallback untuk perangkat yang tidak kompatibel.
- Exposure slider mengikuti range exposure kamera yang sebenarnya.
- Bitmap hasil anotasi dibersihkan setelah penyimpanan.
- File `MainActivity.java` duplikat di root project dihapus.
- GitHub Actions menggunakan Gradle 8.9 + JDK 17.

## Build di GitHub
Workflow berada di `.github/workflows/main.yml`.

1. Upload seluruh isi project ke repository GitHub.
2. Push ke branch `main` atau jalankan Actions → Build Magnifier Microscope APK → Run workflow.
3. APK Debug tersedia pada artifact `magnifier-microscope-debug-apk`.
