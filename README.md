# axion-release-plugin 멀티모듈 샘플

[axion-release-plugin](https://github.com/allegro/axion-release-plugin) 1.21.3 으로
멀티모듈(`a`, `b`, `c`)의 버전을 **모듈별로 독립 관리**하는 Java/Gradle 샘플이다.

버전은 `build.gradle.kts` 에 하드코딩하지 않고 **git 태그에서 계산**한다.

- 현재 커밋에 릴리스 태그가 있으면 → 릴리스 버전 (`0.4.1`)
- 태그 이후 변경이 있으면 → 스냅샷 버전 (`0.4.2-SNAPSHOT`)

버전이 저장소 상태의 함수이므로, 같은 커밋이면 로컬에서든 CI 에서든 몇 번을 돌리든
같은 값이 나온다. 이 성질이 뒤에 나오는 [자동 릴리스](#자동-릴리스) 설계의 근거다.

---

## 목차

- [구조](#구조)
- [1. 어떤 모듈을 올릴지 — monorepo 모드](#1-어떤-모듈을-올릴지--monorepo-모드)
- [2. 얼마나 올릴지 — 커밋 메시지 기반 판정](#2-얼마나-올릴지--커밋-메시지-기반-판정)
- [자주 쓰는 명령](#자주-쓰는-명령)
- [자동 릴리스](#자동-릴리스)
- [CI 에서 겪는 함정](#ci-에서-겪는-함정)
- [플러그인 API 메모](#플러그인-api-메모)
- [검증 기록](#검증-기록)

---

## 구조

```
.
├── build.gradle.kts        # 루트 버전(v<version>) + 공통 설정 + 증가 폭 판정 로직
├── settings.gradle.kts
├── a/                      # 라이브러리        (태그: a-<version>)
├── b/                      # a 에 의존         (태그: b-<version>)
└── c/                      # b 에 의존, 실행부 (태그: c-<version>)
```

| 프로젝트 | 태그 접두사 | 태그 예시 | 버전 증가 대상 |
|---|---|---|---|
| root | `v` | `v0.3.0` | `a/`, `b/`, `c/` 를 **제외한** 모든 경로 |
| `a` | `a-` | `a-0.4.1` | `a/` |
| `b` | `b-` | `b-1.0.0` | `b/` |
| `c` | `c-` | `c-1.0.0` | `c/` |

```kotlin
scmVersion {
    tag {
        prefix.set("a")
        versionSeparator.set("-")   // 태그: a-0.4.1
    }
}
```

> **접두사에 `-` 를 넣지 말 것.** 접두사와 버전 구분자가 겹치면 태그 파싱이 깨진다.
> `my-service` 같은 이름이 필요하면 `versionSeparator` 를 `/` 등으로 바꾼다.

빌드는 Gradle 9.7.1(wrapper 포함), Java toolchain 21 이다.

```bash
./gradlew build                   # 전체 빌드 + 테스트
./gradlew :c:run --args="axion"   # -> HELLO, AXION!
```

---

## 1. 어떤 모듈을 올릴지 — monorepo 모드

`scmVersion { monorepo { } }` 를 선언하면, 플러그인이 **직전 태그 이후 변경된 경로**를 보고
자기 버전을 올릴지 판단한다. 별도의 diff 스크립트가 필요 없다.

```kotlin
// a/build.gradle.kts — 자기 디렉터리만
scmVersion {
    monorepo {
    }
}

// build.gradle.kts (루트) — 하위 모듈은 루트 버전에 영향 없음
scmVersion {
    monorepo {
        exclude(listOf("a", "b", "c"))
    }
}
```

네 프로젝트 전부에 `release` 를 걸어도, 변경이 없는 모듈은 스스로 건너뛴다.

```
$ ./gradlew release :a:release :b:release :c:release
Working on released version 0.2.1, nothing to release   # root
Creating tag: a-0.4.0                                   # a  (a/ 만 변경됨)
Working on released version 0.2.2, nothing to release   # b
Working on released version 0.2.2, nothing to release   # c
```

### 의존 모듈을 함께 올릴 것인가

`include` 로 다른 디렉터리를 자기 증가 대상에 포함시킬 수 있다.

```kotlin
// b 는 a 에 의존하므로 a/ 변경도 b 를 올린다 — 이 저장소에서는 쓰지 않는다
monorepo {
    include(listOf("a"))
}
```

**이 저장소는 의도적으로 `include` 를 쓰지 않는다.** 초기에는 `b → a`, `c → a, b` 로
걸어뒀는데, `a/` 만 고친 PR 하나에 `a`, `b`, `c` 태그가 전부 생성됐다. 증가 폭은
모듈별로 구분되지 않으므로 의존성 때문에 딸려 올라간 모듈도 같은 등급을 먹는다.

트레이드오프는 명확하다.

| | `include` 사용 | 미사용 (현재) |
|---|---|---|
| a 변경 시 b, c | 자동으로 함께 릴리스 | 릴리스되지 않음 |
| 버전 번호의 의미 | "의존성이 바뀌었다" 포함 | "내 코드가 바뀌었다" 만 |
| a 의 breaking change | b, c 도 올라가지만 **등급은 a 와 무관** | 수동 대응 필요 |

컴파일 의존(`implementation(project(":a"))`)과는 별개다. 버전 결합만 끊은 것이라
`b` 는 여전히 `a` 를 컴파일 타임에 사용한다.

---

## 2. 얼마나 올릴지 — 커밋 메시지 기반 판정

증가 폭은 **직전 태그 이후 커밋 메시지**(Conventional Commits)에서 읽는다.

| 커밋 메시지 | 증가 | `0.4.1` 기준 |
|---|---|---|
| `feat!:`, `fix!:`, 본문에 `BREAKING CHANGE` | major | `1.0.0` |
| `feat:` | minor | `0.5.0` |
| 그 외 (`fix:`, `chore:`, `docs:` …) | patch | `0.4.2` |

모듈마다 기준 태그(`a-*`, `v*` …)와 대상 경로가 다르므로 **각자 자기 범위의 커밋만** 훑는다.
여러 등급이 섞여 있으면 가장 큰 쪽이 이긴다.

### 왜 브랜치명이 아닌가

초기 설계는 PR 의 head 브랜치명(`feature/*` → minor)으로 등급을 정하고
`-Prelease.overriddenBranchName` 으로 주입했다. 두 가지가 깨졌다.

**(1) 체크아웃 시점 경합.** `pull_request` 트리거에서 `ref: main` 은 "머지된 그 커밋"이
아니라 **"체크아웃하는 순간의 main"** 이다. 잡이 늦게 시작하면 그 사이 머지된 다른 PR 의
커밋까지 자기 브랜치명의 등급으로 태그해버린다.

```
t=0    PR1(hotfix/*) 머지
t=20   PR2(feature/*) 머지
t=40   잡1 시작 → main 은 이미 PR2 포함 → patch 로 둘 다 태그
t=70   잡2 → 변경 없음, 건너뜀 → PR2 의 minor 규칙 소실
```

**(2) 큐 취소.** concurrency 그룹의 대기 슬롯은 하나뿐이다. 새 실행이 들어오면 GitHub 이
**대기 중이던 실행을 취소**한다. `cancel-in-progress: false` 는 실행 중인 잡만 보호하며,
대기 잡 취소는 끌 수 있는 옵션이 없다. 취소된 잡의 등급은 그대로 증발하고, 그 코드는
옆 PR 의 등급으로 배포된다.

**major 가 patch 로 둔갑해도 버전 숫자만 봐서는 알 수 없다**는 게 특히 나쁘다.

근본 원인은 등급이 **브랜치명이라는 일회성 정보**에 붙어 있었다는 것이다. 코드는 main 에
영원히 남지만, 브랜치명은 워크플로 실행에만 실려 전달되고 그 실행이 늦거나 취소되면 사라진다.

커밋 메시지는 main 에 영구히 남는다. 직전 태그 이후 커밋을 전부 훑어 판정하면
**잡이 언제 몇 번 돌든, 취소되든 같은 답이 나온다.**

### 구현

루트 `build.gradle.kts` 의 `conventionalIncrementer` 가 판정한다.

```kotlin
fun conventionalIncrementer(config: VersionConfig, pathspec: () -> List<String>) =
    VersionProperties.Incrementer { context ->
        val tagPattern = config.tag.prefix.get() + config.tag.versionSeparator.get() + "*"
        val messages = commitMessagesSince(latestTag(tagPattern), pathspec())
        when {
            breakingChangePattern.containsMatchIn(messages) -> context.currentVersion.incrementMajorVersion()
            featurePattern.containsMatchIn(messages) -> context.currentVersion.incrementMinorVersion()
            else -> context.currentVersion.incrementPatchVersion()
        }
    }
```

설계상 알아둘 점 세 가지.

- **`git log` 를 직접 호출한다.** `VersionIncrementerContext` 는 `currentVersion` 과
  `scmPosition` 만 노출하고 커밋 메시지를 주지 않는다.
- **CLI 프로퍼티가 아니라 빌드 스크립트에 심는다.** Gradle 프로젝트 프로퍼티는 호출 단위
  전역이라 `-Prelease.versionIncrementer` 로는 모듈별로 다른 등급을 한 번에 넘길 수 없다.
  빌드 스크립트에 두면 각 모듈이 자기 `tag.prefix` 와 `monorepoConfig` 를 읽어 판정하므로
  gradle 호출 한 번으로 끝나고, 로컬에서 `./gradlew versions` 를 돌려도 CI 와 같은 값이 나온다.
- **pathspec 은 lazy 다.** 모듈의 `scmVersion` 블록은 `plugins {}` 이후에 평가되므로,
  설정이 끝난 뒤(버전 계산 시점)에 읽어야 실제 값을 본다.

모듈 쪽에는 `versionIncrementer` 를 두지 않는다. 모듈의 `scmVersion` 블록이 루트에서 심어준
incrementer 를 덮어쓰기 때문이다.

수동으로 등급을 덮어써야 하면 **`-Prelease.versionIncrementer` 가 여전히 우선한다.**

---

## 자주 쓰는 명령

### 현재 버전 확인

```bash
./gradlew versions            # 루트 + a, b, c 를 한 번에 (이 저장소에서 추가한 태스크)

./gradlew currentVersion      # 루트만  (축약: ./gradlew cV)
./gradlew :a:currentVersion   # 모듈 a
./gradlew :b:cV -q -Prelease.quiet   # 버전 문자열만 출력
```

### 릴리스 (태그 생성 + push)

```bash
./gradlew :a:release          # 모듈 a 만 → a-0.4.2 태그 생성 & push
./gradlew release             # 루트만   → v0.3.1
./gradlew --no-parallel release :a:release :b:release :c:release   # 전부

./gradlew :a:release -Prelease.localOnly   # push 없이 로컬 태그만 (연습용)
./gradlew :a:release --dry-run             # 실행 계획만 확인
```

`release` = `createRelease`(태그 생성) + `pushRelease`(push) 를 원자적으로 수행한다.
직전에 `verifyRelease` 로 커밋되지 않은 변경 / 원격보다 앞선 커밋 / 스냅샷 의존성을 검사한다.

### 등급 수동 지정

```bash
./gradlew :a:release -Prelease.versionIncrementer=incrementMinor   # 0.4.1 -> 0.5.0
./gradlew :a:release -Prelease.versionIncrementer=incrementMajor   # 0.4.1 -> 1.0.0
```

사용 가능한 값: `incrementPatch`, `incrementMinor`, `incrementMajor`,
`incrementMinorIfNotOnRelease`, `incrementPrerelease`.

### 버전 강제 지정

```bash
./gradlew :a:release -Prelease.forceVersion=2.0.0   # a-2.0.0 태그 생성
./gradlew :a:cV -Prelease.forceVersion=2.0.0        # 확인만 (2.0.0-SNAPSHOT)
```

### 다음 버전 미리 선언 (next version marker)

당장 릴리스하지 않고 "다음은 2.0.0" 이라고만 표시해서, 그때까지 `2.0.0-SNAPSHOT` 을 쓰게 한다.

```bash
./gradlew :a:markNextVersion -Prelease.version=2.0.0   # a-2.0.0-alpha 태그
./gradlew :a:cV                                        # 2.0.0-SNAPSHOT
./gradlew :a:release                                   # a-2.0.0
```

---

## 자동 릴리스

| 워크플로 | 트리거 | 하는 일 |
|---|---|---|
| `ci.yml` | `main` push / PR | 현재 버전 출력 + `./gradlew build` |
| `auto-release.yml` | **`push: branches: [main]`** | 변경된 모듈만 자동 릴리스 |
| `release.yml` | 수동(`workflow_dispatch`) | 대상 모듈·등급·강제 버전을 골라 릴리스 |

`auto-release.yml` 이 `pull_request: closed` 가 아니라 `push` 를 쓰는 이유는
[위](#왜-브랜치명이-아닌가)에서 설명한 대로다. push 이벤트는 체크아웃 대상이 **그 푸시의
커밋으로 고정**되므로 `ref: main` 처럼 "지금 최신"을 다시 조회하는 문제가 없다.

concurrency 그룹은 그대로 둔다. 다만 역할이 달라졌다 — 버전 계산이 저장소 상태의 함수가
됐으므로 잡이 취소돼도 안전하고, 직렬화는 **태그 push 충돌 방지** 용도로만 남는다.

```yaml
on:
  push:
    branches: [ main ]

concurrency:
  group: auto-release
  cancel-in-progress: false

jobs:
  release:
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0        # ref 를 지정하지 않는다 = 이 push 의 커밋
```

`release.yml` 입력값:

- **module**: `all` / `root` / `a` / `b` / `c`
- **incrementer**: `incrementPatch` / `incrementMinor` / `incrementMajor`
- **forceVersion**: 비워두면 자동 계산

---

## CI 에서 겪는 함정

- `actions/checkout` 은 기본이 shallow clone 이라 태그를 못 본다 → **`fetch-depth: 0` 필수**
- detached HEAD 상태이므로 `-Prelease.disableChecks -Prelease.pushTagsOnly` 를 붙인다
- 플러그인은 CI 에서 shallow 여부와 무관하게 `fetch --unshallow` 를 시도한다.
  `fetch-depth: 0` 을 쓰고 있으므로 각 모듈에 `unshallowRepoOnCI.set(false)` 로 꺼둔다.
  (켜둔 채 여러 모듈을 병렬 릴리스하면 `lock error: .git/shallow` 로 실패한다)
- 릴리스는 `--no-parallel` 로 실행한다. 여러 모듈이 같은 `.git` 에 동시에 태그를 만들면 충돌한다
- 태그 push 를 위해 `permissions: contents: write` 와 `GITHUB_TOKEN` 인증이 필요하다
- `release` 태스크는 `released-version` 을 GitHub output 으로 내보낸다.
  모듈마다 버전이 다르면 JSON 형태다: `{"a":"0.4.2","b":"1.0.0"}`
- 태그 push 는 `push: branches:` 워크플로를 다시 트리거하지 않는다 (무한 루프 없음)

---

## 플러그인 API 메모

1.21.3 jar 를 직접 뜯어 확인한 사실들. 커스터마이징할 때 필요하다.

```
VersionProperties.Incrementer          // SAM: Version apply(VersionIncrementerContext)
VersionIncrementerContext              // currentVersion, scmPosition — 커밋 메시지는 없음
VersionConfig.getTag()                 // prefix, versionSeparator, branchPrefix, fallbackPrefixes
VersionConfig.getMonorepoConfig()      // projectDirs, dependenciesDirs, excludeDirs
```

- `monorepo { include(...) }` 는 `dependenciesDirs` 에, `exclude(...)` 는 `excludeDirs` 에 쌓인다.
  빌드 스크립트에서 읽어 재사용할 수 있다.
- 버전 계산은 [java-semver](https://github.com/zafarkhaja/jsemver) 0.10.2 의
  `incrementMajorVersion()` / `incrementMinorVersion()` / `incrementPatchVersion()` 을 쓴다.

CLI 로 넘길 수 있는 `release.*` 프로퍼티 (일부):

```
forceVersion  versionIncrementer  versionCreator  nextVersion  version
localOnly     pushTagsOnly        dryRun          quiet        fetchTags
disableChecks disableUncommittedCheck  disableRemoteCheck  disableSnapshotsCheck
customUsername  customPassword  customKey  customKeyFile  customKeyPassword
overriddenBranchName  overriddenIsClean  useHighestVersion  attachRemote
releaseBranchNames  releaseOnlyOnReleaseBranches  forceSnapshot  ignoreUncommittedChanges
```

---

## 검증 기록

커밋 메시지 기반 판정이 경합 상황에서 실제로 버티는지 확인했다.

**방법** — PR 10 개를 만들어 연속 머지했다. 브랜치명은 등급과 **일부러 어긋나게** 지었다
(`hotfix/*` 브랜치에 `feat:` 커밋, `major/*` 브랜치에 `fix:` 커밋 등).

**경합 발생** — 워크플로 10 개가 트리거됐고 **8 개가 큐에서 취소**됐다.
살아남은 2 개는 첫 번째 머지와 마지막 머지의 잡이다.

**결과**

| 모듈 | 기준선 | 관련 커밋 | 최대 등급 | 최종 |
|---|---|---|---|---|
| root | 0.2.1 | `docs:`, `feat(ci):` | minor | **0.3.0** ✅ |
| a | 0.3.1 | `feat:`, `fix:`, `chore:` | minor | **0.4.0 → 0.4.1** ✅ |
| b | 0.2.2 | `fix:`, `feat:`, `feat!:` | major | **1.0.0** ✅ |
| c | 0.2.2 | `feat!:`, `fix:` | major | **1.0.0** ✅ |

잡 하나가 PR 9 개를 흡수했는데도 `b`, `c` 의 **major 가 보존됐다.** 취소된 잡이 들고 있던
`feat!:` 커밋을 로그에서 찾아냈다는 뜻이다. 변경 없는 모듈은 한 번도 태그가 생기지 않았고,
브랜치명은 한 건도 등급에 영향을 주지 않았다.

같은 상황을 예전 설계로 돌렸다면, 살아남은 잡의 브랜치명(`patch/ci-doc` → 패턴 미매치 →
기본 patch)이 적용되어 **major 두 건이 `0.2.3` patch 로 배포됐을 것이다.**

패치 자리(`0.4.0` vs `0.4.1`)는 릴리스 잡이 몇 번 살아남았는지에 따라 달라진다.
보장되는 것은 **등급이 절대 낮아지지 않는다**는 점이다.

---

## 참고

- [공식 문서](https://axion-release-plugin.readthedocs.io/en/latest/)
- [설정 전체 목록](https://axion-release-plugin.readthedocs.io/en/latest/configuration/overview/)
- [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/)
