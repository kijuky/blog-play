# AGENTS.md

このリポジトリは Play Framework (Scala) でブログデータを「投稿」するのではなく「閲覧」するための viewer です。

このファイルは設計・実装方針の合意を集約するので、方針が変わったら随時更新します。
指摘を受けた場合も、必要に応じてこのファイルを更新します。

## 重要な前提

- DI は compiled-time DI。`AppLoader` / `MyComponents` で明示的に wiring する。
- `conf/blog` はブログデータ専用リポジトリを submodule として取り込む。
- 起動時に `conf/blog/**/meta.yaml` を読み取り、DB（H2）を構築する（viewer 用）。
- `macwire` は使わない。

## 起動時フロー

- `MyComponents` で DB 接続プール初期化（ScalikeJDBC）。
- `conf/init.sql` 実行（DROP → CREATE 前提）。
- `conf/blog` から全記事を import（失敗したら起動失敗）。

## データ形式（blog データ）

- 1記事はディレクトリ単位。
- `meta.yaml` は記事メタデータ。
- `meta.yaml:title` は Optional（title が無いデータ源を想定）。
- `meta.yaml:published_at` は Optional（ISO-8601 文字列を想定。importer 側で UTC に正規化）。
- `meta.yaml:modified_at` は Optional（ISO-8601 文字列を想定。importer 側で UTC に正規化）。
- `meta.yaml:tags` は Optional。ある場合は YAML 配列のみ（文字列のカンマ区切り等には対応しない）。
- 本文は `README.md`（Markdown）を参照する（`meta.yaml` に `body` は存在しない）。
- `README.md` の大文字小文字は区別する（`READMe.md` 等はデータ側の問題として起動失敗でよい）。

## source の解釈

- `conf/blog/<article>/meta.yaml` は `source = "github"` として扱う。
- `conf/blog/00_archive/<source>/<article>/meta.yaml` は `<source>` を `source` 名として扱う。

## stable_id（URL 用の安定キー）

- DB の `blogs.stable_id` を URL パラメータに使う。
- 生成ルールは `stable_id = s"$source-$prefix"`。
- `prefix` はディレクトリ名から導く。
- 先頭の連続数字がある場合はそれ（例: `06_title` → `06`、`02` → `02`）。
- 先頭数字が無い場合は `_` の手前（例: `abc_title` → `abc`）。
- どちらも取れない場合は data 不正として import 失敗。

## Markdown の扱い（viewer）

- viewer として Markdown を表示する必要はないので、取り込み時に Markdown を HTML に変換し DB の `blogs.body` に保存する。
- URL らしい文字列（`http://` / `https://`）は自動リンク化する。
- 画像は `README.md` からの相対パスで書かれている前提。
- クラスパス上にしか無い画像でも表示できるよう、取り込み時に画像を読み込み `data:` URI (base64) に変換して HTML に埋め込む。

## DB（H2 / PostgreSQL mode）

- 既定パスは `data/blog`（git 管理外。H2 が `data/blog.mv.db` などを作成）。
- 本番は環境変数で JDBC URL を上書きする（例: `BLOG_DB_URL=jdbc:h2:file:/path/to/blog;MODE=PostgreSQL`）。
- 起動時に `conf/init.sql` を実行してスキーマを作る。
- 現状は毎回 DROP → CREATE を行う（再生成前提）。
- SQL は H2(pg mode) を前提にしつつ、`insert or ignore` のような方言に寄せない。

## 日付とタイムゾーン

- DB 保存は importer が日時を UTC の ISO-8601（`...Z`）に正規化して TEXT 保存する。
- 表示フォーマットは `application.conf` の `blog.datetime.format` で指定する。
- 表示ゾーンは実行環境のデフォルトゾーンを使う（Docker では `TZ` を設定して合わせる）。

## 設定と責務の境界

- `AppLoader` / `MyComponents` は環境依存（設定・環境変数・ファイル・DB）を集約する。
- ドメイン側は raw 値から型付けする（例: `BlogDateTime.from(rawTz, rawPattern)` が `ZoneId` と `DateTimeFormatter` を作る）。
- 設定ミスは開発者が直す前提なので、過剰にエラー型を作らない（例外で落としてよい）。

## 実行方法

- 初期化: `mise install` / `sbt compile`
- 起動: `sbt run`

## 命名・実装方針（継続的な合意）

- viewer なので、UI/コード/DB では原則 `post` ではなく `blog` で命名を統一する。
- 変数のサフィックスに型名は付けない（例: `bodyHtml: Html` なら `body: Html`）。
- 変換元/変換後が同時に存在する場合のみ区別する（例: `bodyMarkdown` / `bodyHtml`）。
- Scala 3 の `given/using` を使い、`implicit` は使わない。
- Scala のコレクションを使う（`Seq` を基本、空は `Nil`）。
- `try/catch` は使わない。例外を扱うなら `scala.util.Try` を使い、`Try#get` は使わない。
- `asInstanceOf` は使わない。
- service 層はナイーブに throw しない（ドメインエラーは `Either` で返す）。起動失敗にするかは `AppLoader/MyComponents` 側で決める。
- Controller は `Action.async` を使い、DB は `DB.futureLocalTx` で非同期にする。
- 新しいクラスを追加したら、そのクラスの単体テストを追加する。

## 用語

- 用語集は `docs/glossary.md` に置く。
