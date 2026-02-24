# Anichin Scraper API (Vercel)

Serverless scrape API compatible dengan pola endpoint aplikasi Android.

## Endpoint

- `GET /api/anime/ongoing`
- `GET /api/anime/home`
- `GET /api/anime/complete?sort=rating`
- `GET /api/anime/search?q=keyword`
- `GET /api/anime/details/{slug}`
- `GET /api/anime/stream/{slug}`
- `GET /api/anime/genre`

## Deploy

```bash
cd vercel-scraper
npm install
vercel
```

Setelah deploy, base URL API contoh:

`https://your-project.vercel.app/api/anime`
