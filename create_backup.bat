@echo off
echo ==================================================
echo         CREATE BACKUP - BEAR RUSH MOD
echo ==================================================
echo Perintah ini akan menyimpan kondisi kode aplikasi saat ini
echo sebagai titik backup baru.
echo.
set /p note="Masukkan catatan untuk backup ini (misal: setelah iklan sukses): "
if "%note%"=="" set note=Backup manual oleh user

echo.
echo Mendaftarkan semua file...
git add .

echo Menyimpan backup...
git commit -m "Backup: %note%"

echo.
echo ==================================================
echo  BACKUP SUKSES! Titik restore baru telah dibuat.
echo ==================================================
pause
