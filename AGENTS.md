# AGENTS.md

このリポジトリは Play Framework (Scala) でブログデータを「投稿」するのではなく「閲覧」するための viewer です。

## 重要な前提

- DI は compiled-time DI。`AppLoader` / `MyComponents` で明示的に wiring する。
- `conf/blog` はブログデータ専用リポジトリを submodule として取り込む。
- 起動時に `conf/blog/**/meta.yaml` を読み取り、SQLite を構築する（viewer 用）。
- `macwire` は使わない。

## データ形式（blog データ）

- 1記事はディレクトリ単位。
- `meta.yaml` は記事メタデータ:
  - `title`: Optional（例: Twitter など title が無いデータ源を想定）
  - `published_at`, `modified_at`: Optional（ISO-8601 文字列を想定。ただし importer 側で UTC に正規化する）
  - `tags`: Optional。ある場合は YAML 配列（`[a, b]` or `- a`）のみを想定。
- 本文は `README.md`（Markdown）を参照する（`meta.yaml` に `body` は存在しない）。

## source の解釈

- `conf/blog/<xx_title>/meta.yaml` は `source = "github"` として扱う。
- `conf/blog/00_archive/<source>/<xx_title>/meta.yaml` は `<source>` を `source` 名として扱う。

## Markdown の扱い（viewer）

- viewer として Markdown を表示する必要はないので、取り込み時に Markdown を HTML に変換し DB の `blogs.body` に保存する。
- URL らしい文字列（`http://` / `https://`）は自動リンク化する。
- 画像は `README.md` からの相対パスで書かれている前提。クラスパス上にしか無い場合でも表示できるよう、取り込み時に画像を読み込み `data:` URI (base64) に変換して HTML に埋め込む。

## DB（SQLite）

- 既定パスは `data/blog.db`（git 管理外）。本番は環境変数で上書きする。
  - `BLOG_DB_URL=jdbc:sqlite:/path/to/blog.db`
- 起動時に `conf/init.sql` を実行してスキーマを作る。
- 現状は毎回 DROP → CREATE を行う（再生成前提）。

スキーマ（概要）:

- `blogs`: 記事本体（`body` は HTML）
- `tags`: タグ辞書
- `blog_tags`: 中間テーブル

## 実行方法

- 初期化: `mise install` / `sbt compile`
- 起動: `sbt run`

## 命名・実装方針（継続的な合意）

- これは viewer なので、UI/コード/DB では原則 `post` ではなく `blog` で命名を統一する。
- 変数のサフィックスに型名は付けない（例: `bodyHtml: Html` なら `body: Html`）。
- 変換元/変換後が同時に存在する場合のみ区別する（例: `bodyMarkdown` / `bodyHtml`）。
- Scala 3 の `given/using` を使い、`implicit` は使わない。

