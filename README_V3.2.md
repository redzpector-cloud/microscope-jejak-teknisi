# Jejak Teknisi Microscope V3.2

Tahap V3.2 fokus pada Editor Gambar teknisi.

Alur utama:
- Microscope LIVE -> BEKU -> Editor
- Galeri -> pilih foto -> Editor

Editor mencakup zoom/pan, pilih objek, titik/marker, panah, garis, lingkaran, kotak, highlight, teks, OCR, undo/redo, duplikat, hapus semua, crop, putar, pengaturan brightness/contrast/sharpen, reset tampilan, simpan dan share.

Mode Jumper terpisah tidak digunakan; penandaan jalur jumper dilakukan melalui Editor.

Catatan build: proyek menggunakan Android Gradle Plugin/AndroidX dan workflow GitHub Actions yang sudah ada di repository.


## V3.2.1 – Editor Profesional
- Referensi desain mengikuti mockup Jejak Teknisi yang disepakati.
- Senter/Lampu tetap berada di tampilan awal Microscope (LIVE).
- Edit hanya dimulai dari FREEZE atau Galeri.
- Editor menambahkan Pen bebas berbasis objek, bersama marker, panah, garis, lingkaran, kotak, highlight, teks, OCR, undo/redo, crop, rotate, adjustment, simpan dan share.
- Marking tetap berada pada koordinat gambar sehingga mengikuti zoom/pan.
