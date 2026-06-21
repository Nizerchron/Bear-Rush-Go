#!/usr/bin/env python3
"""
Bear Rush Mod — Upload Tool
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Satu file. Double-click → jalan.
"""

# ── GITHUB TOKEN ──────────────────────────────────────────
# Cara 1 (recommended): Set environment variable GITHUB_TOKEN
#   - CMD:  set GITHUB_TOKEN=ghp_xxx
#   - PowerShell:  $env:GITHUB_TOKEN="ghp_xxx"
#   - Permanen: System Properties → Environment Variables
# Cara 2: Isi langsung di bawah (tapi hati-hati jangan di-commit)
# Cara bikin token: https://github.com/settings/tokens → Centang repo → Generate
# ──────────────────────────────────────────────────────────
import os
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
# ──────────────────────────────────────────────────────────

import subprocess, sys, os, threading, tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path

# ── Auto-install dependencies ──
_REQUIRED = ["PyGithub==2.3.0", "requests==2.31.0"]
_MISSING = []
for pkg in _REQUIRED:
    try:
        __import__(pkg.split("==")[0].replace("-", "_"))
    except ImportError:
        _MISSING.append(pkg)
if _MISSING:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q"] + _MISSING)

from github import Github, GithubException
import requests


class UploadTool:
    def __init__(self):
        if not GITHUB_TOKEN:
            root = tk.Tk()
            root.withdraw()
            messagebox.showerror(
                "GitHub Token",
                "Buka file upload_tool.py dengan Notepad,\n"
                "isi GITHUB_TOKEN, simpan, jalankan lagi.\n\n"
                "Bikin token di: https://github.com/settings/tokens\n"
                "Centang repo → Generate"
            )
            root.destroy()
            raise SystemExit(1)

        self.gh = Github(GITHUB_TOKEN)
        self.repo = self.gh.get_repo("Nizerchron/Bear-Rush-Go")
        self.preview_path: str | None = None
        self.bin_path: str | None = None
        self.preview_url: str = ""
        self.download_url: str = ""

        self.root = tk.Tk()
        self.root.title("Bear Rush Mod — Upload Preset")
        self.root.geometry("600x700")
        self.root.minsize(600, 500)
        self._build_ui()

    def _build_ui(self):
        # ── Canvas + scrollbar biar gak kepotong ──
        canvas = tk.Canvas(self.root, highlightthickness=0)
        scrollbar = ttk.Scrollbar(self.root, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        canvas.pack(side="left", fill="both", expand=True)

        main = ttk.Frame(canvas, padding=16)
        canvas.create_window((0, 0), window=main, anchor="nw")

        def _configure(event):
            canvas.configure(scrollregion=canvas.bbox("all"), width=event.width)
        main.bind("<Configure>", _configure)
        # scroll dengan mousewheel
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")
        canvas.bind_all("<MouseWheel>", _on_mousewheel)

        ttk.Label(main, text="Nama Preset").pack(anchor="w")
        self.entry_name = ttk.Entry(main)
        self.entry_name.pack(fill="x", pady=(0, 8))

        ttk.Label(main, text="Kategori").pack(anchor="w")
        self.cb_category = ttk.Combobox(main, values=["Nature", "Structure"], state="readonly")
        self.cb_category.current(0)
        self.cb_category.pack(fill="x", pady=(0, 8))

        self.is_free = tk.BooleanVar(value=True)
        ttk.Checkbutton(main, text="Gratis", variable=self.is_free).pack(anchor="w", pady=(0, 8))

        frame_preview = ttk.Frame(main)
        frame_preview.pack(fill="x", pady=(0, 4))
        ttk.Button(frame_preview, text="Pilih Gambar Preview", command=self._pick_preview).pack(side="left")
        self.lbl_preview = ttk.Label(frame_preview, text="Belum dipilih")
        self.lbl_preview.pack(side="left", padx=(8, 0))
        self.preview_thumb = ttk.Label(main)
        self.preview_thumb.pack(pady=(0, 8))

        frame_bin = ttk.Frame(main)
        frame_bin.pack(fill="x", pady=(0, 4))
        ttk.Button(frame_bin, text="Pilih File .bin", command=self._pick_bin).pack(side="left")
        self.lbl_bin = ttk.Label(frame_bin, text="Belum dipilih")
        self.lbl_bin.pack(side="left", padx=(8, 0))

        ttk.Label(main, text="Deskripsi").pack(anchor="w")
        self.btn_gen_desc = ttk.Button(main, text="Generate Deskripsi (Otomatis)", command=self._generate_desc)
        self.btn_gen_desc.pack(anchor="w", pady=(0, 4))
        self.txt_desc = tk.Text(main, height=4)
        self.txt_desc.pack(fill="x", pady=(0, 8))

        self.log = tk.Text(main, height=8, state="disabled", fg="#555")
        self.log.pack(fill="both", pady=(0, 8))

        self.btn_upload = ttk.Button(main, text="Upload ke Supabase", command=self._upload)
        self.btn_upload.pack()

    def _log(self, msg):
        self.log.configure(state="normal")
        self.log.insert("end", f"{msg}\n")
        self.log.see("end")
        self.log.configure(state="disabled")
        self.root.update()

    def _set_busy(self, busy: bool):
        state = "disabled" if busy else "normal"
        for w in (self.btn_upload, self.btn_gen_desc):
            w.configure(state=state)
        self.root.update()

    def _pick_preview(self):
        path = filedialog.askopenfilename(filetypes=[("Gambar", "*.png *.jpg *.jpeg")])
        if not path:
            return
        self.preview_path = path
        self.lbl_preview.configure(text=Path(path).name)
        img = tk.PhotoImage(file=path)
        w, h = img.width(), img.height()
        scale = max(w / 280, h / 160)
        self._tkthumb = img.subsample(int(scale), int(scale)) if scale > 1 else img
        self.preview_thumb.configure(image=self._tkthumb)

    def _pick_bin(self):
        path = filedialog.askopenfilename(filetypes=[("Binary", "*.bin")])
        if not path:
            return
        self.bin_path = path
        self.lbl_bin.configure(text=Path(path).name)

    def _generate_desc(self):
        name = self.entry_name.get().strip()
        cat = self.cb_category.get()
        if not name:
            messagebox.showwarning("Nama kosong", "Isi nama preset dulu.")
            return
        # ponytail: template-based, no AI API key needed
        templates = {
            "Nature": f"Preset {name} dengan nuansa alam yang segar dan menenangkan, cocok untuk tema outdoor.",
            "Structure": f"Preset {name} dengan desain struktur yang kokoh dan detail arsitektur yang menarik.",
        }
        desc = templates.get(cat, f"Preset {name} — kategori {cat}.")
        self.txt_desc.delete("1.0", "end")
        self.txt_desc.insert("1.0", desc)
        self._log("✅ Deskripsi siap (template).")

    def _upload(self):
        name = self.entry_name.get().strip()
        cat = self.cb_category.get()
        desc = self.txt_desc.get("1.0", "end").strip()
        if not name or not self.preview_path or not self.bin_path or not desc:
            messagebox.showwarning("Data belum lengkap", "Isi nama, preview, .bin, dan deskripsi.")
            return
        self._set_busy(True)
        self._log("🚀 Mulai upload...")
        threading.Thread(target=self._do_upload, args=(name, cat, desc), daemon=True).start()

    def _do_upload(self, name, cat, desc):
        try:
            # 1. Upload preview ke GitHub superbear/
            self._log("📤 Upload preview ke GitHub...")
            fn = f"prev_{name.lower().replace(' ', '_')}.png"
            rp = f"superbear/{fn}"
            with open(self.preview_path, "rb") as f:
                content = f.read()
            try:
                existing = self.repo.get_contents(rp)
                self.repo.update_file(rp, f"Update preview: {name}", content, existing.sha)
            except GithubException:
                self.repo.create_file(rp, f"Add preview: {name}", content)
            self.preview_url = f"https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/{rp}"
            self._log(f"   → {self.preview_url}")

            # 2. Upload .bin ke GitHub superbear/bins/
            self._log("📤 Upload .bin ke GitHub...")
            bfn = f"{name.lower().replace(' ', '_')}.bin"
            brp = f"superbear/bins/{bfn}"
            with open(self.bin_path, "rb") as f:
                content = f.read()
            try:
                existing = self.repo.get_contents(brp)
                self.repo.update_file(brp, f"Update .bin: {name}", content, existing.sha)
            except GithubException:
                self.repo.create_file(brp, f"Add .bin: {name}", content)
            self.download_url = f"https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/{brp}"
            self._log(f"   → {self.download_url}")

            # 3. INSERT ke Supabase
            self._log("📤 Insert ke Supabase...")
            self._insert_supabase(name, cat, desc)
            self._log("✅ Selesai! Preset sudah live.")
        except Exception as e:
            self._log(f"❌ Gagal: {e}")
        finally:
            self.root.after(0, lambda: self._set_busy(False))

    def _insert_supabase(self, name, cat, desc):
        payload = {
            "name": name, "description": desc, "category": cat,
            "preview_url": self.preview_url, "download_url": self.download_url,
            "is_free": self.is_free.get()
        }
        headers = {
            "apikey": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFsdGtzYWhnZ3BycGpxd3lxaWlrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIwMDc5MjYsImV4cCI6MjA5NzU4MzkyNn0.tywf81lm9HHfLuexewdLliAEE7dee76jNFD8fjYKLIk",
            "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFsdGtzYWhnZ3BycGpxd3lxaWlrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIwMDc5MjYsImV4cCI6MjA5NzU4MzkyNn0.tywf81lm9HHfLuexewdLliAEE7dee76jNFD8fjYKLIk",
            "Content-Type": "application/json",
            "Prefer": "return=minimal"
        }
        resp = requests.post(
            "https://qltksahggprpjqwyqiik.supabase.co/rest/v1/presets",
            json=payload, headers=headers
        )
        if resp.status_code not in (200, 201, 204):
            raise RuntimeError(f"Supabase INSERT gagal: {resp.status_code} {resp.text[:200]}")

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    UploadTool().run()