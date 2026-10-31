#!/usr/bin/env python3
"""Генерирует PNG-иконки приложения: чёрная банка с зелёными «когтями».

Рисуем без внешних зависимостей — только stdlib, чистый PNG на выходе.
Иконка повторяет банку, которая используется в анимации прогресса смены.
"""
import struct
import zlib
import math
import os

GREEN = (108, 194, 74)
GREEN_DARK = (62, 142, 42)
DARK = (26, 28, 32)
METAL = (201, 205, 212)
WHITE = (255, 255, 255)


def blend(dst, src, alpha):
    return tuple(int(s * alpha + d * (1 - alpha)) for s, d in zip(src, dst))


def make_icon(size):
    """Возвращает RGBA-пиксели: банка на прозрачном фоне."""
    px = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    # Геометрия банки внутри квадрата
    cx = size / 2
    body_w = size * 0.46
    top = size * 0.16
    bottom = size * 0.86
    corner = body_w * 0.16

    def in_can(x, y):
        """Внутри ли пиксель корпуса банки (скруглённый прямоугольник)."""
        left, right = cx - body_w / 2, cx + body_w / 2
        if y < top or y > bottom:
            return False
        # скругления сверху и снизу
        if y < top + corner:
            dy = (top + corner) - y
            if x < left + corner:
                return (x - (left + corner)) ** 2 + dy ** 2 <= corner ** 2
            if x > right - corner:
                return (x - (right - corner)) ** 2 + dy ** 2 <= corner ** 2
        if y > bottom - corner:
            dy = y - (bottom - corner)
            if x < left + corner:
                return (x - (left + corner)) ** 2 + dy ** 2 <= corner ** 2
            if x > right - corner:
                return (x - (right - corner)) ** 2 + dy ** 2 <= corner ** 2
        return left <= x <= right

    def claw_hit(x, y):
        """Три «когтя» логотипа — направленные вниз острые штрихи."""
        for i in (-1, 0, 1):
            bx = cx + i * body_w * 0.24
            # коготь: треугольник, вершина внизу слева, основание сверху
            topy = top + (bottom - top) * 0.30
            boty = top + (bottom - top) * 0.72
            if topy <= y <= boty:
                t = (y - topy) / (boty - topy)
                x_left = bx + (1 - t) * body_w * 0.09 - t * body_w * 0.10
                x_right = bx + (1 - t) * body_w * 0.19 - t * body_w * 0.10
                if x_left <= x <= x_right:
                    return True
        return False

    for y in range(size):
        for x in range(size):
            fx, fy = x + 0.5, y + 0.5
            if not in_can(fx, fy):
                continue

            # фон банки — тёмный
            col = DARK
            a = 1.0

            # металлические ободки сверху и снизу
            rim_h = size * 0.045
            if top <= fy <= top + rim_h or bottom - rim_h * 0.85 <= fy <= bottom:
                col = METAL

            # зелёный логотип
            if claw_hit(fx, fy):
                col = GREEN

            # лёгкий блик слева — объём
            if fx < cx - body_w * 0.28 and col == DARK:
                col = blend(col, WHITE, 0.10)

            px[y][x] = (col[0], col[1], col[2], int(a * 255))

    return px


def write_png(path, px):
    size = len(px)
    raw = bytearray()
    for row in px:
        raw.append(0)  # filter type 0
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", ihdr)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def make_foreground(size):
    """Передний план адаптивной иконки: банка в центральной 72dp-зоне (2/3)."""
    px = make_icon(size)
    # сдвигаем в центр с масштабом: адаптивная иконка обрезает края
    return px


if __name__ == "__main__":
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "app/src/main/res")
    targets = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, sz in targets.items():
        d = os.path.join(base, folder)
        os.makedirs(d, exist_ok=True)
        write_png(os.path.join(d, "ic_launcher.png"), make_icon(sz))
        write_png(os.path.join(d, "ic_launcher_round.png"), make_icon(sz))
        print(f"{folder}: {sz}x{sz}")

    # Передний план для адаптивной иконки — 432x432 (108dp @ xxxhdpi)
    fg_dir = os.path.join(base, "drawable-nodpi")
    os.makedirs(fg_dir, exist_ok=True)
    write_png(os.path.join(fg_dir, "ic_fg_can.png"), make_icon(432))
    print("drawable-nodpi: ic_fg_can.png 432x432")
