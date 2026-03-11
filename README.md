# blog-play

Play Framework を使って `conf/blog` の記事メタデータからブログサイトを生成・表示するプロジェクトです。

## 初期化

```bash
mise install
sbt compile
```

## Docker (本番想定)

```bash
cp .env.example .env
sbt Docker/publishLocal
docker compose up
```

- Docker イメージは `sbt-native-packager` の `Docker/publishLocal` で生成します（`target/docker/stage/Dockerfile` もここで生成されます）。
- DB は `./data` を `/opt/docker/data` にマウントして永続化します（H2 の `blog.mv.db` などが作成されます）。
- `conf/blog` の内容は image に同梱されます（submodule を checkout 済みであることが前提です）。
- 本番では Host header 対策のため `PLAY_ALLOWED_HOST_0` に許可するホスト名を設定します。
