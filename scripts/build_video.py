"""Builds the demo video from recorded scenes, phone captures, captions and narration.

Usage: build_video.py <scene dir> <output mp4>
Narration uses the Windows Korean voice because the emulator's sound cannot be recorded.
"""
import json
import subprocess
import sys
from pathlib import Path

W, H = 1280, 720
FONT = "C\\:/Windows/Fonts/malgun.ttf"
BOLD = "C\\:/Windows/Fonts/malgunbd.ttf"
PAPER = "0xFFF8EC"
INK = "0x2E2A25"

# kind, source, narration, caption, optional note shown at the top right
SCENES = [
    ("card", "우리 차 일기", "우리 차 일기. 가족이 이름 지어준 차가, 실제 차량 신호로 쓴 일기를 들려줍니다.",
     "에뮬레이터 화면 · 신호는 Virtual Console로 입력 · 합성 내레이션", None),
    ("card", "폰은 모르고, 차만 아는 것", "누가 어느 자리에 탔는지, 벨트를 언제 맸는지, 비를 얼마나 맞았는지. 폰은 모르고 차만 압니다. "
     "그래서 이 이야기는, 가족이 함께 보고 듣는 차가 직접 들려줍니다.",
     "좌석 · 벨트 · 와이퍼 · 배터리 · 주행 상태", None),
    ("clip", "s1_setup", "처음 만나는 날, 가족이 차에게 이름을 지어 줍니다. 아이 이름과 자리를 정하고, 보호자가 동의합니다.",
     "이름 짓기와 보호자 동의", "빠르게 재생"),
    ("clip", "s2_board", "아이가 타면 차가 먼저 인사하고, 어제 일기를 들려줍니다. 비 온 날도, 달린 거리도 실제 차량 신호에서 나옵니다.",
     "좌석 신호로 탑승 감지, 어제의 기록으로 일기", "어제 기록은 시연용 입력"),
    ("clip", "s3_belt", "벨트를 매는 순간, 차가 꼭 안아 줍니다. 벨트 매기가 잔소리가 아니라 놀이가 됩니다.",
     "벨트 포옹", "시연에서는 버튼으로 벨트 신호 입력"),
    ("clip", "s4_drive", "주행이 시작되면 앱은 화면과 음성을 멈춥니다. 와이퍼와 주행 거리만 조용히 기록합니다.",
     "주행 중에는 화면과 음성 없음", None),
    ("clip", "s5_park", "주차하면, 미뤄 둔 말을 전합니다. 배터리가 부족하면 아이에게는 배고프다고 말하고, 보호자에게는 정확한 잔량을 알립니다.",
     "돌봄 알림과 보호자 안내", None),
    ("clip", "s6_charge", "충전하면 고맙다고 인사합니다. 애정이 차량 관리로 이어집니다.", "충전 후 감사 인사", None),
    ("clip", "s7_ask", "가족이 내린 사이 차는 기다립니다. 다시 타면, 뭐 하고 왔는지 묻고 일기에 적습니다.",
     "차가 기다린 시간", "기다리는 시간은 생략"),
    ("clip", "s8_qr", "사진은 보호자 폰에서 고릅니다. 차 화면의 QR 코드를 찍으면 페이지가 열립니다.", "폰과 연결", None),
    ("phone", "phone_pick", "사진은 폰에 그대로 두고, 촬영 시각만 차로 보냅니다. 위치 대신, 차가 기록한 시간으로 고릅니다.", "시각 정보만 먼저 전송", "샘플 그림"),
    ("phone", "phone_review", "차가 기다린 시간의 사진 중 일곱 장을 추천합니다. 빼거나 더할 수 있고, 운전 중 운전자 폰 사진은 추천하지 않습니다.",
     "100장 중 7장 추천", "샘플 그림 · 추천 결과 예시"),
    ("clip", "s9_photos", "승인한 사진만 차로 옮겨지고, 다음에 탈 때 차 화면에서 가족이 함께 봅니다.", "다음 탑승 때 사진에 반응", "샘플 그림"),
    ("card", "연말에는 차가 쓴 책으로", "쌓인 일기와 사진은 연말에 차가 쓴 책이 됩니다. 우리 차 일기. 차도 가족이 됩니다.",
     "포토북 주문은 구현 예정 · 실제 차량 미시험", None),
]


def run(*args):
    subprocess.run(args, check=True, capture_output=True)


def duration(path):
    out = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)])
    return float(json.loads(out)["format"]["duration"])


def speak(text, wav):
    script = (
        "Add-Type -AssemblyName System.Speech;"
        "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
        "$s.SelectVoice('Microsoft Heami Desktop');$s.Rate=1;"
        f"$s.SetOutputToWaveFile('{wav}');$s.Speak([IO.File]::ReadAllText('{wav}.txt'));$s.Dispose()"
    )
    Path(f"{wav}.txt").write_text(text, encoding="utf-8-sig")
    run("powershell", "-NoProfile", "-Command", script)


def text_filter(work, name, text, size, font, color, x, y, box=None):
    file = work / f"{name}.txt"
    file.write_text(text, encoding="utf8")
    path = str(file).replace("\\", "/").replace(":", "\\:")
    extra = f":box=1:boxcolor={box}:boxborderw=14" if box else ""
    return f"drawtext=fontfile='{font}':textfile='{path}':fontsize={size}:fontcolor={color}:x={x}:y={y}{extra}"


def captions(work, i, caption, note):
    parts = [text_filter(work, f"cap{i}", caption, 30, BOLD, "white", "(w-text_w)/2", "h-68", "0x2E2A25CC")]
    if note:
        parts.append(text_filter(work, f"note{i}", note, 20, FONT, "white", "w-text_w-24", "24", "0x2E2A25AA"))
    return ",".join(parts)


def build_scene(i, scene, src, work):
    kind, source, narration, caption, note = scene
    wav = str((work / f"n{i}.wav").resolve())
    speak(narration, wav)
    length = duration(wav) + 0.7
    out = work / f"scene{i:02}.mp4"
    caps = captions(work, i, caption, note)
    common = ["-map", "[v]", "-map", "1:a", "-t", f"{length:.2f}", "-r", "30", "-c:v", "libx264", "-pix_fmt", "yuv420p",
              "-preset", "veryfast", "-crf", "22", "-c:a", "aac", "-ar", "44100", "-ac", "2", str(out)]
    if kind == "clip":
        clip = src / f"{source}.mp4"
        # Fast-forward clips longer than the narration; hold the last frame of shorter ones.
        speed = min(1.0, length / duration(clip))
        filt = f"[0:v]setpts={speed:.4f}*PTS,fps=30,scale={W}:{H},tpad=stop_mode=clone:stop_duration=60,{caps}[v]"
        run("ffmpeg", "-y", "-v", "error", "-i", str(clip), "-i", wav, "-filter_complex", filt, *common)
    elif kind == "phone":
        image = src / "phone" / f"{source}.png"
        filt = (f"color=c={PAPER}:s={W}x{H}:r=30:d={length:.2f}[bg];"
                f"[0:v]scale=-2:{H - 110}[ph];[bg][ph]overlay=(W-w)/2:20,{caps}[v]")
        run("ffmpeg", "-y", "-v", "error", "-loop", "1", "-i", str(image), "-i", wav, "-filter_complex", filt, *common)
    else:
        title = text_filter(work, f"title{i}", source, 64, BOLD, INK, "(w-text_w)/2", "(h-text_h)/2-30")
        filt = f"color=c={PAPER}:s={W}x{H}:r=30:d={length:.2f},{title},{caps}[v]"
        run("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", "anullsrc", "-i", wav, "-filter_complex", filt, *common)
    return out


def main():
    src = Path(sys.argv[1])
    output = sys.argv[2]
    work = src / "build"
    work.mkdir(exist_ok=True)
    parts = [build_scene(i, s, src, work) for i, s in enumerate(SCENES)]
    listing = work / "list.txt"
    listing.write_text("".join(f"file '{p.resolve().as_posix()}'\n" for p in parts), encoding="utf8")
    run("ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", "-movflags", "+faststart", output)
    print("total seconds", round(duration(output), 1))


if __name__ == "__main__":
    main()
