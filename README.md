# blog-play

Play Framework で blog データを読み込み、閲覧用サイトを生成するプロジェクトです。

## Setup

事前にmiseとsbtのインストールが必要です。miseではJavaとNodeのバージョンを固定します。sbtは別途インストールしてください。

```shell
mise install
sbt compile
```

参考 [Getting Started | mise-en-place](https://mise.jdx.dev/getting-started.html)

参考 [Install | The Scala Programming Language](https://www.scala-lang.org/download/)

## Test

```shell
mise run test
```

## Run

起動方法は4種類あります。

### 1. Play / Dev

開発向けにDevモードで起動します。

```shell
mise run play:run
```

参考 [Development mode | PlayFramework](https://www.playframework.com/documentation/3.0.10/PlayConsole#Development-mode)

### 2. Play / Docker

Docker上でProdモードで起動します。事前にdockerのインストールと`.env`の作成が必要です。

```shell
cp play/.env.example play/.env
mise run play:docker:up
```

参考 [Install | Docker Doc](https://docs.docker.com/engine/install/)

### 3. Play / Native Image

GraalVMでビルドしてJVMに依存せずに起動します。事前にdockerのインストールと`.env`の作成が必要です。

```shell
cp play/.env.example play/.env
mise run play:graalvm:up
```

#### トレースエージェント

クラスの依存関係やリソースファイルの追加削除が変わった場合、[reachability-metadata.json](play/conf/META-INF/native-image/io.github.kijuky/blog-play/reachability-metadata.json)を更新する必要があります。トレースエージェントを使うことで、アプリケーションが実際に必要としたクラスやリソースを自動で追記できます。トレースエージェントは下記で起動します。

```shell
mise run play:graalvm:agent
```

アプリが起動したら、できるだけカバレッジが通るように操作します。アプリを終了したタイミングでreachability-metadata.jsonが更新されます。

参考 [Collect Metadata with the Tracing Agent | GraalVM](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)

### 4. ZIO HTTP

```shell
mise run zio:run
```

- default port: `9001` (`ZIO_HTTP_PORT` で上書き可能)
- routes: `GET /`, `GET /blog/:stableId`
