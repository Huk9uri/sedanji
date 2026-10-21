CREATE TABLE chefs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE CHECK (length(trim(name)) > 0)
);
CREATE TABLE videos (
  id VARCHAR(11) PRIMARY KEY,
  title TEXT NOT NULL,
  channel_title TEXT NOT NULL,
  published_at TIMESTAMPTZ NOT NULL,
  description TEXT NOT NULL,
  extraction_warning TEXT NOT NULL,
  imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE restaurants (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kakao_place_id VARCHAR(40) NOT NULL UNIQUE,
  name TEXT NOT NULL,
  road_address TEXT NOT NULL,
  address TEXT NOT NULL,
  longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  place_url TEXT NOT NULL
);
CREATE TABLE mentions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  video_id VARCHAR(11) NOT NULL REFERENCES videos(id),
  chef_id BIGINT REFERENCES chefs(id),
  restaurant_id BIGINT REFERENCES restaurants(id),
  extracted_name TEXT NOT NULL CHECK (length(trim(extracted_name)) > 0),
  extracted_address TEXT NOT NULL DEFAULT '',
  source_line INTEGER NOT NULL CHECK (source_line >= 0),
  evidence TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  review_note TEXT,
  reviewed_by VARCHAR(100),
  reviewed_at TIMESTAMPTZ,
  UNIQUE(video_id, source_line, extracted_name),
  CHECK (status <> 'APPROVED' OR (chef_id IS NOT NULL AND restaurant_id IS NOT NULL AND reviewed_at IS NOT NULL AND length(trim(review_note)) > 0))
);
CREATE TABLE place_candidates (
  mention_id BIGINT NOT NULL REFERENCES mentions(id),
  kakao_place_id VARCHAR(40) NOT NULL,
  name TEXT NOT NULL,
  road_address TEXT NOT NULL,
  address TEXT NOT NULL,
  longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  place_url TEXT NOT NULL,
  fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (mention_id, kakao_place_id)
);
CREATE INDEX mentions_status_idx ON mentions(status);
CREATE INDEX mentions_restaurant_idx ON mentions(restaurant_id);
