@echo off
:menu
cls
echo ==================================================
echo       BACKUP & RESTORE SYSTEM - BEAR RUSH MOD
echo ==================================================
echo Silakan pilih menu di bawah ini:
echo.
echo [1] Buat Backup Baru (Simpan Kode Saat Ini)
echo [2] Lakukan Restore (Kembalikan ke Backup Terakhir)
echo [3] Keluar
echo.
set /p choice="Masukkan pilihan Anda (1/2/3): "

if "%choice%"=="1" goto backup
if "%choice%"=="2" goto restore
if "%choice%"=="3" goto exit
goto menu

:backup
cls
echo ==================================================
echo         BUAT BACKUP BARU
echo ==================================================
echo Menyimpan seluruh kode saat ini sebagai titik backup baru.
echo.
set /p note="Masukkan catatan untuk backup ini (misal: setelah iklan sukses): "
if "%note%"=="" set note=Backup manual oleh user
echo.
echo Mendaftarkan semua file...
git add .
echo Menyimpan backup...
git commit -m "Backup: %note%"
echo.
echo BACKUP SUKSES! Titik restore baru telah dibuat.
echo.
pause
goto menu

:restore
cls
echo ==================================================
echo         LAKUKAN RESTORE
echo ==================================================
echo PERINGATAN: Perintah ini akan mengembalikan kode aplikasi
echo ke keadaan bersih (saat backup dibuat) dan menghapus
echo semua file/folder baru yang Anda tambahkan setelahnya.
echo.
set /p confirm="Apakah Anda yakin ingin melakukan restore? (y/n): "
if /i "%confirm%" neq "y" (
    echo.
    echo Restore dibatalkan.
    echo.
    pause
    goto menu
)
echo.
echo Menghapus file dan folder baru...
git clean -fd
echo Mengembalikan file yang dimodifikasi...
git reset --hard HEAD
echo.
echo RESTORE SUKSES! Aplikasi kembali ke keadaan bersih.
echo.
pause
goto menu

:exit
exit
