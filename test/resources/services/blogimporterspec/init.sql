DROP TABLE IF EXISTS blog_tags;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS blogs;

CREATE TABLE blogs (
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  published_at TEXT,
  modified_at TEXT,
  source TEXT NOT NULL
);

CREATE TABLE tags (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE blog_tags (
  blog_id INTEGER NOT NULL,
  tag_id INTEGER NOT NULL,
  UNIQUE(blog_id, tag_id)
);
