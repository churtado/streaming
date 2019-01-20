CREATE TABLE sensor_reading (
  reading_id SERIAL PRIMARY KEY,
  reading_guid TEXT NOT NULL,
  reading_timestamp TIMESTAMP NOT NULL,
  reading_value DOUBLE PRECISION,
  reading_description TEXT NOT NULL,
  sensor_id TEXT
);

CREATE TABLE tweets (
  id BIGINT,
  "text" TEXT NOT NULL,
  created_at TEXT,
  timestamp_ms BIGINT
);

ALTER TABLE tweets ADD CONSTRAINT constraint_name UNIQUE (id);