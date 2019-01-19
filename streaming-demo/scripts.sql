CREATE TABLE sensor_reading (
  reading_id SERIAL PRIMARY KEY,
  reading_guid TEXT NOT NULL,
  reading_timestamp TIMESTAMP,
  reading_value DOUBLE,
  reading_description TEXT NOT NULL,
  sensor_id TEXT
);