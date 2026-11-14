#!/usr/bin/env python3
"""Creates small tagged FLAC/MP3 fixtures (tones, covers, genres, synced lyrics).

They are synthetic test media generated in CI; nothing is committed or shipped.
"""
import subprocess
import sys
from pathlib import Path

out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
TRACKS = [
    # title, artist, album, genre, year, colour, accent, freq, ext, seconds
    ("Amanecer Coral", "Luna Vega", "Mareas", "Pop", 2024, "0xE8615A", "0xFFD9C7", 330, "flac", 40),
    ("Marea Baja", "Luna Vega", "Mareas", "Pop", 2024, "0xE8615A", "0xFFD9C7", 392, "flac", 36),
    ("Jardín Eléctrico", "Los Faroles", "Neón Verde", "Rock", 2021, "0x2E8B57", "0xD7F5C6", 247, "flac", 38),
    ("Ruta 27", "Los Faroles", "Neón Verde", "Rock", 2021, "0x2E8B57", "0xD7F5C6", 294, "mp3", 34),
    ("Cielo Índigo", "Aria Montes", "Azul Profundo", "Jazz", 2019, "0x3E4FA8", "0xDCE1FF", 440, "flac", 42),
    ("Lluvia Lenta", "Aria Montes", "Azul Profundo", "Jazz", 2019, "0x3E4FA8", "0xDCE1FF", 349, "mp3", 35),
    ("Polvo de Oro", "Sol Naciente", "Dorado", "Electrónica", 2023, "0xC79A1E", "0xFFF1B8", 523, "flac", 37),
    ("Último Tren", "Sol Naciente", "Dorado", "Electrónica", 2023, "0xC79A1E", "0xFFF1B8", 262, "mp3", 33),
]
LYRIC_WORDS = [
    "Primera línea de la canción", "La luz entra por la ventana", "Cuento los pasos hasta el mar",
    "Nadie duerme en la ciudad", "Vuelve la marea a comenzar", "Guardo el eco de tu voz",
    "Todo cambia de color", "Seguimos el compás", "El tren se va sin avisar", "Última línea, hasta volver",
]


def run(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


for index, (title, artist, album, genre, year, colour, accent, freq, ext, seconds) in enumerate(TRACKS, 1):
    cover = out / f"cover-{index}.png"
    run("-f", "lavfi", "-i", f"color=c={colour}:s=600x600",
        "-vf", f"drawbox=x=90:y=90:w=420:h=420:color={accent}@0.9:t=fill,drawbox=x=210:y=210:w=180:h=180:color={colour}:t=fill",
        "-frames:v", "1", str(cover))
    lyrics = "\n".join(f"[00:{2 + i * 3:02d}.00]{line}" for i, line in enumerate(LYRIC_WORDS))
    target = out / f"{index:02d} - {title}.{ext}"
    meta = ["-metadata", f"title={title}", "-metadata", f"artist={artist}", "-metadata", f"album={album}",
            "-metadata", f"album_artist={artist}", "-metadata", f"genre={genre}", "-metadata", f"date={year}",
            "-metadata", f"track={index % 2 + 1}"]
    source = ["-f", "lavfi", "-i", f"sine=frequency={freq}:sample_rate=44100:duration={seconds}", "-i", str(cover),
              "-map", "0:a", "-map", "1:v", "-af", "volume=0.2", "-disposition:v", "attached_pic"]
    if ext == "flac":
        run(*source, "-c:a", "flac", "-c:v", "png", *meta, "-metadata", f"LYRICS={lyrics}", str(target))
    else:
        run(*source, "-c:a", "libmp3lame", "-b:a", "192k", "-c:v", "png", "-id3v2_version", "3",
            *meta, "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)", str(target))
    cover.unlink()
print("\n".join(sorted(p.name for p in out.iterdir())))
