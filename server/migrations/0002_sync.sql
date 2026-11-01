-- Gesynchroniseerde gegevens per account: logins (versleuteld) en per login favorieten/groepen/volgorde.
CREATE TABLE sync (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  updated INTEGER NOT NULL,
  PRIMARY KEY (user_id, key)
);
