# vaccount-lab 인프라 (Proxmox + Terraform)

Proxmox 서버 한 대에 VM 5대를 코드로 만든다.

| VM | ID | IP | vCPU | 메모리 | 디스크 | 역할 |
|---|---|---|---|---|---|---|
| k3s-cp | 210 | 172.30.1.210 | 2 | 3GB | 30GB | k3s 컨트롤 플레인 |
| k3s-w1 | 211 | 172.30.1.211 | 4 | 6GB | 40GB | 서비스 서버 (role=app) |
| k3s-w2 | 212 | 172.30.1.212 | 4 | 6GB | 40GB | 모니터링 (role=monitoring) |
| svc-db | 220 | 172.30.1.220 | 4 | 5GB | 50GB | 서비스 MySQL (Docker) |
| hecto | 221 | 172.30.1.221 | 2 | 3GB | 30GB | 헥토 시뮬레이터 + 입금 MySQL (Docker) |

공유기 DHCP 범위(1~100, 128~148) 밖의 번호만 쓴다. Proxmox 호스트는 172.30.1.200.

## 0. 한 번만 하는 준비

### Proxmox 호스트 IP를 95 → 200으로 옮기기 (서버 화면 또는 웹 Shell)
95번은 DHCP 범위 안이라 나중에 충돌할 수 있다.
```bash
sed -i 's#172.30.1.95/24#172.30.1.200/24#' /etc/network/interfaces
sed -i 's#^172.30.1.95#172.30.1.200#' /etc/hosts
grep -n "172.30.1" /etc/network/interfaces /etc/hosts   # 200으로 바뀌었는지 확인
reboot
```
재부팅 후 `https://172.30.1.200:8006`으로 접속.

### Proxmox 쪽
1. Datacenter → Storage → local → Edit → Content에 **Snippets** 추가
2. Ubuntu 24.04 템플릿(VM 9000) 생성 (Shell)
   ```bash
   cd /root
   wget https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img
   qm create 9000 --name ubuntu-2404-tpl --memory 2048 --cores 2 \
     --net0 virtio,bridge=vmbr0 --scsihw virtio-scsi-single
   qm set 9000 --scsi0 local-lvm:0,import-from=/root/noble-server-cloudimg-amd64.img
   qm set 9000 --ide2 local-lvm:cloudinit
   qm set 9000 --boot order=scsi0
   qm set 9000 --serial0 socket --vga serial0
   qm set 9000 --agent enabled=1
   qm template 9000
   ```
3. Datacenter → Permissions → API Tokens → Add
   - User `root@pam`, Token ID `terraform`, Privilege Separation **해제**
   - Secret은 한 번만 보인다. 본인만 보관.

### 맥북 쪽
```bash
brew tap hashicorp/tap && brew install hashicorp/tap/terraform
ls ~/.ssh/id_ed25519.pub || ssh-keygen -t ed25519

# 스니펫 업로드용: Proxmox root에 공개키 등록 + ssh-agent에 키 올리기
ssh-copy-id root@172.30.1.200
ssh-add --apple-use-keychain ~/.ssh/id_ed25519
```

## 1. 실행
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # 토큰 입력
terraform init
terraform plan      # VM 5대 + 스니펫 5개 + 토큰 1개 생성 예정인지 확인
terraform apply
```
VM이 뜨고 cloud-init이 패키지와 k3s/Docker를 설치하는 데 5~10분 걸린다.

## 2. 확인
```bash
terraform output ssh_commands
ssh dev@172.30.1.210 'sudo k3s kubectl get nodes -o wide'   # 노드 3대 Ready
ssh dev@172.30.1.220 'docker version'
```
맥북에서 kubectl 쓰기:
```bash
brew install kubectl
eval "$(terraform output -raw kubeconfig_command)"
kubectl get nodes
```

## 3. 전부 지우고 다시 만들기
```bash
terraform destroy
terraform apply
```

## 문제 생기면
- `apply`가 agent 대기에서 오래 멈춤: VM 콘솔에서 `cloud-init status --long`, `/var/log/cloud-init-output.log` 확인
- 스니펫 업로드 실패: `ssh root@172.30.1.200`이 비밀번호 없이 되는지, `ssh-add -l`에 키가 있는지 확인
- 노드가 클러스터에 안 붙음: w1/w2에서 `sudo journalctl -u k3s-agent -f`
