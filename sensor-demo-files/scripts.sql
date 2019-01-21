DROP TABLE IF EXISTS "sensor_reading";
DROP SEQUENCE IF EXISTS sensor_reading_reading_id_seq;

CREATE SEQUENCE sensor_reading_reading_id_seq INCREMENT 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1;

CREATE TABLE "public"."sensor_reading" (
    "reading_id" integer DEFAULT nextval('sensor_reading_reading_id_seq') NOT NULL,
    "reading_guid" text,
    "reading_timestamp" timestamp NOT NULL,
    "reading_value" double precision,
    "reading_description" text,
    "sensor_id" text,
    CONSTRAINT "sensor_reading_pkey" PRIMARY KEY ("reading_id")
) WITH (oids = false);


CREATE TABLE tweets (
  id BIGINT,
  "text" TEXT NOT NULL,
  created_at TEXT,
  timestamp_ms BIGINT
);

ALTER TABLE tweets ADD CONSTRAINT constraint_name UNIQUE (id);