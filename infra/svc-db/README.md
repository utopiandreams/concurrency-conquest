# svc-db: 서비스 MySQL 8.4

svc-db VM(172.30.1.220)에서 Docker Compose로 띄운다. VM은 Terraform이, MySQL은 이 compose가 관리한다.

## 배포
```bash
# 맥북에서: .env 만들기 (비밀번호는 직접 생성, 커밋 금지)
cp .env.example .env && vi .env

# VM으로 복사 후 실행
ssh dev@172.30.1.220 'mkdir -p ~/svc-db'
scp -r docker-compose.yml conf .env dev@172.30.1.220:~/svc-db/
ssh dev@172.30.1.220 'cd ~/svc-db && docker compose up -d && docker compose ps'
```

## 확인
```bash
ssh dev@172.30.1.220 'docker exec svc-mysql mysql -uroot -p"$(grep ROOT ~/svc-db/.env | cut -d= -f2)" -e "SELECT @@version, @@transaction_isolation, @@innodb_buffer_pool_size/1024/1024/1024 AS bp_gb"'
```

## 설정 변경
`conf/my.cnf` 수정 → 다시 scp → `docker compose restart mysql`. 데이터는 `mysql-data` 볼륨에 남는다.

## 주의
- `docker compose down -v`는 볼륨(데이터)까지 지운다.
- `.env`의 계정 정보는 볼륨이 **처음 생성될 때만** 적용된다. 이후 바꾸려면 SQL로 변경하거나 볼륨을 지우고 다시 만든다.
