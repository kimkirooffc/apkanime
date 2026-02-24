package com.aniflow.model;

public record Genre(String name, String slug) {
    @Override
    public String toString() {
        return name;
    }
}
