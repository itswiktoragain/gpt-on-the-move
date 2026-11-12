"""Encode the original 5,395 Flash frames from six-cell image sheets into video.
The resulting 5 MB MP4 is streamed into TurboWarp, avoiding 900 costumes.
"""
from pathlib import Path
from PIL import Image
import subprocess
import time

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'assets'
OUTPUT = ROOT / 'BreakingTheBank_video.mp4'
FRAME_COUNT = 5395
WIDTH, HEIGHT = 480, 360
FPS = 30

ffmpeg = [
    'ffmpeg', '-hide_banner', '-loglevel', 'warning', '-y',
    '-f', 'rawvideo', '-pixel_format', 'rgb24',
    '-video_size', f'{WIDTH}x{HEIGHT}', '-framerate', str(FPS),
    '-i', 'pipe:0', '-an',
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '19',
    '-g', '30', '-keyint_min', '30', '-sc_threshold', '0',
    '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
    str(OUTPUT)
]

started = time.time()
encoder = subprocess.Popen(ffmpeg, stdin=subprocess.PIPE)
try:
    for sheet_no in range((FRAME_COUNT + 5) // 6):
        sheet = Image.open(ASSETS / f'sheet_{sheet_no:04}.png').convert('RGB')
        for slot in range(min(6, FRAME_COUNT - sheet_no * 6)):
            x, y = slot % 3, slot // 3
            image = sheet.crop((x * WIDTH, y * HEIGHT, (x + 1) * WIDTH, (y + 1) * HEIGHT))
            encoder.stdin.write(image.tobytes())
        if sheet_no % 60 == 0:
            print('Video encode:', sheet_no, 'of', (FRAME_COUNT + 5) // 6, flush=True)
finally:
    encoder.stdin.close()
    code = encoder.wait()
    if code:
        raise RuntimeError('ffmpeg exited with status ' + str(code))

assert OUTPUT.is_file() and OUTPUT.stat().st_size > 100000
print('Encoded', FRAME_COUNT, 'frames at', FPS, 'FPS.',
      'Output bytes', OUTPUT.stat().st_size,
      'Elapsed', round(time.time() - started, 2), 'seconds')
