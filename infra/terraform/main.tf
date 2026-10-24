locals {
  # DHCP 범위(1~100, 128~148) 밖의 고정 IP만 사용한다.
  # role: k3s-server | k3s-agent | docker
  vms = {
    "k3s-cp" = {
      vm_id = 210, ip = "172.30.1.210", cores = 2, memory_mb = 3072, disk_gb = 30
      role  = "k3s-server", node_label = "role=control-plane"
    }
    "k3s-w1" = {
      vm_id = 211, ip = "172.30.1.211", cores = 4, memory_mb = 6144, disk_gb = 40
      role  = "k3s-agent", node_label = "role=app"
    }
    "k3s-w2" = {
      vm_id = 212, ip = "172.30.1.212", cores = 4, memory_mb = 6144, disk_gb = 40
      role  = "k3s-agent", node_label = "role=monitoring"
    }
    "svc-db" = {
      vm_id = 220, ip = "172.30.1.220", cores = 4, memory_mb = 5120, disk_gb = 50
      role  = "docker", node_label = ""
    }
    "hecto" = {
      vm_id = 221, ip = "172.30.1.221", cores = 2, memory_mb = 3072, disk_gb = 30
      role  = "docker", node_label = ""
    }
  }

  k3s_server_ip  = local.vms["k3s-cp"].ip
  ssh_public_key = trimspace(file(pathexpand(var.ssh_public_key_path)))

  templates = {
    "k3s-server" = "${path.module}/templates/k3s-server.yaml.tftpl"
    "k3s-agent"  = "${path.module}/templates/k3s-agent.yaml.tftpl"
    "docker"     = "${path.module}/templates/docker-host.yaml.tftpl"
  }
}

# k3s 노드끼리 클러스터에 합류할 때 쓰는 비밀 토큰. 상태 파일에만 저장된다.
resource "random_password" "k3s_token" {
  length  = 48
  special = false
}

# VM별 cloud-init user-data 스니펫 (첫 부팅 때 계정, 패키지, k3s/Docker 설치)
resource "proxmox_virtual_environment_file" "cloud_init" {
  for_each = local.vms

  content_type = "snippets"
  datastore_id = var.snippet_datastore
  node_name    = var.pve_node

  source_raw {
    file_name = "${each.key}-user-data.yaml"
    data = templatefile(local.templates[each.value.role], {
      hostname      = each.key
      user          = var.vm_user
      ssh_key       = local.ssh_public_key
      ip            = each.value.ip
      node_label    = each.value.node_label
      k3s_token     = random_password.k3s_token.result
      k3s_server_ip = local.k3s_server_ip
    })
  }
}

resource "proxmox_virtual_environment_vm" "vm" {
  for_each = local.vms

  name      = each.key
  node_name = var.pve_node
  vm_id     = each.value.vm_id
  tags      = ["vaccount-lab", each.value.role]

  on_boot = false # 실험할 때만 켠다 (전기료)
  started = true

  clone {
    vm_id = var.template_vm_id
    full  = true
  }

  # 템플릿에 guest agent가 켜져 있고, cloud-init이 qemu-guest-agent를 설치한다
  agent {
    enabled = true
    timeout = "15m"
  }

  cpu {
    cores = each.value.cores
    type  = "host"
  }

  memory {
    dedicated = each.value.memory_mb
  }

  disk {
    datastore_id = var.vm_datastore
    interface    = "scsi0"
    size         = each.value.disk_gb
    discard      = "on"
    iothread     = true
  }

  network_device {
    bridge = var.bridge
  }

  operating_system {
    type = "l26"
  }

  initialization {
    datastore_id = var.vm_datastore

    ip_config {
      ipv4 {
        address = "${each.value.ip}/24"
        gateway = var.gateway
      }
    }

    dns {
      servers = var.dns_servers
    }

    # user_data_file_id를 쓰면 user_account 블록은 무시되므로 계정은 스니펫에서 만든다
    user_data_file_id = proxmox_virtual_environment_file.cloud_init[each.key].id
  }
}
