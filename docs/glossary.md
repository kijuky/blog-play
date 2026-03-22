# Glossary

このリポジトリで使う用語の最小定義です。

## Blog

表示単位。DB テーブル `blogs` の1行に対応する。

## Source

元ブログサイト名（例: `qiita`, `hatena_m3`）。`blog` 直下は `github` 扱い。

## Blog Root

取り込み対象ディレクトリのルート。通常は `blog`。

## meta.yaml

記事メタデータファイル。

## README.md

記事本文（Markdown）ファイル。

## Archive Layout

`blog/00_archive/<source>/<article>/meta.yaml` の構造。`<source>` を `Source` として扱う。
