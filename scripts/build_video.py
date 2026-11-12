"""Builds the demo video from recorded scenes, phone captures, captions and narration.

Usage: build_video.py <scene dir> <output mp4>
The emulator's sound cannot be recorded, so narration is synthesized: Microsoft's neural Korean voice
through the edge-tts package when it is installed (only the narration text is sent), otherwise the
built-in Windows Korean voice.
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
ACCENT = "0xD9573F"
MUTED = "0x8A8073"
# The car screen sits on paper as a rounded card with a soft shadow.
SW, SH, SX, SY, RADIUS = 1120, 630, 80, 18, 26
# The phone page is shown inside a simple dark phone frame.
PW, PH, BEZEL = 340, 612, 12
FADE = 0.3

# kind, source, narration, caption, note. For clips and phone shots the note is a small disclosure next
# to the caption. For cards the note is "<face>|<footer>": the character picture face_<face>.png in the
# scene dir is drawn above the title, and the optional footer is a small disclosure at the bottom.
SCENES = [
    ("card", "우리 차 일기", "우리 차 일기. 가족이 지어 준 이름으로 불리는 차가, 실제 차량 신호로 하루를 기록하고 들려주는 앱이에요.",
     "가족이 이름 지어 준 차가, 실제 차량 신호로 쓰는 일기",
     "talk|에뮬레이터 화면 · 신호는 Virtual Console로 입력 · 합성 내레이션"),
    ("card", "폰은 모르고, 차만 아는 것", "누가 어느 자리에 탔는지, 벨트는 언제 맸는지, 비는 얼마나 맞았는지. 이건 폰은 모르고, 차만 알아요. "
     "그래서 이 이야기는, 가족이 함께 보고 듣는 차가 직접 들려줘요.",
     "좌석 · 벨트 · 와이퍼 · 배터리 · 주행 상태", "talk"),
    ("clip", "s1_setup", "처음 만나는 날, 가족이 차에게 이름을 지어 줘요. 아이 이름과 앉는 자리를 정하고, 보호자가 동의하면 준비 끝이에요.",
     "이름 짓기와 보호자 동의", "빠르게 재생"),
    ("clip", "s2_board", "아이가 타면 차가 먼저 반갑게 인사해요. 그리고 어제 일기를 들려줘요. 비가 온 것도, 달린 거리도, 모두 실제 차량 신호에서 나온 이야기예요.",
     "좌석 신호로 탑승 감지, 어제의 기록으로 일기", "어제 기록은 시연용 입력"),
    ("clip", "s3_belt", "벨트를 매는 순간, 차가 아이를 꼭 안아 줘요. 벨트 매기가 잔소리가 아니라, 즐거운 놀이가 돼요.",
     "벨트 포옹", "시연에서는 버튼으로 벨트 신호 입력"),
    ("clip", "s4_drive", "출발하면 앱은 화면도, 목소리도 멈춰요. 운전을 방해하지 않도록, 와이퍼와 주행 거리만 조용히 기록해요.",
     "주행 중에는 화면과 음성 없음", None),
    ("clip", "s5_park", "차를 세우면, 그때 하고 싶던 말을 전해요. 배터리가 부족하면 아이에게는 배가 고프다고 말하고, 보호자에게는 정확한 잔량을 알려 줘요.",
     "돌봄 알림과 보호자 안내", None),
    ("clip", "s6_charge", "충전을 해 주면 고맙다고 인사해요. 차를 아끼는 마음이, 자연스럽게 차량 관리로 이어져요.", "충전 후 감사 인사", None),
    ("clip", "s7_ask", "가족이 내린 동안 차는 기다려요. 다시 타면 뭐 하고 왔는지 묻고, 아이의 대답을 일기에 적어요.",
     "차가 기다린 시간", "기다리는 시간은 생략"),
    ("clip", "s8_qr", "사진은 보호자 폰에서 골라요. 차 화면의 QR 코드를 찍으면 바로 연결돼요.", "폰과 연결", None),
    ("phone", "phone_pick", "사진은 폰에 그대로 두고, 찍은 시각만 차로 보내요. 위치 정보 대신, 차가 기록한 시간으로 고르는 거예요.", "시각 정보만 먼저 전송", "샘플 그림"),
    ("phone", "phone_review", "차가 기다린 시간에 찍은 사진 중에서 일곱 장을 추천해요. 빼거나 더할 수 있고, 운전 중에 운전자 폰으로 찍은 사진은 추천하지 않아요.",
     "100장 중 7장 추천", "샘플 그림 · 추천 결과 예시"),
    ("clip", "s9_photos", "승인한 사진만 차로 옮겨져요. 다음에 탈 때, 차 화면에서 가족이 함께 봐요.", "다음 탑승 때 사진에 반응", "샘플 그림"),
    ("card", "연말에는 차가 쓴 책으로", "쌓인 일기와 사진은 연말에, 차가 쓴 한 권의 책이 돼요. 우리 차 일기. 이제, 차도 가족이 돼요.",
     "차도 가족이 됩니다", "hug|포토북 주문은 구현 예정 · 실제 차량 미시험"),
]


def run(*args):
    subprocess.run(args, check=True, capture_output=True)


def duration(path):
    out = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)])
    return float(json.loads(out)["format"]["duration"])


VOICE = "ko-KR-SunHiNeural"


def speak(text, wav):
    try:
        import asyncio
        import edge_tts
        mp3 = f"{wav}.mp3"
        asyncio.run(edge_tts.Communicate(text, VOICE, rate="-3%").save(mp3))
        run("ffmpeg", "-y", "-v", "error", "-i", mp3, "-ar", "44100", "-ac", "1", wav)
        return
    except ImportError:
        pass
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
    path = str(file).replace("\\", "/").replace(":", r"\:")
    extra = f":box=1:boxcolor={box}:boxborderw=12" if box else ""
    return f"drawtext=fontfile='{font}':textfile='{path}':fontsize={size}:fontcolor={color}:x={x}:y={y}{extra}"


def make_mask(work, name, w, h, r):
    """A white rounded rectangle on black, used to round corners and cast shadows."""
    mask = work / f"{name}.png"
    expr = (rf"255*lte(pow(max(max({r}-X\,X-({w - 1 - r}))\,0)\,2)"
            rf"+pow(max(max({r}-Y\,Y-({h - 1 - r}))\,0)\,2)\,{r * r})")
    run("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", f"color=black:s={w}x{h}", "-vf",
        f"format=gray,geq=lum='{expr}'", "-frames:v", "1", str(mask))
    return mask


def fades(length):
    return (f"fade=t=in:st=0:d={FADE}:color={PAPER},"
            f"fade=t=out:st={length - FADE:.2f}:d={FADE}:color={PAPER}")


def shadow(w, h, mask_label, out_label, alpha=0.22):
    """A blurred dark copy of a rounded shape, padded by 40px on each side."""
    return (f"color=black:s={w}x{h}:r=30,format=rgba[k{out_label}];[k{out_label}][{mask_label}]alphamerge,"
            f"colorchannelmixer=aa={alpha},pad={w + 80}:{h + 80}:40:40:color=black@0,gblur=sigma=14[{out_label}]")


def build_scene(i, step, scene, src, work, masks):
    kind, source, narration, caption, note = scene
    wav = str((work / f"n{i}.wav").resolve())
    speak(narration, wav)
    length = duration(wav) + 0.9
    out = work / f"scene{i:02}.mp4"
    common = ["-map", "[v]", "-map", "[a]", "-t", f"{length:.2f}", "-r", "30", "-c:v", "libx264", "-pix_fmt", "yuv420p",
              "-preset", "veryfast", "-crf", "20", "-c:a", "aac", "-ar", "44100", "-ac", "2", str(out)]
    audio = "[1:a]adelay=250|250,apad[a]"
    bg = f"color=c={PAPER}:s={W}x{H}:r=30:d={length:.2f}"
    if kind in ("clip", "phone"):
        cap_y = SY + SH + 22
        label = text_filter(work, f"step{i}", f"{step:02}", 30, BOLD, ACCENT, SX, cap_y)
        cap = text_filter(work, f"cap{i}", caption, 30, BOLD, INK, SX + 56, cap_y)
        parts = [label, cap]
        if note:
            parts.append(text_filter(work, f"note{i}", note, 20, FONT, MUTED, f"{SX + SW}-text_w", cap_y + 8))
        texts = ",".join(parts)
    if kind == "clip":
        clip = src / f"{source}.mp4"
        # Fast-forward clips longer than the narration; hold the last frame of shorter ones.
        speed = min(1.0, (length - 0.4) / duration(clip))
        filt = (f"{bg}[bg];"
                f"[0:v]setpts={speed:.4f}*PTS,fps=30,scale={SW}:{SH},tpad=stop_mode=clone:stop_duration=60,format=rgba[c];"
                f"[2:v]format=gray,split[m1][m2];[c][m1]alphamerge[screen];{shadow(SW, SH, 'm2', 'sh')};"
                f"[bg][sh]overlay={SX - 40}:{SY - 32}:shortest=1[b1];[b1][screen]overlay={SX}:{SY}:shortest=1,"
                f"{texts},{fades(length)}[v];{audio}")
        run("ffmpeg", "-y", "-v", "error", "-i", str(clip), "-i", wav, "-loop", "1", "-i", str(masks["screen"]),
            "-filter_complex", filt, *common)
    elif kind == "phone":
        image = src / "phone" / f"{source}.png"
        fw, fh = PW + 2 * BEZEL, PH + 2 * BEZEL
        fx, fy = (W - fw) // 2, 8
        filt = (f"{bg}[bg];"
                f"[0:v]scale={PW}:{PH},format=rgba[p];[2:v]format=gray[pm];[p][pm]alphamerge[ps];"
                f"[3:v]format=gray,split[b1][b2];color={INK}:s={fw}x{fh}:r=30,format=rgba[bz0];[bz0][b1]alphamerge[bz];"
                f"{shadow(fw, fh, 'b2', 'sh', 0.28)};"
                f"[bg][sh]overlay={fx - 40}:{fy - 32}:shortest=1[l1];[l1][bz]overlay={fx}:{fy}:shortest=1[l2];"
                f"[l2][ps]overlay={fx + BEZEL}:{fy + BEZEL}:shortest=1,{texts},{fades(length)}[v];{audio}")
        run("ffmpeg", "-y", "-v", "error", "-loop", "1", "-i", str(image), "-i", wav,
            "-loop", "1", "-i", str(masks["phone"]), "-loop", "1", "-i", str(masks["bezel"]), "-filter_complex", filt, *common)
    else:
        face_name, _, footer = note.partition("|")
        face = src / f"face_{face_name}.png"
        title = text_filter(work, f"title{i}", source, 60, BOLD, INK, "(w-text_w)/2", 420)
        sub = text_filter(work, f"cap{i}", caption, 26, FONT, MUTED, "(w-text_w)/2", 515)
        bar = f"drawbox=x=(iw-80)/2:y=498:w=80:h=4:color={ACCENT}:t=fill"
        foot = "," + text_filter(work, f"foot{i}", footer, 19, FONT, MUTED, "(w-text_w)/2", "h-50") if footer else ""
        filt = (f"{bg}[bg];[2:v]scale=-2:330[f];[bg][f]overlay=(W-w)/2:60:shortest=1,{title},{bar},{sub}{foot},"
                f"{fades(length)}[v];{audio}")
        run("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", "anullsrc", "-i", wav, "-loop", "1", "-i", str(face),
            "-filter_complex", filt, *common)
    return out


def main():
    src = Path(sys.argv[1])
    output = sys.argv[2]
    work = src / "build"
    work.mkdir(exist_ok=True)
    masks = {
        "screen": make_mask(work, "mask_screen", SW, SH, RADIUS),
        "phone": make_mask(work, "mask_phone", PW, PH, 30),
        "bezel": make_mask(work, "mask_bezel", PW + 2 * BEZEL, PH + 2 * BEZEL, 42),
    }
    parts, step = [], 0
    for i, scene in enumerate(SCENES):
        if scene[0] != "card":
            step += 1
        parts.append(build_scene(i, step, scene, src, work, masks))
    listing = work / "list.txt"
    listing.write_text("".join(f"file '{p.resolve().as_posix()}'\n" for p in parts), encoding="utf8")
    run("ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", "-movflags", "+faststart", output)
    print("total seconds", round(duration(output), 1))


if __name__ == "__main__":
    main()
