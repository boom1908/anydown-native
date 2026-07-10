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
            "duration": str(info.get("duration", 0)),
            "thumbnail": info.get("thumbnail", "")
        }

def fetch_video(url, ffmpeg_path, output_dir, format_type, callback):
    def progress_hook(d):
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_approx') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = int((downloaded / total) * 100) if total > 0 else 0
            callback.onProgress(percent, "downloading")
        elif d['status'] == 'finished':
            callback.onProgress(100, "done")

    base_opts = {
        'progress_hooks': [progress_hook],
        'ffmpeg_location': ffmpeg_path,
        'outtmpl': f'{output_dir}/%(title)s.%(ext)s',
    }

    if format_type == "VIDEO":
        ydl_opts = {
            **base_opts,
            'format': 'bestvideo+bestaudio/best',
            'merge_output_format': 'mp4',
        }
    elif format_type == "AUDIO":
        ydl_opts = {
            **base_opts,
            'format': 'bestaudio/best',
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'm4a',
                'preferredquality': '320',
            }],
        }
    elif format_type == "FAST":
        ydl_opts = {
            **base_opts,
            'format': 'best[height<=720][ext=mp4]/best[height<=720]',
        }
    else:
        ydl_opts = {**base_opts, 'format': 'best'}

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)
