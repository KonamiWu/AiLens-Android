import os
from PIL import Image, ImageOps

# 用當前資料夾
folder_path = os.getcwd()
max_size = 1000

image_extensions = ('.jpg', '.jpeg', '.png', '.bmp', '.gif', '.tiff', '.webp')

for filename in os.listdir(folder_path):
    if not filename.lower().endswith(image_extensions):
        continue

    image_path = os.path.join(folder_path, filename)
    try:
        with Image.open(image_path) as img:
            # 跳過動態 GIF/WebP，避免壓縮只處理第一幀
            if getattr(img, "is_animated", False):
                print(f"Skipped (animated): {filename}")
                continue

            # 依 EXIF 方向轉正
            img = ImageOps.exif_transpose(img)

            w, h = img.size
            if w > max_size or h > max_size:
                img.thumbnail((max_size, max_size), Image.LANCZOS)

            # 為了避免顏色模式問題，先轉成適合壓縮的模式
            fmt = (img.format or '').upper()
            save_kwargs = {}

            if fmt in ("JPEG", "JPG", "WEBP"):
                img = img.convert("RGB")
                # 壓縮參數可自行調整
                if fmt in ("JPEG", "JPG"):
                    save_kwargs.update(dict(quality=90, optimize=True, progressive=True))
                elif fmt == "WEBP":
                    save_kwargs.update(dict(quality=90, method=6))

            # 1) 最保險：存檔時不帶 EXIF（包含 Orientation），避免之後再被旋轉
            # 如果你想保留其他 EXIF，可改用「方案 2」
            img.save(image_path, **save_kwargs)
            print(f"Saved: {filename} -> {img.size}")

            # 2)（可選）若要保留 EXIF：把 Orientation 重設為 1 再帶回去
            # exif = img.getexif()
            # if exif:
            #     exif[274] = 1  # 274 是 Orientation 標籤
            #     if fmt in ("JPEG", "JPG", "TIFF", "WEBP"):
            #         save_kwargs["exif"] = exif.tobytes()
            # img.save(image_path, **save_kwargs)

        # with 區塊會自動關閉檔案
    except Exception as e:
        print(f"Failed to process {filename}: {e}")
