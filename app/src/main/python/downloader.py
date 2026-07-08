import yt_dlp

def fetch_video_info(url):
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        return {
            "title": info.get("title", "Unknown Video"),
            "channel": info.get("uploader", "Unknown Channel"),
            "duration": str(info.get("duration", 0))
        }

def fetch_video(url, ffmpeg_path, callback):
    def progress_hook(d):
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_approx') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = int((downloaded / total) * 100) if total > 0 else 0
            callback.onProgress(percent, "downloading")
        elif d['status'] == 'finished':
            callback.onProgress(100, "done")

    ydl_opts = {
        'progress_hooks': [progress_hook],
        'ffmpeg_location': ffmpeg_path,
        'outtmpl': '/storage/emulated/0/Download/%(title)s.%(ext)s',
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([url])
