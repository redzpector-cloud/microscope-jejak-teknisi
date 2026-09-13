# EMMC DATABASE V2 — UI + DATABASE

Database digabung dari database starter + file `EMMC_database_teks_biasa`.

- Kode duplikat dihapus dengan pencocokan ter-normalisasi (huruf besar; spasi/tanda baca diabaikan).
- Struktur: `part_number,brand,capacity,grade,category,notes`.
- Brand tidak ditebak bila sumber tidak mencantumkannya.
- Kapasitas hanya diisi dari label kategori yang jelas; lainnya `Tidak dicantumkan`.
- Tanda `?` pada kode dipertahankan karena sumber tidak cukup jelas.

Total kode unik: 272

Aplikasi: V3.4.2

- Legacy / starter: 7
- A+++ 256: 5
- A+++ 128: 30
- A+++ 64: 33
- A+++ 3/32: 17
- A++ 3/32: 17
- A++ 16A: 19
- A++ 16B / A+: 15
- A+B: 9
- A+: 28
- Pilihan 256: 6
- Pilihan 128: 22
- Pilihan 64: 24
- Pilihan 32: 17
- Pilihan 16: 17
- Pilihan 8GB: 2
- A+ Samsung/A Khusus: 4


## UI V2
- Kamera microscope tetap LIVE di bagian atas saat Database dibuka.
- Panel database muncul dari bawah tanpa menutup preview kamera.
- Pencarian real-time berdasarkan kode, kategori, brand, dan kapasitas.
- Voice input tetap tersedia.
- Filter: Semua, A+++, A++, A+B, A+, Pilihan, Samsung / A Khusus.
- Kode dikelompokkan berdasarkan kategori sumber.
- Ketuk kode untuk melihat detail.
- Saat ditutup, kontrol microscope dikembalikan.
