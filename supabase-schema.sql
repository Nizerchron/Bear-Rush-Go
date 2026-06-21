-- Buat tabel presets (TANPA RLS — paling simpel)
CREATE TABLE presets (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT '',
  preview_url TEXT NOT NULL,
  download_url TEXT NOT NULL,
  is_free BOOLEAN NOT NULL DEFAULT true,
  downloads BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index biar bisa sorting by terbaru
CREATE INDEX idx_presets_created_at ON presets(created_at DESC);

----------------------------------------------------------
-- Insert data preset
----------------------------------------------------------
INSERT INTO presets (name, description, category, preview_url, download_url, is_free)
VALUES 
(
  'Pre Sakura Putih',
  'Preset bertema sakura dengan nuansa putih bersih, cocok untuk karakter perempuan',
  'Nature',
  'https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/superbear/prev_bunga_sakura_putih.png',
  'https://drive.google.com/uc?export=download&id=1EsrXxfTWJxhWyacOzb3pUPqJ8XHEbXf1',
  true
),
(
  'Pre Jembatan Original',
  'Preset jembatan klasik dengan gaya original',
  'Structure',
  'https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/superbear/prev_jembatan_original.png',
  'https://drive.google.com/uc?export=download&id=1IIChHJS6m_bJ7khrVrhlztouZJIvckVS',
  true
),
(
  'Pre Jembatan Live',
  'Preset jembatan dengan nuansa live dan dinamis',
  'Structure',
  'https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/superbear/prev_jembatan_live.png',
  'https://drive.google.com/uc?export=download&id=1M2mRdj8bDm6na8gHxpEOzdV4KkWVFDvZ',
  true
),
(
  'Pre Jembatan Air Terjun',
  'Preset jembatan dengan pemandangan air terjun yang indah',
  'Nature',
  'https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/superbear/prev_jembatan_air_terjun.png',
  'https://drive.google.com/uc?export=download&id=1QIPD3xV6BrY2WYGKmeBEa5Q1jO2KWz-I',
  true
);

-- Cek data
SELECT * FROM presets ORDER BY id;