import os
import yt_dlp
from yt_dlp.utils import DownloadCancelled
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# Android native library executable patch:
# On Android, FFmpeg is stored as libffmpeg.so in nativeLibraryDir.
# This patch ensures yt-dlp recognizes libffmpeg.so as the valid ffmpeg binary,
# and if ffprobe is absent, uses ffmpeg for codec probing.
_orig_determine = FFmpegPostProcessor._determine_executables

def _android_determine_executables(self):
    location = self.get_param('ffmpeg_location', self._ffmpeg_location.get())
    paths = _orig_determine(self)
    if location and os.path.isfile(location):
        paths['ffmpeg'] = location
        if 'ffprobe' not in paths or not os.path.exists(paths.get('ffprobe', '')):
            paths['ffprobe'] = location
    elif location and os.path.isdir(location):
        f_cand = os.path.join(location, 'libffmpeg.so')
        if not os.path.exists(paths.get('ffmpeg', '')) and os.path.exists(f_cand):
            paths['ffmpeg'] = f_cand
        if not os.path.exists(paths.get('ffprobe', '')):
            paths['ffprobe'] = paths.get('ffmpeg', f_cand)
    return paths

FFmpegPostProcessor._determine_executables = _android_determine_executables

def _format_duration(seconds):
    seconds = int(seconds or 0)
    m, s = divmod(seconds, 60)
    h, m = divmod(m, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"

def fetch_video_info(url):
    ydl_opts = {'quiet': True, 'no_warnings': True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        return {
            "title": info.get("title", "Unknown Video"),
            "thumbnailUrl": info.get("thumbnail", ""),
            "durationText": _format_duration(info.get("duration", 0)),
            "formats": [
                {"id": "full", "label": "Video + Audio (Best Quality)",
                 "subtitle": "Best available · MP4",
                 "sizeText": "Size depends on the best quality available"},
                {"id": "audio", "label": "Audio Only",
                 "subtitle": "M4A", "sizeText": "5-10 MB"},
                {"id": "fast", "label": "Fast Download",
                 "subtitle": "720p · MP4", "sizeText": "<50 MB"},
            ]
        }

def _format_bytes(bytes_count):
    if not bytes_count or bytes_count <= 0:
        return ""
    if bytes_count < 1024 * 1024:
        return f"{bytes_count / 1024:.1f} KB"
    elif bytes_count < 1024 * 1024 * 1024:
        return f"{bytes_count / (1024 * 1024):.1f} MB"
    else:
        return f"{bytes_count / (1024 * 1024 * 1024):.2f} GB"

def _format_speed(speed_bytes_sec):
    if not speed_bytes_sec or speed_bytes_sec <= 0:
        return ""
    if speed_bytes_sec < 1024 * 1024:
        return f"{speed_bytes_sec / 1024:.0f} KB/s"
    else:
        return f"{speed_bytes_sec / (1024 * 1024):.1f} MB/s"

def fetch_video(url, ffmpeg_dir, output_dir, format_type, callback):
    if ffmpeg_dir:
        native_dir = os.path.dirname(os.path.abspath(ffmpeg_dir)) if os.path.isfile(ffmpeg_dir) else os.path.abspath(ffmpeg_dir)
        old_ld = os.environ.get("LD_LIBRARY_PATH", "")
        if native_dir not in old_ld:
            os.environ["LD_LIBRARY_PATH"] = f"{native_dir}:{old_ld}".strip(":")

    stream_tracker = {
        "seen_audio": False,
        "video_height": None
    }

    def report(pct, st_code, st_text, dt_text, downloaded=0, total=0, speed=0):
        if callback.isCancelled():
            raise DownloadCancelled("User cancelled")
        try:
            callback.onProgressDetails(
                int(pct),
                str(st_code),
                str(st_text),
                str(dt_text),
                int(downloaded or 0),
                int(total or 0),
                int(speed or 0)
            )
        except Exception:
            callback.onProgress(int(pct), str(st_code))

    def progress_hook(d):
        if callback.isCancelled():
            raise DownloadCancelled("User cancelled")
        status = d.get('status')
        if status == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            speed = d.get('speed') or 0
            percent = int((downloaded / total) * 100) if total else 0

            info = d.get('info_dict') or {}
            vcodec = info.get('vcodec')
            acodec = info.get('acodec')
            height = info.get('height')
            if height and not stream_tracker.get("video_height"):
                stream_tracker["video_height"] = height
            filename = str(d.get('filename') or '').lower()

            speed_str = _format_speed(speed)
            if total > 0:
                size_detail = f"{_format_bytes(downloaded)} / {_format_bytes(total)}"
                if speed_str:
                    size_detail += f" • {speed_str}"
            elif downloaded > 0:
                size_detail = f"{_format_bytes(downloaded)} downloaded"
                if speed_str:
                    size_detail += f" • {speed_str}"
            else:
                size_detail = speed_str

            if format_type == "full":
                is_audio = stream_tracker["seen_audio"] or (acodec and acodec != 'none' and (not vcodec or vcodec == 'none')) or ".f140." in filename or ".m4a" in filename
                if is_audio:
                    stream_tracker["seen_audio"] = True
                    stage_text = "Step 2/2: Downloading audio"
                    report(percent, "downloading_audio", stage_text, size_detail, downloaded, total, speed)
                else:
                    vh = stream_tracker.get("video_height")
                    quality_label = f"{vh}p" if vh else "high quality"
                    stage_text = f"Step 1/2: Downloading {quality_label} video"
                    report(percent, "downloading_video", stage_text, size_detail, downloaded, total, speed)
            elif format_type == "audio":
                stage_text = "Downloading audio"
                report(percent, "downloading_audio", stage_text, size_detail, downloaded, total, speed)
            else:
                stage_text = "Downloading video (720p)"
                report(percent, "downloading_video", stage_text, size_detail, downloaded, total, speed)

        elif status == 'finished':
            downloaded = d.get('downloaded_bytes', 0)
            if format_type == "full" and not stream_tracker["seen_audio"]:
                report(100, "video_finished", "Step 1/2 finished · Preparing audio...", f"{_format_bytes(downloaded)} video ready", downloaded, downloaded, 0)
            else:
                report(100, "merging", "Merging video & audio with FFmpeg...", "Finalizing MP4 file", downloaded, downloaded, 0)

    def postprocessor_hook(d):
        if callback.isCancelled():
            raise DownloadCancelled("User cancelled")
        pp_status = d.get('status')
        if pp_status == 'started':
            report(100, "merging", "Merging video & audio with FFmpeg...", "Finalizing MP4 file", 0, 0, 0)

    base = {
        'progress_hooks': [progress_hook],
        'postprocessor_hooks': [postprocessor_hook],
        'ffmpeg_location': ffmpeg_dir,
        'outtmpl': f'{output_dir}/%(title)s_{format_type}.%(ext)s',
        'overwrites': True,
        'nopart': False,
        'socket_timeout': 30,
        'retries': 10,
        'fragment_retries': 10,
        'extractor_retries': 3,
    }

    if format_type == "full":
        opts = {**base, 'format': 'bestvideo+bestaudio/best', 'merge_output_format': 'mp4'}
    elif format_type == "audio":
        opts = {**base, 'format': 'bestaudio[ext=m4a]/bestaudio'}
    else:
        opts = {**base, 'format': 'best[height<=720][ext=mp4]/best[height<=720]'}

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)


# ---------------------------------------------------------------------------
# Playlist support (Feature 2)
#
# Kept deliberately shallow: extract_flat gives us titles/ids/durations in a
# single request instead of one deep extraction per video, which keeps the
# checklist screen fast even for 100+ item playlists.
# ---------------------------------------------------------------------------

def is_playlist(url):
    """True when the URL points at a playlist rather than a single video."""
    try:
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(url)
        query = parse_qs(parsed.query)
        list_id = (query.get("list") or [""])[0]
        if list_id and not list_id.startswith("RD"):  # RD* = autogenerated radio mix
            return True
        return "/playlist" in parsed.path
    except Exception:
        return False


def fetch_playlist_info(url):
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist',
        'skip_download': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)

    entries = []
    for entry in (info.get("entries") or []):
        if not entry:
            continue
        video_id = entry.get("id") or ""
        entry_url = entry.get("url") or entry.get("webpage_url") or ""
        if entry_url and not entry_url.startswith("http"):
            entry_url = "https://www.youtube.com/watch?v=" + entry_url
        if not entry_url and video_id:
            entry_url = "https://www.youtube.com/watch?v=" + video_id

        thumb = ""
        thumbs = entry.get("thumbnails") or []
        if thumbs:
            thumb = thumbs[-1].get("url", "")
        if not thumb and video_id:
            thumb = "https://i.ytimg.com/vi/%s/hqdefault.jpg" % video_id

        entries.append({
            "id": video_id or entry_url,
            "url": entry_url,
            "title": entry.get("title") or "Untitled",
            "thumbnailUrl": thumb,
            "durationText": _format_duration(entry.get("duration", 0)),
        })

    return {
        "title": info.get("title", "Playlist"),
        "entries": entries,
    }


# ---------------------------------------------------------------------------
# Spotify support (Features 3 & 4)
#
# Deliberately credential-free: we only touch Spotify's *public* oEmbed
# endpoint and the public track page HTML. No Web API, no client id/secret,
# no login. Playlists/albums are never enumerated — that would require the
# official API — so the app only shows an explanatory popup for those.
# ---------------------------------------------------------------------------

import json as _json
import re as _re
from html import unescape as _html_unescape
from urllib.parse import (
    urlparse as _urlparse,
    parse_qs as _parse_qs,
    quote as _quote,
    unquote as _unquote,
)
from urllib.error import HTTPError as _HTTPError
from urllib.request import Request as _Request, urlopen as _urlopen

# The app fetches Spotify from an Android device, so use a complete mobile
# Chrome profile rather than urllib's default or an incomplete desktop UA.
# Spotify serves the server-rendered metadata page to this profile instead of
# the tiny Web Player shell that some desktop/browser profiles receive.
_UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
       "(KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36")
_HTTP_HEADERS = {
    "User-Agent": _UA,
    "Accept": (
        "text/html,application/xhtml+xml,application/xml;q=0.9,"
        "image/avif,image/webp,image/apng,*/*;q=0.8"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


def spotify_link_type(url):
    """Returns 'track' | 'playlist' | 'album' | '' for a given URL."""
    try:
        parsed = _urlparse(url.strip())
        if "spotify.com" not in (parsed.netloc or ""):
            return ""
        path = parsed.path or ""
        for kind in ("track", "playlist", "album"):
            if _re.search(r"/%s/[A-Za-z0-9]+" % kind, path):
                return kind
        return ""
    except Exception:
        return ""


def _spotify_intent_fallback_url(location):
    """Extract and validate Spotify's HTTPS fallback from an intent redirect."""
    try:
        parsed = _urlparse(location or "")
        if (parsed.scheme or "").lower() != "intent":
            return ""

        params = _parse_qs(parsed.query, keep_blank_values=True)
        fallback = ""
        for key in ("S.browser_fallback_url", "$fallback_url"):
            values = params.get(key) or []
            if values and values[0]:
                fallback = _unquote(values[0]).strip()
                break

            # Android intent URIs commonly put their extras after the
            # "#Intent" fragment and separate them with semicolons, e.g.
            # "S.browser_fallback_url=<encoded-url>;S.market_referrer=...".
            # Match the raw value up to the next semicolon so encoded "&" and
            # "=" characters inside the nested URL stay part of the value.
            match = _re.search(
                r"(?:^|;)" + _re.escape(key) + r"=([^;]*)",
                parsed.fragment,
            )
            if match and match.group(1):
                fallback = _unquote(match.group(1)).strip()
                break
        if not fallback:
            return ""

        fallback_parsed = _urlparse(fallback)
        if (
            (fallback_parsed.scheme or "").lower() != "https"
            or (fallback_parsed.hostname or "").lower() != "open.spotify.com"
            or not spotify_link_type(fallback)
        ):
            return ""
        return fallback
    except Exception:
        return ""


def _http_get_once(url, timeout=15):
    req = _Request(url, headers=_HTTP_HEADERS)
    with _urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="ignore")


def _http_get(url, timeout=15):
    try:
        return _http_get_once(url, timeout=timeout)
    except _HTTPError as error:
        # Branch share links can redirect to an Android intent URI. urllib
        # cannot follow that non-HTTP scheme, but the intent carries the
        # public Spotify URL that a normal HTTP client should fetch instead.
        headers = getattr(error, "headers", None)
        location = headers.get("Location", "") if headers else ""
        fallback = _spotify_intent_fallback_url(location)
        if not fallback:
            raise
        return _http_get_once(fallback, timeout=timeout)


def _spotify_oembed_title(track_url):
    raw = _http_get("https://open.spotify.com/oembed?url=" + _quote(track_url, safe=""))
    data = _json.loads(raw)
    return data.get("title") or "", data.get("thumbnail_url") or ""


def _spotify_artist_from_page(track_url):
    """Best-effort artist extraction from the public track page's meta tags."""
    try:
        html = _http_get(track_url)
    except Exception:
        return ""
    patterns = [
        r'<meta\s+property="og:description"\s+content="([^"]+)"',
        r'<meta\s+name="description"\s+content="([^"]+)"',
        r'<meta\s+name="music:musician_description"\s+content="([^"]+)"',
    ]
    for pattern in patterns:
        m = _re.search(pattern, html, _re.IGNORECASE)
        if not m:
            continue
        desc = m.group(1)
        # Typical shapes: "Song · Artist · Song · 2020" or "Artist · Song · 2020"
        parts = [p.strip() for p in _re.split(r"·|\u00b7", desc) if p.strip()]
        for part in parts:
            low = part.lower()
            if low in ("song", "single", "album", "listen on spotify"):
                continue
            if _re.fullmatch(r"\d{4}", part):
                continue
            return part
    return ""


def _youtube_best_match(query):
    opts = {
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extract_flat': 'in_playlist',
        'default_search': 'ytsearch',
        'noplaylist': True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info("ytsearch5:" + query, download=False)
    entries = [e for e in (info.get("entries") or []) if e]
    if not entries:
        return None
    # Prefer an official-looking audio result; fall back to the top hit.
    def score(entry):
        title = (entry.get("title") or "").lower()
        channel = (entry.get("channel") or entry.get("uploader") or "").lower()
        s = 0
        if "official audio" in title or "audio" in title:
            s += 3
        if "topic" in channel:
            s += 4
        if "official" in title:
            s += 1
        for bad in ("live", "cover", "remix", "reaction", "karaoke", "sped up", "8d"):
            if bad in title:
                s -= 3
        return s

    best = max(entries, key=score)
    video_id = best.get("id") or ""
    url = best.get("url") or best.get("webpage_url") or ""
    if url and not url.startswith("http"):
        url = "https://www.youtube.com/watch?v=" + url
    if not url and video_id:
        url = "https://www.youtube.com/watch?v=" + video_id

    thumb = ""
    thumbs = best.get("thumbnails") or []
    if thumbs:
        thumb = thumbs[-1].get("url", "")
    if not thumb and video_id:
        thumb = "https://i.ytimg.com/vi/%s/hqdefault.jpg" % video_id

    return {
        "url": url,
        "title": best.get("title") or "Unknown",
        "channel": best.get("channel") or best.get("uploader") or "YouTube",
        "thumbnailUrl": thumb,
        "durationText": _format_duration(best.get("duration", 0)),
    }


def resolve_spotify_track(url):
    """
    Public-data-only Spotify track resolution:
      title (oEmbed) [+ artist (page meta)] -> YouTube search -> best match.
    Returns a dict the UI shows as a confirmation card before downloading.
    """
    title, thumb = _spotify_oembed_title(url)
    if not title:
        raise ValueError("Could not read this Spotify track")

    artist = _spotify_artist_from_page(url)
    # The oEmbed title sometimes already contains the artist; don't duplicate it.
    if artist and artist.lower() in title.lower():
        query = title
    elif artist:
        query = "%s %s" % (title, artist)
    else:
        query = title

    match = _youtube_best_match(query)
    if not match:
        raise ValueError("No YouTube match found for this track")

    match["spotifyTitle"] = title
    match["spotifyArtist"] = artist
    match["spotifyThumbnailUrl"] = thumb
    return match


# ---------------------------------------------------------------------------
# Single-song albums / playlists
#
# Some "albums" are really a single released as an album, and to the user that
# link looks identical to a real album. A logged-out visitor still gets the
# full tracklist embedded in the public page HTML (meta music:song tags plus
# the embedded JSON payload), so we can count tracks without any API.
#
# Everything here is deliberately best-effort: any doubt at all returns None,
# and the caller falls back to the "convert your playlist" popup.
# ---------------------------------------------------------------------------

def _spotify_collection_track_urls(html):
    """Best-effort list of unique track URLs embedded in a public album/playlist page."""
    ordered = []
    seen = set()
    # Spotify serializes the same page in a few forms depending on locale and
    # which server-side renderer answered the request. Normalize escaped HTML
    # and JSON slashes before looking for track IDs.
    normalized = _html_unescape(html)
    normalized = normalized.replace("\\/", "/")
    normalized = normalized.replace("\\u002F", "/").replace("\\u002f", "/")

    def _add(track_id):
        if track_id and track_id not in seen:
            seen.add(track_id)
            ordered.append("https://open.spotify.com/track/" + track_id)

    # 1) <meta ... music:song ... content=".../track/ID"> — attribute order is
    #    NOT guaranteed, so match the whole tag first, then read its parts.
    for tag in _re.finditer(r'<meta\b[^>]*>', normalized, _re.IGNORECASE):
        raw = tag.group(0)
        if not _re.search(r'(?:name|property)\s*=\s*["\']music:song["\']', raw, _re.IGNORECASE):
            continue
        c = _re.search(r'content\s*=\s*["\']([^"\']+)["\']', raw, _re.IGNORECASE)
        if not c:
            continue
        t = _re.search(r'(?:/track/|spotify:track:)([A-Za-z0-9]{16,})', c.group(1), _re.IGNORECASE)
        if t:
            _add(t.group(1))
    if ordered:
        return ordered

    # 2) Embedded JSON payload: spotify:track:ID / "/track/ID".
    # The locale segment is optional: both /track/ID and /intl-en/track/ID
    # occur in public Spotify pages.
    for m in _re.finditer(r'spotify:track:([A-Za-z0-9]{16,})', normalized, _re.IGNORECASE):
        _add(m.group(1))
    if ordered:
        return ordered
    for m in _re.finditer(
        r'(?:open\.spotify\.com|spotify\.com)/(?:intl-[^/"\s]+/)?track/([A-Za-z0-9]{16,})',
        normalized,
        _re.IGNORECASE,
    ):
        _add(m.group(1))
    return ordered or None


def _spotify_declared_track_count(html):
    """Reads a declared track count from the page, or None when unavailable."""
    normalized = _html_unescape(html).replace("\\/", "/")
    normalized = normalized.replace("\\u002F", "/").replace("\\u002f", "/")
    # These keys have all appeared in Spotify's server-rendered payloads.
    for pattern in (
        r'"totalTracks"\s*:\s*(\d+)',
        r'"total_tracks"\s*:\s*(\d+)',
        r'"trackCount"\s*:\s*(\d+)',
        r'"track_count"\s*:\s*(\d+)',
        r'"num_tracks"\s*:\s*(\d+)',
        r'"tracks"\s*:\s*\{\s*"total"\s*:\s*(\d+)',
    ):
        m = _re.search(pattern, normalized, _re.IGNORECASE)
        if m:
            try:
                return int(m.group(1))
            except Exception:
                pass
    # og:description / description, again order-independent.
    for tag in _re.finditer(r'<meta\b[^>]*>', normalized, _re.IGNORECASE):
        raw = tag.group(0)
        if not _re.search(r'(?:name|property)\s*=\s*["\'](?:og:)?description["\']', raw, _re.IGNORECASE):
            continue
        c = _re.search(r'content\s*=\s*["\']([^"\']+)["\']', raw, _re.IGNORECASE)
        if not c:
            continue
        n = _re.search(r'(\d+)\s+songs?\b', c.group(1), _re.IGNORECASE)
        if n:
            try:
                return int(n.group(1))
            except Exception:
                pass
    return None


def spotify_collection_single_track_url(url):
    """
    Returns the single track's URL when an album/playlist reliably contains
    exactly one song, otherwise "" (caller then shows the popup).
    """
    try:
        html = _http_get(url, timeout=12)
        tracks = _spotify_collection_track_urls(html)
        declared = _spotify_declared_track_count(html)

        # A declared one-track collection is the stronger signal. The page can
        # contain recommendation/related-track URLs outside the collection,
        # so requiring len(tracks) == 1 would incorrectly reject real
        # single-song albums. The first embedded track is the collection's
        # primary track in Spotify's server-rendered document order.
        if declared == 1 and tracks:
            return tracks[0]
        if declared is not None and declared != 1:
            return ""
        if tracks is None:
            return ""
        if len(tracks) != 1:
            return ""
        return tracks[0]
    except Exception:
        return ""


def resolve_spotify_collection_single(url):
    """
    If this album/playlist holds exactly one song, resolve it through the same
    track flow used for /track/ links. Returns None when it doesn't apply.
    """
    track_url = spotify_collection_single_track_url(url)
    if not track_url:
        return None
    try:
        return resolve_spotify_track(track_url)
    except Exception:
        return None


def spotify_collection_debug(url):
    """
    Diagnostics for a collection link that did NOT resolve to a single track.
    Called by the app only on failure and written to the internal crash log —
    never shown to the user.
    """
    try:
        html = _http_get(url, timeout=12)
    except Exception as e:
        return "spotify_collection_debug: fetch failed: %r" % (e,)
    tracks = _spotify_collection_track_urls(html) or []
    declared = _spotify_declared_track_count(html)
    return (
        "spotify_collection_debug url=%s tracks_found=%d declared=%s html_len=%d\n"
        "html_head=%s" % (url, len(tracks), declared, len(html), html[:500])
    )
