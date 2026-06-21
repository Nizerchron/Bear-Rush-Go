@echo off
echo ==================================================
echo         RESTORE SYSTEM - BEAR RUSH MOD
echo ==================================================
echo PERINGATAN: Perintah ini akan mengembalikan kode aplikasi
echo ke keadaan bersih (saat backup dibuat) dan menghapus
echo semua file/folder baru yang Anda tambahkan setelahnya.
echo.
set /p confirm="Apakah Anda yakin ingin melakukan restore? (y/n): "
if /i "%confirm%" neq "y" (
    echo Restore dibatalkan.
    pause
    exit /b
)

echo.
echo Menghapus file dan folder baru...
git clean -fd

echo Mengembalikan file yang dimodifikasi...
git reset --hard HEAD

echo.
echo ==================================================
echo  RESTORE SUKSES! Aplikasi kembali ke keadaan bersih.
echo ==================================================
pause
