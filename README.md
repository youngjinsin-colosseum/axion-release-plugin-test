# axion-release-plugin 멀티모듈 샘플

[axion-release-plugin](https://github.com/allegro/axion-release-plugin) 1.21.3 으로
멀티모듈(`a`, `b`, `c`)의 버전을 **모듈별로 독립 관리**하는 Java/Gradle 샘플 프로젝트다.

버전은 `build.gradle.kts` 에 하드코딩하지 않고 **git 태그에서 계산**한다.

- 현재 커밋에 릴리스 태그가 있으면 → 릴리스 버전 (`0.1.0`)
- 태그 이후 커밋이 있으면 → 스냅샷 버전 (`0.1.1-SNAPSHOT`)

## 구조

```
.
├── build.gradle.kts        # 루트 버전(v<version>) + 공통 설정
├── settings.gradle.kts
├── a/                      # 라이브러리        (태그: a-<version>)
├── b/                      # a 에 의존         (태그: b-<version>)
└── c/                      # b 에 의존, 실행부 (태그: c-<version>)
```

| 프로젝트 | 태그 접두사 | 태그 예시 | 비고 |
|---------|------------|----------|------|
| root    | `v`        | `v0.1.0` | 하위 모듈 변경은 루트 버전에 영향 없음 |
| `a`     | `a-`       | `a-0.1.0` | |
| `b`     | `b-`       | `b-0.1.0` | `a/` 변경 시에도 버전 증가 |
| `c`     | `c-`       | `c-0.1.0` | `a/`, `b/` 변경 시에도 버전 증가 |

> 태그 접두사에 버전 구분자(`-`)가 들어가면 태그 파싱이 깨진다.
> `my-service` 같은 이름을 쓰려면 `versionSeparator` 를 `/` 등으로 바꿔야 한다.

## 버전 계산 규칙 (monorepo 모드)

각 모듈은 `scmVersion { monorepo { ... } }` 를 선언해서, **자기 디렉터리의 변경만** 버전 증가로 취급한다.

- 루트: `monorepo { exclude(listOf("a", "b", "c")) }` → 하위 모듈 변경은 루트 버전을 올리지 않는다.
- `b`: `monorepo { include(listOf("a")) }` → 의존 모듈 `a` 가 바뀌면 `b` 도 새 스냅샷이 된다.
- `c`: `monorepo { include(listOf("a", "b")) }`

실제 동작 예시 (`a` 만 수정하고 커밋한 상태):

```
$ ./gradlew versions
axion-release-plugin-test = 0.1.0        # 루트는 그대로
a = 0.1.1-SNAPSHOT                       # 변경된 모듈
b = 0.1.1-SNAPSHOT                       # a 에 의존하므로 같이 올라감
c = 0.1.1-SNAPSHOT
```

`c` 만 수정한 경우:

```
$ ./gradlew versions
axion-release-plugin-test = 0.1.0
a = 0.1.0
b = 0.1.0
c = 0.1.1-SNAPSHOT
```

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
./gradlew :a:release          # 모듈 a 만 릴리스 → a-0.1.0 태그 생성 & push
./gradlew release             # 루트만 릴리스   → v0.1.0
./gradlew release :a:release :b:release :c:release   # 전부 릴리스

./gradlew :a:release -Prelease.localOnly   # push 없이 로컬 태그만 (연습용)
./gradlew :a:release --dry-run             # 실행 계획만 확인
```

`release` = `createRelease`(태그 생성) + `pushRelease`(push) 를 한 번에 원자적으로 수행한다.
직전에 `verifyRelease` 로 커밋되지 않은 변경 / 원격보다 앞선 커밋 / 스냅샷 의존성을 검사한다.

### 버전 범핑 방식 선택

기본값은 patch 증가(`0.1.0` → `0.1.1`)다. 다른 자리를 올리려면:

```bash
./gradlew :a:release -Prelease.versionIncrementer=incrementMinor   # 0.1.0 -> 0.2.0
./gradlew :a:release -Prelease.versionIncrementer=incrementMajor   # 0.1.0 -> 1.0.0
```

사용 가능한 값: `incrementPatch`(기본), `incrementMinor`, `incrementMajor`,
`incrementMinorIfNotOnRelease`, `incrementPrerelease`.

빌드 스크립트에 고정하려면 각 모듈의 `scmVersion { versionIncrementer("incrementMinor") }` 를 수정한다.

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

### 일반적인 흐름

```bash
# 1. 작업 & 커밋
git commit -am "feat(a): 기능 추가"

# 2. 버전 확인 (0.1.1-SNAPSHOT)
./gradlew :a:cV

# 3. 마이너 릴리스
./gradlew :a:release -Prelease.versionIncrementer=incrementMinor

# 4. 태그 확인 (a-0.2.0)
git tag --list 'a-*'
```

## 빌드 & 실행

```bash
./gradlew build          # 전체 빌드 + 테스트
./gradlew :c:run --args="axion"   # 모듈 c 실행 -> HELLO, AXION!
```

- Gradle 9.7.1 (wrapper 포함), Java toolchain 21
- JDK 17 이상이면 실행 가능하며, toolchain 이 21 을 자동으로 받아온다

## GitHub Actions

| 워크플로 | 트리거 | 하는 일 |
|---------|-------|--------|
| `.github/workflows/ci.yml` | `main` push / PR | 현재 버전 출력 + `./gradlew build` |
| `.github/workflows/release.yml` | 수동 실행(`workflow_dispatch`) | 대상 모듈 선택 → 릴리스 태그 생성 & push |
| `.github/workflows/auto-release.yml` | main 으로 PR 머지 | 변경된 모듈만 자동 릴리스 (브랜치명으로 범핑 폭 결정) |

### 브랜치 기반 자동 릴리스

main 으로 PR 이 머지되면 `auto-release.yml` 이 **변경된 모듈만** 자동으로 릴리스한다.

**어떤 모듈을 올릴지** — 별도 diff 계산 없이 axion 의 monorepo 설정이 판단한다.
4개 프로젝트 전부에 `release` 를 걸어도 변경 없는 모듈은 스스로 건너뛴다.

```
Creating tag: v0.2.0
Working on released version 0.1.1, nothing to release   # a
Working on released version 0.1.1, nothing to release   # b
Working on released version 0.1.1, nothing to release   # c
```

**얼마나 올릴지** — PR 의 head 브랜치명으로 결정한다 (루트 `build.gradle.kts` 의 `branchIncrementers`).

| 브랜치 패턴 | 증가 | `0.1.1` 기준 |
|---|---|---|
| `feature/*`, `feat/*` | minor | `0.2.0` |
| `hotfix/*`, `bugfix/*`, `fix/*` | patch | `0.1.2` |
| `breaking/*`, `major/*` | major | `1.0.0` |
| 그 외 | patch (기본값) | `0.1.2` |

> **`-Prelease.overriddenBranchName` 이 핵심이다.**
> 머지가 끝난 시점의 현재 브랜치는 `main` 이라서, 그대로 두면 `branchVersionIncrementer` 가
> 아무 패턴에도 안 걸려 항상 patch 가 된다. 워크플로에서 PR 의 head 브랜치명
> (`github.event.pull_request.head.ref`)을 명시적으로 주입해야 규칙이 적용된다.

규칙을 바꾸려면 루트 `build.gradle.kts` 의 `branchIncrementers` 맵만 수정하면 된다.
이 맵은 `subprojects` 블록에서 4개 모듈 전부에 전파된다.

Release 워크플로 입력값:

- **module**: `all` / `root` / `a` / `b` / `c`
- **incrementer**: `incrementPatch` / `incrementMinor` / `incrementMajor`
- **forceVersion**: 비워두면 자동 계산 (지정 시 선택한 모듈 전부 그 버전으로 릴리스)

CI 관련 주의점:

- `actions/checkout` 은 기본이 shallow clone 이라 태그를 못 본다 → **`fetch-depth: 0` 필수**
- detached HEAD 상태이므로 `-Prelease.disableChecks -Prelease.pushTagsOnly` 를 붙인다
- 플러그인은 CI에서 shallow 여부와 무관하게 `fetch --unshallow` 를 시도한다.
  `fetch-depth: 0` 을 쓰고 있으므로 각 모듈에 `unshallowRepoOnCI.set(false)` 로 꺼둔다.
  (켜둔 채 여러 모듈을 병렬 릴리스하면 `lock error: .git/shallow` 로 실패한다)
- 릴리스는 `--no-parallel` 로 실행한다. 여러 모듈이 같은 `.git` 에 동시에 태그를 만들면 충돌한다
- 태그 push 를 위해 `permissions: contents: write` 와 `GITHUB_TOKEN` 인증이 필요하다
- `release` 태스크는 `released-version` 을 GitHub output 으로 내보낸다.
  모듈마다 버전이 다르면 JSON 형태다: `{"a":"0.2.0","b":"0.1.0"}`

## 참고

- [공식 문서](https://axion-release-plugin.readthedocs.io/en/latest/)
- [설정 전체 목록](https://axion-release-plugin.readthedocs.io/en/latest/configuration/overview/)
