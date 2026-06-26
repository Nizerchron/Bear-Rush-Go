-- Buat tabel presets (TANPA RLS — paling simpel)
CREATE TABLE IF NOT EXISTS presets (
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

CREATE INDEX IF NOT EXISTS idx_presets_created_at ON presets(created_at DESC);

-- Tabel profiles untuk detail user (koin, bio, dll)
CREATE TABLE IF NOT EXISTS profiles (
  id UUID PRIMARY KEY REFERENCES auth.users ON DELETE CASCADE,
  username TEXT NOT NULL UNIQUE,
  avatar_url TEXT NOT NULL DEFAULT '',
  bio TEXT NOT NULL DEFAULT '',
  coins INT NOT NULL DEFAULT 100,
  updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Profiles are viewable by everyone" ON profiles FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = id);

-- Trigger otomatis untuk buat profile saat user sign up di Supabase Auth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.profiles (id, username, coins)
  VALUES (
    new.id,
    COALESCE(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)),
    100
  );
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Tabel user_presets untuk mencatat preset yang didownload user
CREATE TABLE IF NOT EXISTS user_presets (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  preset_id BIGINT NOT NULL,
  preset_name TEXT NOT NULL DEFAULT '',
  preset_preview_url TEXT NOT NULL DEFAULT '',
  preset_category TEXT NOT NULL DEFAULT '',
  acquired_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(user_id, preset_id)
);

ALTER TABLE user_presets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "User presets are viewable by everyone" ON user_presets FOR SELECT USING (true);
CREATE POLICY "Users can add own presets" ON user_presets FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can remove own presets" ON user_presets FOR DELETE USING (auth.uid() = user_id);

-- Tabel preset_comments untuk diskusi preset
CREATE TABLE IF NOT EXISTS preset_comments (
  id BIGSERIAL PRIMARY KEY,
  preset_id BIGINT NOT NULL,
  user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  username TEXT NOT NULL DEFAULT 'Guest',
  comment TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE preset_comments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Comments are viewable by everyone" ON preset_comments FOR SELECT USING (true);
CREATE POLICY "Users can post comments" ON preset_comments FOR INSERT WITH CHECK (auth.uid() = user_id OR user_id IS NULL);