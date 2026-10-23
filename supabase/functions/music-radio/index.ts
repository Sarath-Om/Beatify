// supabase/functions/music-radio/index.ts — music playlist + live radio RSS (lean version, keyless).
//   GET /music-radio                    RSS 2.0: Vividh Bharati Live + YouTube Music Global Top 50 (newest 15)
//   GET ?format=json                    same entries as JSON
//   GET ?playlist_id=PL...  ?count=1..15  any other PUBLIC YouTube / YouTube Music playlist
// YouTube Shorts are never listed. Real data only; source failures are reported, never hidden.
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const DEFAULT_PLAYLIST = 'PL4fGSI1pDJn5kI81J1fYWK5eZRl1zVM5v' // YouTube Music: Global Top 50 Songs
const PLAYLIST_RE = /^(PL|OLAK5uy_|RD|UU|FL|LL)[A-Za-z0-9_-]{10,60}$/
const MAX_ITEMS = 15 // YouTube's public playlist feed returns the newest 15 entries
const FEED_MAX = 15
const TIMEOUT_MS = 6000
const TTL_MS = 5 * 60 * 1000
const UA = 'Mozilla/5.0 (compatible; SarathMusicBot/1.0; +https://search-sarath.openflip.in)'

const RADIO_PAGE = 'https://akashvani.gov.in/radio/live.php'
const RADIO_EPG = 'https://cuesheets.prasarbharati.org/viewsheet/133'
const RADIO_HIGHLIGHTS = 'https://cuesheets.prasarbharati.org/highlights/133'
const RADIO_LOGO = 'https://dt89je83l7epy.cloudfront.net/images/vividh-bharati.jpg'

interface Entry {
  type: 'radio' | 'song'
  id: string
  title: string
  artist: string
  link: string // direct playback / player link
  stream_url: string // browser-playable audio stream when known, else ''
  embed_url: string
  thumbnail: string
  description: string
  published: string | null
  source: string
}

const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;')
const decode = (s: string) =>
  s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'").replace(/&amp;/g, '&')

const cache = new Map<string, { at: number; value: unknown }>()
async function cached<T>(key: string, fn: () => Promise<T>): Promise<T> {
  const hit = cache.get(key)
  if (hit && Date.now() - hit.at < TTL_MS) return hit.value as T
  const value = await fn()
  cache.set(key, { at: Date.now(), value })
  return value
}

const tag = (block: string, name: string): string => {
  const m = block.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, 'i'))
  return m ? decode(m[1].trim()) : ''
}
const attr = (block: string, name: string, a: string): string => {
  const m = block.match(new RegExp(`<${name}\\b[^>]*\\b${a}="([^"]*)"`, 'i'))
  return m ? decode(m[1]) : ''
}

// "Artist - Song (Official Video)" → { artist, song }. Falls back to the uploader channel
// ("Artist - Topic" auto-generated channels carry the real artist name).
export function splitTitle(rawTitle: string, channel: string): { song: string; artist: string } {
  const chan = channel.replace(/\s*-\s*Topic$/i, '').replace(/VEVO$/i, '').trim()
  const m = rawTitle.match(/^(.{1,80}?)\s+[-–—]\s+(.+)$/)
  if (m) return { artist: m[1].trim(), song: m[2].trim() }
  return { artist: chan, song: rawTitle.trim() }
}

// YouTube Shorts are never listed: the feed link is /shorts/<id>, or the video is tagged #shorts.
export function isShort(block: string, title: string): boolean {
  const href = attr(block, 'link', 'href')
  const desc = tag(block, 'media:description').slice(0, 200)
  return /\/shorts\//i.test(href) || /#shorts?\b/i.test(`${title} ${desc}`)
}

export function parsePlaylistFeed(xml: string, count: number): Entry[] {
  const out: Entry[] = []
  for (const block of xml.split(/<entry>/i).slice(1)) {
    if (out.length >= count) break
    const id = tag(block, 'yt:videoId')
    const title = tag(block, 'title')
    if (!/^[A-Za-z0-9_-]{11}$/.test(id) || !title) continue
    if (isShort(block, title)) continue
    const channel = tag(block.slice(block.indexOf('<author>')), 'name')
    const { song, artist } = splitTitle(title, channel)
    const published = tag(block, 'published')
    const ms = Date.parse(published)
    out.push({
      type: 'song',
      id,
      title: song,
      artist,
      link: `https://music.youtube.com/watch?v=${id}`,
      stream_url: '',
      embed_url: `https://www.youtube-nocookie.com/embed/${id}`,
      thumbnail: attr(block, 'media:thumbnail', 'url') || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
      description: `${song} — ${artist}`,
      published: Number.isNaN(ms) ? null : new Date(ms).toISOString(),
      source: 'YouTube Music',
    })
  }
  return out
}

async function fetchSongs(playlistId: string, count: number): Promise<Entry[]> {
  count = Math.min(count, FEED_MAX)
  return cached(`yt:${playlistId}:${count}`, async () => {
    const r = await fetch(`https://www.youtube.com/feeds/videos.xml?playlist_id=${encodeURIComponent(playlistId)}`, {
      headers: { 'User-Agent': UA, Accept: 'application/atom+xml, application/xml, text/xml' },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (r.status === 404) throw new Error('playlist not found or not public')
    if (!r.ok) throw new Error(`YouTube feed HTTP ${r.status}`)
    const songs = parsePlaylistFeed(await r.text(), count)
    if (songs.length === 0) throw new Error('playlist feed had no songs')
    return songs
  })
}

// Looks for a plain audio stream in the official page's static HTML. Returns '' when none is exposed.
async function discoverStream(): Promise<{ stream: string; note: string }> {
  const fromEnv = (Deno.env.get('VIVIDH_BHARATI_STREAM_URL') ?? '').trim()
  if (/^https?:\/\//i.test(fromEnv)) return { stream: fromEnv, note: 'stream from VIVIDH_BHARATI_STREAM_URL' }
  try {
    const r = await fetch(RADIO_PAGE, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(TIMEOUT_MS) })
    if (!r.ok) return { stream: '', note: `Akashvani page HTTP ${r.status}` }
    const html = await r.text()
    const near = html.match(/vividh[\s\S]{0,1500}?(https?:\/\/[^\s"'<>\\]+\.(?:m3u8|mp3|aac|pls)[^\s"'<>\\]*)/i)
    if (near) return { stream: near[1], note: 'stream auto-detected on akashvani.gov.in' }
    return { stream: '', note: 'Akashvani does not expose a stream URL in its page; linking the official player' }
  } catch (err) {
    return { stream: '', note: `Akashvani page unreachable: ${err instanceof Error ? err.message : String(err)}` }
  }
}

async function radioEntry(): Promise<{ entry: Entry; note: string }> {
  const { stream, note } = await cached('radio', discoverStream)
  const entry: Entry = {
    type: 'radio',
    id: 'vividh-bharati',
    title: 'Vividh Bharati Live',
    artist: 'Akashvani · All India Radio',
    link: RADIO_PAGE,
    stream_url: stream,
    embed_url: '',
    thumbnail: RADIO_LOGO,
    description:
      'Live Hindi film-music radio from Akashvani (Prasar Bharati). ' +
      `Open the official player: ${RADIO_PAGE} · Today's programme schedule: ${RADIO_EPG} · Highlights: ${RADIO_HIGHLIGHTS}. ` +
      'A live "now playing" title is not published by the broadcaster; see the schedule for what is on air.',
    published: null,
    source: 'Akashvani',
  }
  return { entry, note }
}

function toRss(entries: Entry[], self: string, warnings: string[]): string {
  const now = new Date().toUTCString()
  const items = entries
    .map((e) => {
      const pub = e.published ? `<pubDate>${new Date(e.published).toUTCString()}</pubDate>` : `<pubDate>${now}</pubDate>`
      const enclosure = e.stream_url ? `<enclosure url="${esc(e.stream_url)}" type="audio/mpeg" length="0"/>` : ''
      const player = e.embed_url ? `<media:player url="${esc(e.embed_url)}"/>` : ''
      return `    <item>
      <title>${esc(e.type === 'radio' ? e.title : `${e.title} — ${e.artist}`)}</title>
      <link>${esc(e.link)}</link>
      <guid isPermaLink="${e.type === 'song'}">${esc(e.type === 'song' ? e.link : `urn:vividh-bharati:live`)}</guid>
      ${pub}
      <category>${e.type === 'radio' ? 'Live Radio' : 'Music'}</category>
      <source url="${esc(self)}">${esc(e.source)}</source>
      <description>${esc(e.description)}</description>
      <media:title>${esc(e.title)}</media:title>
      <media:credit role="artist">${esc(e.artist)}</media:credit>
      <media:thumbnail url="${esc(e.thumbnail)}"/>
      ${player}${enclosure}
    </item>`
    })
    .join('\n')
  const warn = warnings.length ? `\n    <!-- ${warnings.map((w) => w.replace(/--/g, '-')).join(' | ')} -->` : ''
  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>Sarath Music &amp; Radio</title>
    <link>${esc(RADIO_PAGE)}</link>
    <description>Vividh Bharati live radio and YouTube Music Global Top 50 songs.</description>
    <language>en</language>
    <lastBuildDate>${now}</lastBuildDate>
    <atom:link href="${esc(self)}" rel="self" type="application/rss+xml"/>${warn}
${items}
  </channel>
</rss>
`
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'GET') return new Response(JSON.stringify({ error: 'use GET' }), { status: 405, headers: { ...cors, 'Content-Type': 'application/json' } })

  const u = new URL(req.url)
  const playlist = (u.searchParams.get('playlist_id') ?? DEFAULT_PLAYLIST).trim()
  if (!PLAYLIST_RE.test(playlist)) {
    return new Response(JSON.stringify({ error: 'invalid_playlist_id' }), { status: 400, headers: { ...cors, 'Content-Type': 'application/json' } })
  }
  const n = Number(u.searchParams.get('count'))
  const count = Number.isFinite(n) && n > 0 ? Math.min(Math.floor(n), MAX_ITEMS) : MAX_ITEMS
  const format = (u.searchParams.get('format') ?? 'rss').toLowerCase()

  const [songsR, radioR] = await Promise.allSettled([fetchSongs(playlist, count), radioEntry()])
  const warnings: string[] = []
  const entries: Entry[] = []
  if (radioR.status === 'fulfilled') {
    entries.push(radioR.value.entry)
    if (!radioR.value.entry.stream_url) warnings.push(radioR.value.note)
  } else warnings.push(`radio: ${String(radioR.reason)}`)
  if (songsR.status === 'fulfilled') entries.push(...songsR.value)
  else warnings.push(`songs: ${songsR.reason instanceof Error ? songsR.reason.message : String(songsR.reason)}`)

  const songCount = entries.filter((e) => e.type === 'song').length
  if (songCount === 0) {
    // Radio entry (if any) is still returned, but a total music failure is signalled to the app.
    console.error('music-radio: no songs', warnings)
  }
  const self = `${u.origin}${u.pathname}${u.search}`

  if (format === 'json') {
    return new Response(JSON.stringify({ source: 'music-radio', mode: 'playlist', playlist, count: entries.length, songs: songCount, entries, ...(warnings.length ? { warnings } : {}) }), {
      status: songCount === 0 ? 502 : 200,
      headers: { ...cors, 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=300' },
    })
  }
  return new Response(toRss(entries, self, warnings), {
    status: songCount === 0 ? 502 : 200,
    headers: { ...cors, 'Content-Type': 'application/rss+xml; charset=utf-8', 'Cache-Control': 'public, max-age=300' },
  })
})
