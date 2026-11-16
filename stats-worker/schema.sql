CREATE TABLE IF NOT EXISTS pings (
  day TEXT NOT NULL,
  install_id TEXT NOT NULL,
  version TEXT NOT NULL,
  platform TEXT NOT NULL,
  country TEXT NOT NULL,
  PRIMARY KEY (day, install_id)
);

CREATE INDEX IF NOT EXISTS pings_day ON pings (day);
