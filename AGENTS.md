# AGENTS.md

このファイルは「このリポジトリ固有の合意」を短く維持するためのものです。
標準的な Play/Scala の使い方はここに増やさず、必要最低限のみ記載します。

## Scope

- プロダクトは blog viewer（投稿機能は持たない）。
- リポジトリ構成は `play` / `zio` / `markdownRenderer` / `blog`。
- `zio` は `play` に依存しない。

## Architecture

- DI は compiled-time DI（`AppLoader` / `MyComponents`）。
- `macwire` は使わない。
- 起動時に blog データから DB を再生成する前提（DROP -> CREATE）。

## Data Rules

- blog データ root: `blog/src/main/resources/blog`。
- 本文は `README.md`、メタは `meta.yaml`。
- `tags` は YAML 配列のみを許可（文字列カンマ区切りは不可）。
- `README.md` の大文字小文字は区別する。

## Source And ID

- `blog/<article>/meta.yaml` は `source = github`。
- `blog/00_archive/<source>/<article>/meta.yaml` は `<source>` を利用。
- URL は `blogs.stable_id` を使用。
- `stable_id = s"$source-$prefix"`。
- `prefix` はディレクトリ名の先頭連続数字、なければ `_` より前。

## Markdown

- Markdown は import 時に HTML 化して `blogs.body` に保存。
- URL 自動リンク化・画像 data URI 化を行う。
- コードブロックは tm4e ハイライト。
- Mermaid はクライアント描画。

## DB

- 既定は H2（PostgreSQL mode）。
- DB ファイルは `play/data`（git 管理外）。
- SQL は H2(pg mode) 互換を優先し、方言依存を避ける。

## Time

- DB 保存は UTC ISO-8601 (`...Z`)。
- 表示書式は `blog.datetime.format`。
- 表示ゾーンは実行環境デフォルト（Docker では `TZ`）。

## Coding Conventions

- Scala 3 の `given/using` を使い、`implicit` は使わない。
- 依存 import はファイル先頭にまとめる。
- コレクションは `Seq` 基本、空は `Nil`。
- `asInstanceOf` は使わない。
- `try/catch` は使わない。例外処理は `Try` / `Either` を使う（`Try#get` 禁止）。
- service 層はドメインエラーを `Either` で返し、起動失敗化の判断は loader 側で行う。
- Controller は `Action.async` + `DB.futureLocalTx` を使う。
- 新規クラス追加時は単体テストを追加する。

## Docs Policy

- `README.md` はセットアップ/起動手順のみを中心に保つ。
- 詳細仕様は必要時のみ `docs/*.md` に追加する。
