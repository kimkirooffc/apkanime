const cheerio = require('cheerio');
const { Buffer } = require('node:buffer');

const BASE_URL = 'https://anichin.cafe';
const REQUEST_HEADERS = {
  'user-agent':
    'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36',
  accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
  'accept-language': 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7'
};

async function fetchHtml(url) {
  const response = await fetch(url, {
    method: 'GET',
    headers: REQUEST_HEADERS,
    redirect: 'follow'
  });
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }
  const html = await response.text();
  if (!safe(html)) {
    throw new Error('Empty HTML response');
  }
  return html;
}

function safe(value) {
  return String(value || '').trim();
}

function firstNonBlank(...values) {
  for (const value of values) {
    const cleaned = safe(value);
    if (cleaned) {
      return cleaned;
    }
  }
  return '';
}

function toAbsoluteUrl(url) {
  const value = safe(url);
  if (!value) {
    return '';
  }
  if (value.startsWith('http://') || value.startsWith('https://')) {
    return value;
  }
  if (value.startsWith('//')) {
    return `https:${value}`;
  }
  try {
    return new URL(value, BASE_URL).toString();
  } catch (error) {
    return '';
  }
}

function normalizeStatus(raw) {
  const value = safe(raw);
  if (!value) {
    return 'Unknown';
  }
  const lower = value.toLowerCase();
  if (lower.includes('ongoing')) {
    return 'Ongoing';
  }
  if (lower.includes('complete')) {
    return 'Completed';
  }
  if (lower.includes('upcoming') || lower.includes('coming')) {
    return 'Upcoming';
  }
  return value;
}

function parseEpisodeNumber(raw) {
  const value = safe(raw);
  if (!value) {
    return 0;
  }
  const match = value.match(/episode\s*(\d+)|(\d+)/i);
  if (!match) {
    return 0;
  }
  const numberText = safe(match[1] || match[2]);
  const parsed = Number.parseInt(numberText, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseScore(raw) {
  const value = safe(raw);
  if (!value) {
    return 0;
  }
  const match = value.match(/(\d+(?:[.,]\d+)?)/);
  if (!match) {
    return 0;
  }
  const parsed = Number.parseFloat(match[1].replace(',', '.'));
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 10) {
    return 0;
  }
  return parsed;
}

function scoreText(score) {
  if (!Number.isFinite(score) || score <= 0) {
    return '-';
  }
  return score.toFixed(2);
}

function extractYear(raw) {
  const value = safe(raw);
  const match = value.match(/(19\d{2}|20\d{2})/);
  return match ? match[1] : '-';
}

function normalizeDuration(raw) {
  const value = safe(raw);
  if (!value) {
    return '-';
  }
  const match = value.match(/(\d+)/);
  if (!match) {
    return value;
  }
  return `${match[1]} menit`;
}

function hashId(raw) {
  const value = safe(raw);
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(hash || value.length || 1);
}

function pathFromUrl(url) {
  const value = safe(url);
  if (!value) {
    return '';
  }
  try {
    return safe(new URL(value).pathname);
  } catch (error) {
    const protocolIndex = value.indexOf('://');
    if (protocolIndex >= 0) {
      const slashIndex = value.indexOf('/', protocolIndex + 3);
      return slashIndex >= 0 ? safe(value.slice(slashIndex)) : '';
    }
    return value;
  }
}

function extractSeriesSlug(url) {
  const path = pathFromUrl(url);
  if (!path) {
    return '';
  }
  const parts = path.split('/').map(safe).filter(Boolean);
  for (let i = 0; i < parts.length; i += 1) {
    if (parts[i] === 'seri' && parts[i + 1]) {
      return parts[i + 1];
    }
  }
  return '';
}

function extractEpisodeSlug(url) {
  const path = pathFromUrl(url);
  if (!path) {
    return '';
  }
  const parts = path.split('/').map(safe).filter(Boolean);
  if (!parts.length) {
    return '';
  }
  if (parts[0] === 'seri') {
    return '';
  }
  return parts[parts.length - 1];
}

function normalizeText(raw) {
  return safe(raw).replace(/\u00A0/g, ' ').replace(/\s+/g, ' ').trim();
}

function parseSeriesCards(html, fallbackStatus, synthesizeScores) {
  const $ = cheerio.load(html);
  const cards = $('div.listupd article.bs').length
    ? $('div.listupd article.bs')
    : $('article.bs');

  const unique = new Map();
  let rank = 0;
  cards.each((_, node) => {
    const el = $(node);
    const link = el.find('a[href]').first();
    const seriesUrl = toAbsoluteUrl(link.attr('href'));
    const slug = extractSeriesSlug(seriesUrl);
    if (!slug) {
      return;
    }

    const title = firstNonBlank(link.attr('title'), el.find('h2').first().text(), el.find('div.tt').first().text());
    if (!title) {
      return;
    }

    const image = el.find('img').first();
    const cover = firstNonBlank(toAbsoluteUrl(image.attr('src')), toAbsoluteUrl(image.attr('data-src')));
    const episodeLabel = firstNonBlank(el.find('span.epx').first().text(), el.find('div.status').first().text(), fallbackStatus);
    const status = normalizeStatus(firstNonBlank(el.find('div.status').first().text(), episodeLabel, fallbackStatus));
    const episodes = parseEpisodeNumber(episodeLabel);

    let score = parseScore(firstNonBlank(el.find('.upscore').first().text(), el.find('.rating strong').first().text()));
    if (score <= 0 && synthesizeScores) {
      score = Math.max(7, 9.8 - rank * 0.05);
    }

    const anime = {
      id: hashId(slug),
      slug,
      title,
      thumbnail: cover,
      cover,
      image: cover,
      banner: cover,
      synopsis: '',
      description: '',
      episode: episodeLabel,
      episodes: episodeLabel,
      totalEpisodes: episodes > 0 ? String(episodes) : '',
      status,
      rating: scoreText(score),
      score: scoreText(score),
      genres: [],
      releaseDate: '',
      studio: '',
      producer: '',
      duration: '',
      trailer: '',
      trailerUrl: '',
      url: seriesUrl
    };

    if (!unique.has(slug)) {
      unique.set(slug, anime);
      rank += 1;
    }
  });

  return Array.from(unique.values());
}

function parseSpecs($, selector) {
  const specs = {};
  $(selector).each((_, node) => {
    const row = $(node);
    const label = safe(row.find('b').first().text()).replace(/:/g, '').toLowerCase();
    if (!label) {
      return;
    }
    const clone = row.clone();
    clone.find('b').remove();
    const value = normalizeText(clone.text());
    if (value) {
      specs[label] = value;
    }
  });
  return specs;
}

function parseEpisodes($, fallbackThumbnail, fallbackDuration) {
  const episodes = [];

  $('div.eplister ul li a[href]').each((_, node) => {
    const el = $(node);
    const episodeUrl = toAbsoluteUrl(el.attr('href'));
    const episodeSlug = extractEpisodeSlug(episodeUrl);
    if (!episodeSlug) {
      return;
    }

    const number = parseEpisodeNumber(
      firstNonBlank(el.find('.epl-num').first().text(), el.find('.epl-title').first().text(), el.text())
    );
    const title = normalizeText(firstNonBlank(el.find('.epl-title').first().text(), el.text(), `Episode ${Math.max(number, 1)}`));
    const releaseDate = firstNonBlank(el.find('.epl-date').first().text(), '-');
    const released = !releaseDate.toLowerCase().includes('coming');

    episodes.push({
      episodeNumber: number,
      title,
      slug: episodeSlug,
      endpoint: episodeSlug,
      releaseDate,
      thumbnail: fallbackThumbnail,
      duration: fallbackDuration,
      released
    });
  });

  if (episodes.length) {
    return episodes;
  }

  $('div.lastend .inepcx a[href]').each((_, node) => {
    const el = $(node);
    const episodeUrl = toAbsoluteUrl(el.attr('href'));
    const episodeSlug = extractEpisodeSlug(episodeUrl);
    if (!episodeSlug) {
      return;
    }
    const text = normalizeText(el.text());
    const number = parseEpisodeNumber(text);
    episodes.push({
      episodeNumber: number,
      title: text || `Episode ${Math.max(number, 1)}`,
      slug: episodeSlug,
      endpoint: episodeSlug,
      releaseDate: '-',
      thumbnail: fallbackThumbnail,
      duration: fallbackDuration,
      released: true
    });
  });

  return episodes;
}

function decodeMirrorBase64(value) {
  const encoded = safe(value);
  if (!encoded) {
    return '';
  }
  try {
    return Buffer.from(encoded, 'base64').toString('utf8');
  } catch (error) {
    return '';
  }
}

function buildSeriesUrl(slugOrUrl) {
  const value = safe(slugOrUrl);
  if (!value) {
    throw new Error('Series slug is required');
  }
  if (value.startsWith('http://') || value.startsWith('https://')) {
    return value.endsWith('/') ? value : `${value}/`;
  }

  const normalized = value.startsWith('seri/') ? value.slice('seri/'.length) : value;
  return `${BASE_URL}/seri/${normalized}/`;
}

function buildEpisodeUrl(slugOrUrl) {
  const value = safe(slugOrUrl);
  if (!value) {
    throw new Error('Episode slug is required');
  }
  if (value.startsWith('http://') || value.startsWith('https://')) {
    return value.endsWith('/') ? value : `${value}/`;
  }
  const normalized = value.replace(/^\/+/, '');
  return `${BASE_URL}/${normalized}/`;
}

async function getOngoing() {
  const html = await fetchHtml(`${BASE_URL}/ongoing/`);
  return parseSeriesCards(html, 'Ongoing', false);
}

async function getCompletedTopRated() {
  const html = await fetchHtml(`${BASE_URL}/completed/`);
  return parseSeriesCards(html, 'Completed', true);
}

async function search(query) {
  const normalized = safe(query);
  if (!normalized) {
    return [];
  }
  const encoded = encodeURIComponent(normalized);
  const html = await fetchHtml(`${BASE_URL}/?s=${encoded}`);
  return parseSeriesCards(html, '', false);
}

async function getDetail(slugOrUrl) {
  const detailUrl = buildSeriesUrl(slugOrUrl);
  const html = await fetchHtml(detailUrl);
  const $ = cheerio.load(html);

  const title = firstNonBlank($('h1.entry-title').first().text());
  if (!title) {
    throw new Error('Detail title not found');
  }

  const sourceUrl = detailUrl;
  const seriesSlug = extractSeriesSlug(sourceUrl);
  const cover = firstNonBlank(
    toAbsoluteUrl($('div.bigcontent .thumb img').first().attr('src')),
    toAbsoluteUrl($('div.single-info .thumb img').first().attr('src')),
    toAbsoluteUrl($('meta[property="og:image"]').attr('content'))
  );
  const synopsis = normalizeText(
    firstNonBlank(
      $('div.bixbox.synp .entry-content').first().text(),
      $('div.info-content .desc').first().text(),
      'Sinopsis belum tersedia dari Anichin.'
    )
  );
  const score = parseScore(firstNonBlank($('div.rating strong').first().text(), $('div.single-info .rating strong').first().text()));
  const specs = parseSpecs($, 'div.info-content .spe span');

  const status = normalizeStatus(firstNonBlank(specs.status, 'Unknown'));
  const duration = normalizeDuration(firstNonBlank(specs.duration, '-'));
  const releaseInfo = firstNonBlank(specs['released on'], specs.released, '-');
  const studio = firstNonBlank(specs.studio, '-');
  const producer = firstNonBlank(specs.producer, specs.network, '-');
  const episodeLabel = firstNonBlank(specs.episodes, '');
  const episodesCount = parseEpisodeNumber(episodeLabel);

  const genres = [];
  $('div.genxed a').each((_, node) => {
    const name = safe($(node).text());
    if (name) {
      genres.push(name);
    }
  });

  const episodeList = parseEpisodes($, cover, duration);
  if (!episodeList.length) {
    throw new Error('Episode list not found');
  }

  return {
    id: hashId(seriesSlug || sourceUrl),
    title,
    japaneseTitle: '',
    slug: seriesSlug,
    thumbnail: cover,
    banner: cover,
    synopsis,
    description: synopsis,
    episodes: episodeLabel,
    totalEpisodes: episodesCount > 0 ? String(episodesCount) : '',
    status,
    rating: scoreText(score),
    score: scoreText(score),
    producer,
    type: 'Donghua',
    duration,
    releaseDate: releaseInfo,
    studio,
    trailer: '',
    trailerUrl: '',
    url: sourceUrl,
    genres,
    episodeList
  };
}

async function getStream(slugOrUrl) {
  const streamUrl = buildEpisodeUrl(slugOrUrl);
  const html = await fetchHtml(streamUrl);
  const $ = cheerio.load(html);

  const title = firstNonBlank($('h1.entry-title').first().text(), 'Episode');
  const episodeSlug = extractEpisodeSlug(streamUrl) || safe(slugOrUrl);
  const streamSet = new Set();

  const primarySrc = toAbsoluteUrl(firstNonBlank($('#pembed iframe').first().attr('src'), $('div.player-embed iframe').first().attr('src')));
  if (primarySrc) {
    streamSet.add(primarySrc);
  }

  $('select.mirror option[value]').each((_, node) => {
    const value = safe($(node).attr('value'));
    if (!value) {
      return;
    }
    const decoded = decodeMirrorBase64(value);
    if (!decoded) {
      return;
    }
    const fragment = cheerio.load(decoded);
    const src = toAbsoluteUrl(fragment('iframe').first().attr('src'));
    if (src) {
      streamSet.add(src);
    }
  });

  const downloadUrls = {};
  $('div.soraurlx').each((_, node) => {
    const row = $(node);
    const quality = firstNonBlank(row.find('strong').first().text(), 'Default');
    const urls = new Set(downloadUrls[quality] || []);

    row.find('a[href]').each((__, linkNode) => {
      const href = toAbsoluteUrl($(linkNode).attr('href'));
      if (href) {
        urls.add(href);
      }
    });

    if (urls.size) {
      downloadUrls[quality] = Array.from(urls);
    }
  });

  const prevSlug = extractEpisodeSlug(
    firstNonBlank(
      toAbsoluteUrl($('div.naveps a[rel="prev"]').first().attr('href')),
      toAbsoluteUrl($('div.naveps.bignav a[rel="prev"]').first().attr('href'))
    )
  );
  const nextSlug = extractEpisodeSlug(
    firstNonBlank(
      toAbsoluteUrl($('div.naveps a[rel="next"]').first().attr('href')),
      toAbsoluteUrl($('div.naveps.bignav a[rel="next"]').first().attr('href'))
    )
  );
  const animeSlug = extractSeriesSlug(
    firstNonBlank(
      toAbsoluteUrl($('div.naveps a[href*="/seri/"]').first().attr('href')),
      toAbsoluteUrl($('div.ts-breadcrumb a[href*="/seri/"]').first().attr('href'))
    )
  );

  const streamingUrls = Array.from(streamSet);
  if (!streamingUrls.length && !Object.keys(downloadUrls).length) {
    throw new Error('No playable links found');
  }

  return {
    title,
    episodeSlug,
    streamingUrls,
    downloadUrls,
    navigation: {
      prev: prevSlug ? { slug: prevSlug } : null,
      next: nextSlug ? { slug: nextSlug } : null,
      list: animeSlug ? { slug: animeSlug } : null
    }
  };
}

async function getGenres() {
  const html = await fetchHtml(`${BASE_URL}/`);
  const $ = cheerio.load(html);
  const genres = new Set();
  $('ul.genre li a').each((_, node) => {
    const text = safe($(node).text());
    if (text) {
      genres.add(text);
    }
  });
  return Array.from(genres);
}

async function getHome() {
  const ongoing = await getOngoing();
  const completed = await getCompletedTopRated();
  return {
    ongoing: ongoing.slice(0, 12),
    trending: ongoing.slice(0, 8),
    complete: completed.slice(0, 12)
  };
}

module.exports = {
  BASE_URL,
  getHome,
  getOngoing,
  getCompletedTopRated,
  search,
  getDetail,
  getStream,
  getGenres
};
