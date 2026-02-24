# AniFlow (JavaFX)

Aplikasi desktop anime streaming dengan UI ala iOS, Java 17+, JavaFX, JFoenix, Ikonli.

## Fitur Utama

- UI/UX iOS-inspired: sidebar dock bulat, large title, card-grid, glassmorphism, bottom player bar.
- Halaman: `Home`, `Search`, `Player`, `Profile`.
- Integrasi API Otakudesu (Vercel) dengan fallback endpoint:
  - Home/ongoing/top/rekomendasi
  - Search + genre
  - Detail anime + episode list
  - Stream episode + download links
- Cache strategy:
  - Home data: 1 jam
  - Detail anime: 24 jam
  - Episode list: 24 jam
  - Stream URL: no-cache
- Offline mode fallback ke cache + notifikasi koneksi putus.
- Background sync tiap 6 jam + notifikasi episode baru + auto download opsional.
- Local persistence untuk `history` dan `watchlist` di `~/.aniflow`.

## Menjalankan (Termux + proot Ubuntu)

Cara paling aman di Android/Termux:

```bash
./run-aniflow.sh --setup-only
./run-aniflow.sh
```

Untuk mode GUI (dengan X server aktif):

```bash
./run-aniflow.sh --gui
```

Catatan:
- Skrip akan auto-install Ubuntu (`proot-distro`) jika belum ada.
- Dependency Ubuntu yang dipasang: `openjdk-21-jdk`, `maven`, `xvfb`, `xauth`.
- Dependency Maven di-prefetch sekali agar run berikutnya lebih cepat.

## Menjalankan (Linux desktop biasa)

Jika bukan Termux/proot dan sudah ada Java + Maven:

```bash
mvn -DskipTests javafx:run
```

## Koordinasi Tim (Deadline 1 Minggu)

Dokumen koordinasi agent dan timeline eksekusi ada di:

- `docs/PM-COORDINATION-WEEK1.md`
- `docs/P0-PLAYER-KICKOFF.md`

## Struktur

- `src/main/java/com/aniflow/app` -> app bootstrap/state
- `src/main/java/com/aniflow/service` -> API, cache, download, sync, analytics
- `src/main/java/com/aniflow/ui` -> layout + pages + components
- `src/main/resources/css/app.css` -> tema iOS-like
- `src/main/resources/icons/aniflow-icon.svg` -> aset icon

## Catatan Endpoint

API publik yang aktif saat ini menggunakan prefix `/api/anime/...`. Implementasi ini tetap mencoba endpoint lama (`/home`, `/search`, `/anime/{slug}`, `/episode/{slug}`) lalu fallback ke endpoint aktif agar kompatibel.
