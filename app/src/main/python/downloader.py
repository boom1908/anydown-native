import yt_dlp
from yt_dlp.utils import DownloadCancelled

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

def fetch_video(url, ffmpeg_dir, output_dir, format_type, callback):
    def progress_hook(d):
        if callback.isCancelled():
            raise DownloadCancelled("User cancelled")
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = int((downloaded / total) * 100) if total else 0
            callback.onProgress(percent, "downloading")
        elif d['status'] == 'finished':
            callback.onProgress(100, "processing")

    base = {
        'progress_hooks': [progress_hook],
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
