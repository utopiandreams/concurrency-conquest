provider "proxmox" {
  endpoint  = var.pve_endpoint
  api_token = var.pve_api_token

  # Proxmox 기본 인증서는 자체 서명이라 검증을 끈다 (집 내부망 전용)
  insecure = true

  # cloud-init 스니펫 파일 업로드는 API가 아니라 SSH로 한다.
  # 맥북의 ssh-agent에 키가 올라가 있고, Proxmox root에 공개키가 등록돼 있어야 한다.
  ssh {
    agent    = true
    username = "root"
  }
}
