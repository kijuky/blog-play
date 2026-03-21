# blog-play

Play Framework を使って `blog` の記事メタデータからブログサイトを生成・表示するプロジェクトです。

## 初期化

```bash
mise install
sbt compile
```

## 起動（開発）

```bash
mise run play:run
```

## Play + GraalVM

```bash
# ネイティブバイナリ生成
sbt "play / GraalVMNativeImage / packageBin"

# コンテナ起動（native-image 版）
mise run play:graalvm
```

## ZIO HTTP サブプロジェクト

```bash
mise run zio-http:run
```

- ポートは `ZIO_HTTP_PORT`（デフォルト `9001`）。
- Play と同じ DB 初期化/インポート処理を起動時に実行します。
- 一覧は `GET /`、詳細は `GET /blog/:stableId`。

## Docker (本番想定)

```bash
cp play/.env.example play/.env
mise run play:up
```

- Docker イメージは `sbt-native-packager` の `play/Docker/publishLocal` で生成します（`play/target/docker/stage/Dockerfile` もここで生成されます）。
- DB は `./play/data` を `/opt/docker/data` にマウントして永続化します（H2 の `blog.mv.db` などが作成されます）。
- `blog` の内容は image に同梱されます（submodule を checkout 済みであることが前提です）。
- 本番では Host header 対策のため `HOST` に許可するホスト名を設定します。
