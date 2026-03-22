# blog-play

Play Framework で blog データを読み込み、閲覧用サイトを生成するプロジェクトです。

## Setup

```shell
mise install
sbt compile
```

## Test

```shell
mise run test
```

## Run (Play / Dev)

```shell
mise run play:run
```

## Run (Play / Docker)

```shell
cp play/.env.example play/.env
mise run play:up
```

## Run (Play / Native Image)

```shell
mise run play:graalvm
```

## Run (ZIO HTTP)

```shell
mise run zio:run
```

- default port: `9001` (`ZIO_HTTP_PORT` で上書き可能)
- routes: `GET /`, `GET /blog/:stableId`
