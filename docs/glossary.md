# Glossary

用語の「このリポジトリ内での意味」をまとめます。

## Blog

viewer の表示単位。DB テーブル `blogs` の1行に対応する。

## Source

元のブログサイト名（例: `qiita`, `hatena_m3`）。`blog` 直下のデータは `github` 扱い。

## Blog Root

取り込み対象のルートディレクトリ。通常は `blog`。

## meta.yaml

記事メタデータ。必須ではなく、存在する記事のみ取り込み対象。

- `title`: Optional（無いデータ源があるため）
- `published_at`, `modified_at`: Optional（import 時に UTC の ISO-8601 に正規化）
- `tags`: Optional。ある場合は YAML 配列

## README.md

記事本文（Markdown）。取り込み時に HTML へ変換され、DB の `blogs.body` に保存される。

## Archive Layout

`blog/00_archive/<source>/<article>/meta.yaml` というディレクトリ構造。`<source>` を `Source` として扱う。
