from PIL import Image, ImageDraw, ImageFont

S = 256
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 圆角方块背景
d.rounded_rectangle([8, 8, S - 8, S - 8], radius=56, fill=(76, 141, 255, 255))
# 内部深色细边，让图标在浅色任务栏上也立得住
d.rounded_rectangle([8, 8, S - 8, S - 8], radius=56, outline=(30, 60, 120, 90), width=4)

font = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 132)
text = "FX"
bbox = d.textbbox((0, 0), text, font=font)
w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
d.text(((S - w) / 2 - bbox[0], (S - h) / 2 - bbox[1] - 6), text, font=font, fill=(255, 255, 255, 255))

img.save(r"E:\外汇\build\fx.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("icon ok")
