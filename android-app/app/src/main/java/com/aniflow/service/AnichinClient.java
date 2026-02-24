package com.aniflow.service;

import com.aniflow.model.Anime;
import com.aniflow.model.Episode;
import com.aniflow.model.StreamInfo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AnichinClient {

    private static final String BASE_URL = "https://anichin.cafe";
    private static final String SLUG_PREFIX = "anichin__";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern EPISODE_NUMBER_PATTERN = Pattern.compile("(?i)episode\\s*(\\d+)|(\\d+)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19\\d{2}|20\\d{2})");

    private final OkHttpClient httpClient;

    public AnichinClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }

    public List<Anime> getOngoing() throws IOException {
        Document doc = fetchDocument(BASE_URL + "/ongoing/");
        return parseSeriesCards(doc, "Ongoing", false);
    }

    public List<Anime> getCompletedTopRated() throws IOException {
        Document doc = fetchDocument(BASE_URL + "/completed/");
        return parseSeriesCards(doc, "Completed", true);
    }

    public List<Anime> search(String query) throws IOException {
        String normalized = safe(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        String encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8);
        Document doc = fetchDocument(BASE_URL + "/?s=" + encoded);
        return parseSeriesCards(doc, "", false);
    }

    public static boolean isAnichinSlug(String slug) {
        String value = safe(slug);
        return !value.isEmpty() && value.startsWith(SLUG_PREFIX);
    }

    public static String stripProviderPrefix(String slug) {
        String value = safe(slug);
        if (value.startsWith(SLUG_PREFIX)) {
            return value.substring(SLUG_PREFIX.length());
        }
        return value;
    }

    public ApiClient.AnimeDetail getAnimeDetail(String slugOrUrl) throws IOException {
        Document doc = fetchDocument(buildSeriesUrl(slugOrUrl));

        String title = safe(textOf(doc.selectFirst("h1.entry-title")));
        if (title.isEmpty()) {
            throw new IOException("Anichin detail title not found");
        }

        String seriesSlug = extractSeriesSlug(doc.location());
        String sourceUrl = safe(doc.location());
        String cover = firstNonBlank(
            absImageUrl(doc.selectFirst("div.bigcontent .thumb img")),
            absImageUrl(doc.selectFirst("div.single-info .thumb img")),
            attrOf(doc.selectFirst("meta[property=og:image]"), "content")
        );
        String banner = cover;
        String synopsis = normalizeText(firstNonBlank(
            textOf(doc.selectFirst("div.bixbox.synp .entry-content")),
            textOf(doc.selectFirst("div.info-content .desc")),
            "Sinopsis belum tersedia dari Anichin."
        ));

        Map<String, String> specs = parseSpecs(doc.select("div.info-content .spe span"));
        String status = normalizeStatus(firstNonBlank(specs.get("status"), "Unknown"));
        String studio = firstNonBlank(specs.get("studio"), "-");
        String producer = firstNonBlank(specs.get("producer"), specs.get("network"), "-");
        String duration = normalizeDuration(firstNonBlank(specs.get("duration"), "-"));
        String releaseInfo = firstNonBlank(specs.get("released on"), specs.get("released"), "-");

        String episodeLabel = firstNonBlank(specs.get("episodes"), "");
        int episodeCount = parseEpisodeNumber(episodeLabel);

        List<String> genres = new ArrayList<>();
        for (Element genreEl : doc.select("div.genxed a")) {
            String genre = safe(genreEl.text());
            if (!genre.isEmpty()) {
                genres.add(genre);
            }
            if (genres.size() >= 8) {
                break;
            }
        }

        double score = parseScore(firstNonBlank(
            textOf(doc.selectFirst("div.rating strong")),
            textOf(doc.selectFirst("div.single-info .rating strong"))
        ));
        String scoreText = score > 0d ? String.format(Locale.US, "%.2f", score) : "-";

        Anime anime = new Anime(
            Math.abs(seriesSlug.hashCode()),
            addProviderPrefix(seriesSlug),
            title,
            cover,
            banner,
            synopsis,
            episodeCount,
            status,
            score,
            scoreText,
            genres,
            episodeLabel,
            releaseInfo,
            0,
            0,
            studio,
            producer,
            duration,
            extractYear(releaseInfo),
            episodeCount,
            "",
            sourceUrl
        );

        List<Episode> episodes = parseEpisodeList(doc, cover, duration);
        if (episodes.isEmpty()) {
            throw new IOException("Anichin episode list not found");
        }

        return new ApiClient.AnimeDetail(
            anime,
            episodes,
            studio,
            producer,
            "Donghua",
            duration,
            releaseInfo
        );
    }

    public StreamInfo getStreamInfo(String episodeSlugOrUrl) throws IOException {
        String episodeUrl = buildEpisodeUrl(episodeSlugOrUrl);
        Document doc = fetchDocument(episodeUrl);

        String title = safe(textOf(doc.selectFirst("h1.entry-title")));
        String episodeSlug = extractEpisodeSlug(doc.location());
        if (episodeSlug.isEmpty()) {
            episodeSlug = stripProviderPrefix(episodeSlugOrUrl);
        }

        Set<String> streamUrls = new LinkedHashSet<>();
        Element primaryIframe = doc.selectFirst("div#pembed iframe[src], div.player-embed iframe[src]");
        if (primaryIframe != null) {
            String url = firstNonBlank(primaryIframe.absUrl("src"), primaryIframe.attr("src"));
            if (!url.isEmpty()) {
                streamUrls.add(url);
            }
        }

        for (Element option : doc.select("select.mirror option[value]")) {
            String encoded = safe(option.attr("value"));
            if (encoded.isEmpty()) {
                continue;
            }
            String decodedHtml = decodeBase64(encoded);
            if (decodedHtml.isEmpty()) {
                continue;
            }
            Document fragment = Jsoup.parseBodyFragment(decodedHtml, BASE_URL);
            Element iframe = fragment.selectFirst("iframe[src]");
            if (iframe == null) {
                continue;
            }
            String url = firstNonBlank(iframe.absUrl("src"), iframe.attr("src"));
            if (!url.isEmpty()) {
                streamUrls.add(url);
            }
        }

        Map<String, List<String>> downloadMap = new LinkedHashMap<>();
        for (Element row : doc.select("div.soraurlx")) {
            String quality = firstNonBlank(textOf(row.selectFirst("strong")), "Default");
            Set<String> urls = new LinkedHashSet<>();
            for (Element link : row.select("a[href]")) {
                String url = firstNonBlank(link.absUrl("href"), link.attr("href"));
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
            if (!urls.isEmpty()) {
                List<String> existing = downloadMap.get(quality);
                if (existing == null) {
                    downloadMap.put(quality, new ArrayList<>(urls));
                } else {
                    for (String url : urls) {
                        if (!existing.contains(url)) {
                            existing.add(url);
                        }
                    }
                }
            }
        }

        String prevSlug = extractEpisodeSlug(firstNonBlank(
            attrOf(doc.selectFirst("div.naveps a[rel=prev][href]"), "href"),
            attrOf(doc.selectFirst("div.naveps.bignav a[rel=prev][href]"), "href")
        ));
        String nextSlug = extractEpisodeSlug(firstNonBlank(
            attrOf(doc.selectFirst("div.naveps a[rel=next][href]"), "href"),
            attrOf(doc.selectFirst("div.naveps.bignav a[rel=next][href]"), "href")
        ));
        String animeSlug = extractSeriesSlug(firstNonBlank(
            attrOf(doc.selectFirst("div.naveps a[href*='/seri/']"), "href"),
            attrOf(doc.selectFirst("div.ts-breadcrumb a[href*='/seri/']"), "href")
        ));

        List<String> streams = new ArrayList<>(streamUrls);
        if (streams.isEmpty() && downloadMap.isEmpty()) {
            throw new IOException("Anichin stream and download links unavailable");
        }

        return new StreamInfo(
            title,
            addProviderPrefix(episodeSlug),
            streams,
            downloadMap,
            emptyToNull(addProviderPrefix(prevSlug)),
            emptyToNull(addProviderPrefix(nextSlug)),
            emptyToNull(addProviderPrefix(animeSlug))
        );
    }

    private List<Anime> parseSeriesCards(Document doc, String defaultStatus, boolean synthesizeScores) {
        Elements cards = doc.select("div.listupd article.bs");
        if (cards.isEmpty()) {
            cards = doc.select("article.bs");
        }

        LinkedHashMap<String, Anime> unique = new LinkedHashMap<>();
        int rank = 0;
        for (Element card : cards) {
            Element link = card.selectFirst("a[href]");
            if (link == null) {
                continue;
            }

            String seriesUrl = firstNonBlank(link.absUrl("href"), link.attr("href"));
            String slug = extractSeriesSlug(seriesUrl);
            if (slug.isEmpty()) {
                continue;
            }

            String title = firstNonBlank(safe(link.attr("title")), textOf(card.selectFirst("h2")), textOf(card.selectFirst("div.tt")));
            if (title.isEmpty()) {
                continue;
            }

            String status = normalizeStatus(firstNonBlank(
                textOf(card.selectFirst("span.epx")),
                textOf(card.selectFirst("div.status")),
                defaultStatus
            ));
            String cover = firstNonBlank(
                absImageUrl(card.selectFirst("img")),
                attrOf(card.selectFirst("img"), "data-src"),
                attrOf(card.selectFirst("img"), "src")
            );
            String episodeLabel = firstNonBlank(textOf(card.selectFirst("span.epx")), status);
            int episodeCount = parseEpisodeNumber(episodeLabel);

            double score = parseScore(firstNonBlank(
                textOf(card.selectFirst(".upscore")),
                textOf(card.selectFirst(".rating strong")),
                textOf(card.selectFirst(".rating"))
            ));

            if (score <= 0d && synthesizeScores) {
                score = Math.max(7.0d, 9.8d - (rank * 0.05d));
            }

            String scoreText = score > 0d ? String.format(Locale.US, "%.2f", score) : "-";
            Anime anime = new Anime(
                Math.abs(slug.hashCode()),
                addProviderPrefix(slug),
                title,
                cover,
                cover,
                "",
                episodeCount,
                status,
                score,
                scoreText,
                Collections.emptyList(),
                episodeLabel,
                "",
                0,
                0,
                "-",
                "-",
                "-",
                extractYear(""),
                episodeCount,
                "",
                seriesUrl
            );

            if (!unique.containsKey(slug)) {
                unique.put(slug, anime);
                rank++;
            }
        }

        return new ArrayList<>(unique.values());
    }

    private List<Episode> parseEpisodeList(Document doc, String fallbackThumbnail, String fallbackDuration) {
        Map<String, Episode> episodes = new LinkedHashMap<>();
        for (Element item : doc.select("div.eplister ul li a[href]")) {
            String episodeUrl = firstNonBlank(item.absUrl("href"), item.attr("href"));
            String episodeSlug = extractEpisodeSlug(episodeUrl);
            if (episodeSlug.isEmpty()) {
                continue;
            }

            int number = parseEpisodeNumber(firstNonBlank(
                textOf(item.selectFirst(".epl-num")),
                textOf(item.selectFirst(".epl-title")),
                textOf(item)
            ));
            String title = firstNonBlank(textOf(item.selectFirst(".epl-title")), textOf(item), "Episode " + Math.max(number, 1));
            String releaseDate = firstNonBlank(textOf(item.selectFirst(".epl-date")), "-");
            boolean released = !releaseDate.toLowerCase(Locale.US).contains("coming");
            String prefixedSlug = addProviderPrefix(episodeSlug);

            episodes.put(prefixedSlug, new Episode(
                number,
                normalizeText(title),
                prefixedSlug,
                ApiClient.formatReleaseDate(releaseDate),
                prefixedSlug,
                fallbackThumbnail,
                fallbackDuration,
                released
            ));
        }

        for (Element item : doc.select("div.lastend .inepcx a[href]")) {
            String episodeUrl = firstNonBlank(item.absUrl("href"), item.attr("href"));
            String episodeSlug = extractEpisodeSlug(episodeUrl);
            if (episodeSlug.isEmpty()) {
                continue;
            }
            int number = parseEpisodeNumber(textOf(item));
            String prefixedSlug = addProviderPrefix(episodeSlug);
            if (episodes.containsKey(prefixedSlug)) {
                continue;
            }
            episodes.put(prefixedSlug, new Episode(
                number,
                normalizeText(textOf(item)),
                prefixedSlug,
                "-",
                prefixedSlug,
                fallbackThumbnail,
                fallbackDuration,
                true
            ));
        }

        List<Episode> merged = new ArrayList<>(episodes.values());
        merged.sort((left, right) -> Integer.compare(right.getEpisodeNumber(), left.getEpisodeNumber()));
        return merged;
    }

    private Map<String, String> parseSpecs(Elements spans) {
        Map<String, String> specs = new LinkedHashMap<>();
        if (spans == null || spans.isEmpty()) {
            return specs;
        }

        for (Element span : spans) {
            Element labelEl = span.selectFirst("b");
            if (labelEl == null) {
                continue;
            }
            String label = safe(labelEl.text()).replace(":", "").toLowerCase(Locale.US);
            if (label.isEmpty()) {
                continue;
            }

            String value = safe(span.ownText());
            if (value.isEmpty()) {
                value = safe(span.text()).replace(labelEl.text(), "").trim();
            }
            if (!value.isEmpty()) {
                specs.put(label, value);
            }
        }
        return specs;
    }

    private Document fetchDocument(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Anichin request failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Anichin response is empty");
            }

            String html = body.string();
            if (safe(html).isEmpty()) {
                throw new IOException("Anichin response body is blank");
            }
            return Jsoup.parse(html, url);
        }
    }

    private String buildSeriesUrl(String slugOrUrl) {
        String value = stripProviderPrefix(slugOrUrl);
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return ensureTrailingSlash(value);
        }

        if (value.startsWith("seri/")) {
            value = value.substring("seri/".length());
        }
        return BASE_URL + "/seri/" + value + "/";
    }

    private String buildEpisodeUrl(String slugOrUrl) {
        String value = stripProviderPrefix(slugOrUrl);
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return ensureTrailingSlash(value);
        }

        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return BASE_URL + "/" + value + "/";
    }

    private static String ensureTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url;
        }
        return url + "/";
    }

    private static String extractSeriesSlug(String url) {
        String path = pathFromUrl(url);
        if (path.isEmpty()) {
            return "";
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("seri".equals(parts[i]) && i + 1 < parts.length) {
                return safe(parts[i + 1]);
            }
        }
        return "";
    }

    private static String extractEpisodeSlug(String url) {
        String path = pathFromUrl(url);
        if (path.isEmpty()) {
            return "";
        }
        String[] parts = path.split("/");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            String cleaned = safe(part);
            if (!cleaned.isEmpty()) {
                segments.add(cleaned);
            }
        }
        if (segments.isEmpty()) {
            return "";
        }
        if ("seri".equals(segments.get(0))) {
            return "";
        }
        return segments.get(segments.size() - 1);
    }

    private static String pathFromUrl(String rawUrl) {
        String value = safe(rawUrl);
        if (value.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(value);
            return safe(uri.getPath());
        } catch (URISyntaxException ignored) {
            int protocol = value.indexOf("://");
            if (protocol >= 0) {
                int slash = value.indexOf('/', protocol + 3);
                if (slash >= 0) {
                    return safe(value.substring(slash));
                }
                return "";
            }
            return value;
        }
    }

    private static int parseEpisodeNumber(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return 0;
        }
        Matcher matcher = EPISODE_NUMBER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return 0;
        }

        String first = safe(matcher.group(1));
        String second = safe(matcher.group(2));
        String numberText = first.isEmpty() ? second : first;
        try {
            return Integer.parseInt(numberText);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double parseScore(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return 0d;
        }

        Matcher matcher = SCORE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return 0d;
        }

        String number = matcher.group(1).replace(',', '.');
        try {
            double parsed = Double.parseDouble(number);
            if (parsed < 0d || parsed > 10d) {
                return 0d;
            }
            return parsed;
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static String normalizeStatus(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return "Unknown";
        }
        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("ongoing")) {
            return "Ongoing";
        }
        if (lower.contains("complete")) {
            return "Completed";
        }
        if (lower.contains("upcoming") || lower.contains("coming")) {
            return "Upcoming";
        }
        return value;
    }

    private static String normalizeDuration(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return "-";
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(value);
        if (matcher.find()) {
            return matcher.group(1) + " menit";
        }
        return value;
    }

    private static String extractYear(String raw) {
        String value = safe(raw);
        Matcher matcher = YEAR_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "-";
    }

    private static String decodeBase64(String input) {
        String value = safe(input);
        if (value.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String absImageUrl(Element img) {
        if (img == null) {
            return "";
        }
        return firstNonBlank(
            img.absUrl("src"),
            img.absUrl("data-src"),
            img.attr("src"),
            img.attr("data-src")
        );
    }

    private static String textOf(Element element) {
        return element == null ? "" : safe(element.text());
    }

    private static String attrOf(Element element, String attr) {
        if (element == null) {
            return "";
        }
        return safe(element.absUrl(attr).isEmpty() ? element.attr(attr) : element.absUrl(attr));
    }

    private static String normalizeText(String text) {
        return safe(text).replace("\u00A0", " ").replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String cleaned = safe(value);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private static String emptyToNull(String value) {
        String cleaned = safe(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String addProviderPrefix(String slug) {
        String value = safe(slug);
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith(SLUG_PREFIX)) {
            return value;
        }
        return SLUG_PREFIX + value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
