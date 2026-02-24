package com.aniflow.service;

import com.aniflow.model.Anime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalLibraryService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dataDir = Path.of(System.getProperty("user.home"), ".aniflow");
    private final Path historyFile = dataDir.resolve("history.json");
    private final Path watchlistFile = dataDir.resolve("watchlist.json");

    public List<Anime> loadHistory() {
        return read(historyFile);
    }

    public List<Anime> loadWatchlist() {
        return read(watchlistFile);
    }

    public void saveHistory(List<Anime> history) {
        write(historyFile, history);
    }

    public void saveWatchlist(List<Anime> watchlist) {
        write(watchlistFile, watchlist);
    }

    private List<Anime> read(Path path) {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        try {
            CollectionType type = mapper.getTypeFactory().constructCollectionType(ArrayList.class, AnimeSnapshot.class);
            List<AnimeSnapshot> snapshots = mapper.readValue(path.toFile(), type);
            List<Anime> result = new ArrayList<>();
            snapshots.forEach(snapshot -> result.add(snapshot.toAnime()));
            return result;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    private void write(Path path, List<Anime> data) {
        try {
            Files.createDirectories(dataDir);
            List<AnimeSnapshot> snapshots = data.stream().map(AnimeSnapshot::fromAnime).toList();
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshots);
        } catch (IOException ignored) {
        }
    }

    private static class AnimeSnapshot {
        public int id;
        public String slug;
        public String title;
        public String coverImage;
        public String bannerImage;
        public String description;
        public int episodes;
        public String status;
        public double score;
        public String scoreText;
        public List<String> genres = new ArrayList<>();
        public String detailEndpoint;
        public String episodeLabel;
        public String releaseInfo;
        public String studio;

        public static AnimeSnapshot fromAnime(Anime anime) {
            AnimeSnapshot snapshot = new AnimeSnapshot();
            snapshot.id = anime.getId();
            snapshot.slug = anime.getSlug();
            snapshot.title = anime.getTitle();
            snapshot.coverImage = anime.getCoverImage();
            snapshot.bannerImage = anime.getBannerImage();
            snapshot.description = anime.getDescription();
            snapshot.episodes = anime.getEpisodes();
            snapshot.status = anime.getStatus();
            snapshot.score = anime.getScore();
            snapshot.scoreText = anime.getScoreText();
            snapshot.genres = anime.getGenres();
            snapshot.detailEndpoint = anime.getDetailEndpoint();
            snapshot.episodeLabel = anime.getEpisodeLabel();
            snapshot.releaseInfo = anime.getReleaseInfo();
            snapshot.studio = anime.getStudio();
            return snapshot;
        }

        public Anime toAnime() {
            return new Anime(
                id,
                slug,
                title,
                coverImage,
                bannerImage,
                description,
                episodes,
                status,
                score,
                scoreText,
                genres,
                "",
                null,
                detailEndpoint,
                episodeLabel,
                releaseInfo,
                studio
            );
        }
    }
}
