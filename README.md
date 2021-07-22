[![release](https://img.shields.io/github/v/release/ledgerhq/cria?color=0366d6&include_prereleases)](https://github.com/LedgerHQ/cria/releases)
[![build](https://github.com/LedgerHQ/cria/workflows/build/badge.svg?branch=master)](https://github.com/LedgerHQ/cria/actions?query=workflow%3Abuild+branch%3Amaster)
[![publish](https://github.com/LedgerHQ/cria/workflows/publish/badge.svg?branch=master)](https://github.com/LedgerHQ/cria/actions?query=workflow%3Apublish+branch%3Amaster)

# Cria

![](./lama.jpg)

Synchronization of account states (transactions, balance, ...) across bitcoin-like protocols.

Main features of Cria are:
- Scalable and stateless components
- λ architecture

## Getting started

### Run cria dependencies through docker

`docker-compose up`

Please have a look on `docker-compose.yml` file for more details on the configuration.

### Run from sbt

```
sbt run cria --keychainId <uuid> --coin <string> --syncId <uuid> [--blockHash <string>] --accountUid <string> --walletUid <string>
```

### Run from docker

```
sbt cria/docker:publishLocal
docker run docker.pkg.github.com/ledgerhq/cria/cria --keychainId <uuid> --coin <string> --syncId <uuid> [--blockHash <string>] --accountUid <string> --walletUid <string>
```

### Command line options
From the help message:
```
Usage: cria --keychainId <uuid> --coin <string> --syncId <uuid> [--blockHash <string>] --accountUid <string> --walletUid <string>

The cria synchronization worker for BTC-like coins

Options and flags:
    --help
        Display this help text.
    --keychainId <uuid>
        The keychain id
    --coin <string>
        The coin to synchronize
    --syncId <uuid>
        The synchronization id
    --blockHash <string>
        The current hash of the blockchain
    --accountUid <string>
        The id of the account the xpub belongs to
    --walletUid <string>
        The id of the wallet the xpub belongs to
```

[docker]: https://docs.docker.com/get-docker/
