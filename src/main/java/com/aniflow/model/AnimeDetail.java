package com.aniflow.model;

import java.util.ArrayList;
import java.util.List;

public class AnimeDetail {
    private final Anime anime;
    private final List<EpisodeInfo> episodeList;
    private final List<Anime> relatedAnime;

    public AnimeDetail(Anime anime, List<EpisodeInfo> episodeList, List<Anime> relatedAnime) {
        this.anime = anime;
        this.episodeList = episodeList == null ? new ArrayList<>() : new ArrayList<>(episodeList);
        this.relatedAnime = relatedAnime == null ? new ArrayList<>() : new ArrayList<>(relatedAnime);
    }

    public Anime getAnime() {
        return anime;
    }

    public List<EpisodeInfo> getEpisodeList() {
        return new ArrayList<>(episodeList);
    }

    public List<Anime> getRelatedAnime() {
        return new ArrayList<>(relatedAnime);
    }

    public int latestEpisodeNumber() {
        return episodeList.stream()
            .map(EpisodeInfo::getEpisodeNumber)
            .max(Integer::compareTo)
            .orElse(0);
    }
}
