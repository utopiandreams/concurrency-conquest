variable "pve_endpoint" {
  description = "Proxmox API 주소 (예: https://172.30.1.200:8006/)"
  type        = string
}

variable "pve_api_token" {
  description = "Proxmox API 토큰. 형식: root@pam!terraform=<secret>"
  type        = string
  sensitive   = true
}

variable "pve_node" {
  description = "Proxmox 노드 이름"
  type        = string
  default     = "proxmox"
}

variable "template_vm_id" {
  description = "복제할 Ubuntu 클라우드 이미지 템플릿 VM ID"
  type        = number
  default     = 9000
}

variable "vm_datastore" {
  description = "VM 디스크와 cloud-init 드라이브를 둘 스토리지"
  type        = string
  default     = "local-lvm"
}

variable "snippet_datastore" {
  description = "cloud-init 스니펫을 둘 스토리지 (Snippets 콘텐츠 허용 필요)"
  type        = string
  default     = "local"
}

variable "bridge" {
  description = "VM이 붙을 네트워크 브리지"
  type        = string
  default     = "vmbr0"
}

variable "gateway" {
  description = "공유기 주소"
  type        = string
  default     = "172.30.1.254"
}

variable "dns_servers" {
  type    = list(string)
  default = ["172.30.1.254", "8.8.8.8"]
}

variable "vm_user" {
  description = "VM에 만들 로그인 계정"
  type        = string
  default     = "dev"
}

variable "ssh_public_key_path" {
  description = "VM에 넣을 맥북 SSH 공개키 경로"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}
