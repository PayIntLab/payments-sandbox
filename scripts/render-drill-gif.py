#!/usr/bin/env python3
"""Render the reconciliation drill log as a two-pane terminal GIF.

Left pane: the drill steps (what is happening).
Right pane: provider and merchant backend logs (why it is happening).

Usage: python3 scripts/render-drill-gif.py [log-path]

Outputs (docs/):
  reconciliation-drill.gif      progressive terminal recording for README/dev.to
  reconciliation-drill.png      final frame, usable as a cover image
  reconciliation-drill.cast     asciinema v2 recording (embed with the player)
"""
import json
import os
import re
import sys
import textwrap
import time

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1440, 810
PAD = 24
TITLE_H = 42
LEFT_RATIO = 0.60
DIVIDER = int(WIDTH * LEFT_RATIO)

LEFT_FONT_SIZE = 15
RIGHT_FONT_SIZE = 13
LEFT_LINE_H = 23
RIGHT_LINE_H = 20

BG = (11, 18, 32)
TITLE_BG = (17, 24, 39)
PANE_BG = (11, 18, 32)
FG = (209, 213, 219)
GREEN = (74, 222, 128)
AMBER = (251, 191, 36)
RED = (248, 113, 113)
BLUE = (96, 165, 250)
MUTED = (100, 116, 139)

MENLO = "/System/Library/Fonts/Menlo.ttc"


def load_fonts():
    left = ImageFont.truetype(MENLO, LEFT_FONT_SIZE, index=0)
    right = ImageFont.truetype(MENLO, RIGHT_FONT_SIZE, index=0)
    try:
        left_bold = ImageFont.truetype(MENLO, LEFT_FONT_SIZE, index=1)
    except Exception:
        left_bold = left
    return left, left_bold, right


def left_color(line):
    if line.startswith("=====") or any(f"[{i}/6]" in line for i in range(1, 7)):
        return BLUE
    if "PENDING" in line:
        return AMBER
    if "PAID" in line or "recovered" in line or "succeeded" in line:
        return GREEN
    if "dropped" in line or "never arrived" in line:
        return RED
    return FG


def right_color(line):
    if "DROP" in line:
        return RED
    if "recovered" in line:
        return GREEN
    if "scan" in line:
        return BLUE
    return MUTED


def left_item_height(item):
    if item["type"] == "divider":
        return 14 + LEFT_LINE_H + 26
    return LEFT_LINE_H


def right_item_height(item):
    if item["type"] == "divider":
        return 14 + LEFT_LINE_H + 26
    return max(1, len(item["wrapped"])) * RIGHT_LINE_H


def trim_to_height(items, max_height, height_of):
    total = 0
    start = len(items)
    for index in range(len(items) - 1, -1, -1):
        height = height_of(items[index])
        if total + height > max_height and start != len(items):
            break
        total += height
        start = index
    return items[start:]


def dashed_line(draw, x0, x1, y, color):
    x = x0
    while x < x1:
        draw.line([x, y, min(x + 8, x1), y], fill=color)
        x += 14


def draw_divider(draw, x0, x1, y, label, font, color):
    dashed_line(draw, x0, x1, y, color)
    draw.text((x0 + 8, y + 4), label, font=font, fill=color)
    return LEFT_LINE_H + 26


def draw_frame(left_items, right_items, left_font, left_bold, right_font):
    img = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)

    draw.rectangle([0, 0, WIDTH, TITLE_H], fill=TITLE_BG)
    for i, x in enumerate((22, 40, 58)):
        draw.ellipse([x, 15, x + 11, 26], fill=(71, 85, 105))
    draw.text((WIDTH // 2 - 250, 12), "reconciliation drill  ·  drill steps  |  provider + merchant logs",
              font=left_font, fill=MUTED)

    draw.line([DIVIDER, TITLE_H + 10, DIVIDER, HEIGHT - 10], fill=(30, 41, 59))
    draw.text((PAD, TITLE_H + 12), "drill", font=left_font, fill=BLUE)
    draw.text((DIVIDER + PAD, TITLE_H + 12), "backend logs", font=right_font, fill=BLUE)

    y = TITLE_H + 44
    for item in left_items:
        if item["type"] == "divider":
            y += 14
            y += draw_divider(draw, PAD, DIVIDER - PAD, y, item["label"], left_font, MUTED)
            continue
        draw.text((PAD, y), item["text"][:120], font=left_font, fill=left_color(item["text"]))
        y += LEFT_LINE_H

    y = TITLE_H + 44
    for item in right_items:
        if item["type"] == "divider":
            y += 14
            y += draw_divider(draw, DIVIDER + PAD, WIDTH - PAD, y, item["label"], right_font, MUTED)
            continue
        for index, part in enumerate(item["wrapped"]):
            prefix = "" if index == 0 else "  "
            draw.text((DIVIDER + PAD, y), prefix + part, font=right_font,
                      fill=right_color(item["text"]))
            y += RIGHT_LINE_H
    return img


def main():
    log_path = sys.argv[1] if len(sys.argv) > 1 else "target/reconciliation-drill/drill.log"
    with open(log_path, encoding="utf-8") as handle:
        raw_lines = [line.rstrip("\n") for line in handle]

    left_font, left_bold, right_font = load_fonts()

    body_height = HEIGHT - (TITLE_H + 44) - PAD
    right_chars = int((WIDTH - DIVIDER - PAD * 2) / (RIGHT_FONT_SIZE * 0.63))

    entries = []
    for line in raw_lines:
        if not line.strip() or line.startswith("--- backend logs") or line.startswith("====="):
            continue
        match = re.match(r"\s*\[(\d)/6\]", line)
        if match:
            entries.append(("divider", f"step {match.group(1)}/6"))
        if line.startswith("[backend] "):
            text = line[len("[backend] "):]
            entries.append(("right", (text, textwrap.wrap(text, right_chars) or [""])))
        else:
            entries.append(("left", line))

    frames = []
    left_buffer = []
    right_buffer = []
    for kind, payload in entries:
        if kind == "divider":
            left_buffer.append({"type": "divider", "label": payload})
            right_buffer.append({"type": "divider", "label": payload})
        elif kind == "left":
            left_buffer.append({"type": "text", "text": payload})
        else:
            right_buffer.append({"type": "text", "text": payload[0], "wrapped": payload[1]})
        frame_image = draw_frame(trim_to_height(left_buffer, body_height, left_item_height),
                                 trim_to_height(right_buffer, body_height, right_item_height),
                                 left_font, left_bold, right_font)
        frames.append(frame_image)
        if kind == "divider":
            for _ in range(4):  # pause on each step divider
                frames.append(frame_image)

    for _ in range(13):  # hold the final frame for about four seconds
        frames.append(frames[-1])

    os.makedirs("docs", exist_ok=True)
    gif = "docs/reconciliation-drill.gif"
    frames[0].save(gif, save_all=True, append_images=frames[1:],
                   duration=320, loop=0, optimize=True, disposal=2)
    frames[-1].save("docs/reconciliation-drill.png")

    cast = "docs/reconciliation-drill.cast"
    with open(cast, "w", encoding="utf-8") as handle:
        handle.write(json.dumps({
            "version": 2,
            "width": 160,
            "height": 42,
            "timestamp": int(time.time()),
            "env": {"SHELL": "/bin/bash", "TERM": "xterm-256color"},
        }) + "\n")
        t = 0.0
        for line in raw_lines:
            handle.write(json.dumps([round(t, 2), "o", line + "\r\n"]) + "\n")
            t += 0.22

    print(f"wrote {gif}, docs/reconciliation-drill.png and {cast}" +
          f" ({len(frames)} frames, {len(entries)} entries)")


if __name__ == "__main__":
    main()
