package com.aniflow.model;

public record SearchFilter(String genre, String season, String status) {
    public static SearchFilter empty() {
        return new SearchFilter("All", "Any", "Any");
    }
}
