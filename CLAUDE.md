# concurrency-conquest

운영하던 **고정 가상계좌 입금·원장·지갑 시스템**을 집 홈랩에 재현하고, 부하와 장애를 주입해서
**결제 도메인의 동시성·정합성 문제**를 검증하는 프로젝트. 결과는 블로그 연재
("금융 시스템 동시성 정복기")와 경력기술서에 쓴다.

## 사용자에 대해
- 한국어로 대화한다. 설명은 쉬운 말로, 개념은 예시와 함께.
- 백엔드 개발자(Kotlin/Spring), 결제·금융 연동 경험 있음. 이직 준비 중이며 새벽 시간에만 작업한다.
- 쿠버네티스와 테라폼은 오래돼서 기억이 흐릿하다. 인프라 작업은 무엇을 왜 하는지 짧게 설명하면서 진행한다.
- **정직성 원칙:** 이 프로젝트의 결과는 "운영하던 구조를 재현해서 검증했다"로 쓴다.
  실제 운영에서 겪지 않은 사고를 겪은 것처럼 쓰지 않는다.

## 홈랩 구성
- 물리 서버: Proxmox VE, Xeon 28스레드, RAM 32GB, 디스크 888GB. 웹 UI `https://172.30.1.200:8006`
  (아직 172.30.1.95일 수 있음. infra/terraform/README.md의 IP 이동 절차 참고)
- 공유기: KT, 게이트웨이 172.30.1.254. DHCP 범위 1~100, 128~148 → **고정 IP는 이 범위 밖만 사용**
- 사용자 PC: 맥북 (Terraform, kubectl, k6 실행)

| VM | ID | IP | vCPU/RAM | 역할 |
|---|---|---|---|---|
| k3s-cp | 210 | 172.30.1.210 | 2 / 3GB | k3s 컨트롤 플레인 |
| k3s-w1 | 211 | 172.30.1.211 | 4 / 6GB | 서비스 서버 (label role=app) |
| k3s-w2 | 212 | 172.30.1.212 | 4 / 6GB | 모니터링 (label role=monitoring) |
| svc-db | 220 | 172.30.1.220 | 4 / 5GB | 서비스 MySQL 8.4 (Docker) |
| hecto | 221 | 172.30.1.221 | 2 / 3GB | 가상계좌 데몬 시뮬레이터(vbank-sim) + 가상계좌 MySQL (Docker) |

- VM은 `infra/terraform`으로 생성 (bpg/proxmox provider, cloud-init으로 k3s/Docker 설치). VM 계정 `dev`, 맥북 SSH 키로 접속.
- **현재 상태:** VM 5대 생성 완료, k3s 노드 3대 Ready, 시스템 파드 정상. 앱 파드는 아직 없음. svc-db는 MySQL 8.4 가동 중(`infra/svc-db`, DB `wallet`, 계정 `app`, 테이블 없음). hecto는 Docker만 설치됨.

## 아키텍처
```
[맥북 k6] → 서비스 서버(k3s) ── 서비스 MySQL(svc-db)
                 └ 입금 폴러 ──(체크포인트 폴링)──→ 가상계좌 MySQL(hecto VM, vbank-sim이 INSERT)
            모니터링: Prometheus + Grafana (k3s, w2)
```
- **명명:** 공개 저장소이므로 코드·테이블에는 벤더 이름 대신 `vbank`를 쓴다 (모듈 `vbank-sim`, 테이블 `vbank_*`). VM 이름 `hecto`는 재생성할 때 바꾼다.
- 가상계좌 DB는 입금 사실만 기록한다 (지갑 개념 없음, 입금취소 없음). 지갑·서비스 원장은 서비스 DB에만 있다.
- 입금 흐름: 폴러가 `deposited_at >= 체크포인트`로 읽음(초 단위, 마지막 초 재조회) → SELECT로 중복 확인 → 서비스 DB 트랜잭션: 원장 INSERT → 지갑 반영 → 커밋 → **그 뒤 가상계좌 DB의 체크포인트 갱신 (별도 DB, 별도 커밋)**
- 스키마와 당시 구조 대비 차이는 `docs/design/schema.md`
- 차감: 맥북 k6가 차감 API 호출 + 하루 4회 차감 배치 API(당시 Jenkins가 호출하던 구조 재현)
- 원장(ledger)과 지갑(wallet) 분리, 지갑에 `last_ledger_id` 보유 (당시 실제 구조)

## 기술 스택
- Kotlin 2.3, Spring Boot 4.1, JDK 21 (Gradle 툴체인이 자동 설치), Gradle 9 Kotlin DSL 멀티모듈 (service, vbank-sim)
- service와 vbank-sim은 코드를 공유하지 않는다. vbank-sim은 외부 시스템 역할이고, service는 가상계좌 DB를 SQL로만 읽는다.
- Spring Data JPA + 원자적 UPDATE는 JPQL/네이티브, 대량 INSERT는 JdbcTemplate
- MySQL 8.4 LTS, Flyway, Spring Batch(API 트리거), Redis + Redisson(분산 락)
- 테스트: JUnit 5, Testcontainers(MySQL, Redis), 동시성 테스트(ExecutorService + CountDownLatch)
- 관측: Actuator + Micrometer(Prometheus), Grafana
- 이미지: Jib → GHCR. 배포: 처음엔 kubectl apply, 이후 GitHub Actions(CI) + Argo CD(GitOps, 클러스터가 pull → 집 서버를 외부에 열지 않음)
- 비밀값: 공개 저장소이므로 Sealed Secrets

## 단계 관리 규칙
- **비교 대상은 코드에 공존시킨다.** 같은 인터페이스의 구현체로 두고 요청 파라미터나 설정으로 선택한다 (예: `DebitStrategy`, `DebitMethod`).
- **스키마는 모든 전략의 합집합.** Flyway 마이그레이션은 추가만 한다. 적용된 V 파일은 수정하지 않는다.
- 서로 충돌하는 구조(예: unique 있음/없음)만 예외로, 그 실험 단계에서 처리 방법을 정한다.
- 실험 결과는 `docs/experiments/NN-*.md`에 문제 → 재현 → 숫자 → 해결 → 숫자 순으로 쓴다.

## 지금 할 일 (학습 우선: 하나씩 제대로 구현하고 이해한다)
1. ~~Git 저장소, svc-db MySQL, 스키마 설계 v1~~ (완료)
2. ~~서비스 뼈대 + 차감 v1(변경 감지) + Testcontainers로 Lost Update 재현~~ (완료, `docs/experiments/01-lost-update.md`)
3. 차감 v2~v4 추가: 원자적 UPDATE / SELECT FOR UPDATE / @Version + 재시도 (V2 마이그레이션으로 version 컬럼)
4. CI: GitHub Actions 빌드·테스트 + Jib 이미지 GHCR push
5. k3s 수동 배포(role=app) → k6 시나리오 1 → 방식별 정합성·TPS·p99 → 블로그 #1
6. CD: Argo CD + Sealed Secrets
7. 운영 화면: 처음엔 Grafana + MySQL 데이터소스로 정합성·누락 확인, 조작 화면이 필요하면 간단한 관리자 페이지

## 이후 시나리오
| 시나리오 | 드러나는 것 |
|---|---|
| 핫 로우: 지갑 1개 vs 1만 개, 방식별 TPS·p99 | 락 경합, 파드를 늘려도 DB가 병목 |
| 납부일 직전 입금 폭주 + 차감 배치 | 지갑 경합, 락 대기 |
| 초 단위 체크포인트 폴링 + 늦게 커밋되는 입금 | 입금 누락 (마지막 1초만 재조회하는 한계) |
| 폴러 크래시 (서비스 커밋 후 체크포인트 갱신 전) | 두 DB 별도 커밋, 재컨슘 멱등성 |
| 배치 API 타임아웃 → 재호출 | 이중 차감 시도, 멱등성 |
| 배치 도중 롤링 배포 | 부분 처리, graceful shutdown |
| 한 트랜잭션 배치 vs 청크 커밋 | 입금 반영 지연 |
| 트랜잭션 안 외부 호출(알림) 지연 | 커넥션 풀 고갈, 장애 전파 |
| checked 예외 롤백 누락 (실제 겪은 버그) | 정합성 검증 배치가 불일치 탐지 |
| 따닥 요청 (분산 락) | 락과 트랜잭션 경계 |
| 폴러 2대 (롤링 배포 겹침) | SELECT 멱등 체크 경쟁 조건, unique 제약 |
| (2단계) Outbox + Kafka | 메시지 유실, 중복, 컨슈머 lag |
| (4단계) 외부 연동 지연: 스레드/가상 스레드/코루틴/WebFlux 비교 | 동시성 모델별 한계 |

## 작업 규칙
- 인프라를 바꾸는 명령(terraform apply/destroy, kubectl delete, VM 내부 데이터 삭제)은 실행 전에 무엇이 바뀌는지 설명하고 사용자 확인을 받는다.
- 토큰, 비밀번호를 대화에 출력하거나 커밋하지 않는다.
- 실험 결과는 숫자와 함께 `docs/experiments/`에 기록한다 (블로그 초안 재료).
