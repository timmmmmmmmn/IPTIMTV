-- Accounts, sessies en kijkgeschiedenis voor IPTIMTV.
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
  pass_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  created INTEGER NOT NULL
);

CREATE TABLE sessions (
  token_hash TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device TEXT,
  created INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);

-- Eén regel per film/aflevering/kanaal per gebruiker; geen stream-adressen (die bevatten inloggegevens).
CREATE TABLE history (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_key TEXT NOT NULL,
  kind TEXT NOT NULL,
  parent TEXT,
  title TEXT NOT NULL,
  subtitle TEXT,
  image TEXT,
  position INTEGER NOT NULL DEFAULT 0,
  duration INTEGER NOT NULL DEFAULT 0,
  watched INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL,
  PRIMARY KEY (user_id, item_key)
);
CREATE INDEX history_recent ON history (user_id, updated DESC);

-- Kijktijd per dag, voor de statistieken.
CREATE TABLE daily (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  kind TEXT NOT NULL,
  seconds INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day, kind)
);
