output "vm_ips" {
  description = "VM 이름별 IP"
  value       = { for name, vm in local.vms : name => vm.ip }
}

output "ssh_commands" {
  description = "맥북에서 각 VM에 접속하는 명령"
  value       = { for name, vm in local.vms : name => "ssh ${var.vm_user}@${vm.ip}" }
}

output "kubeconfig_command" {
  description = "맥북에서 kubectl을 쓰기 위한 kubeconfig 가져오기"
  value       = "ssh ${var.vm_user}@${local.k3s_server_ip} 'sudo cat /etc/rancher/k3s/k3s.yaml' | sed 's/127.0.0.1/${local.k3s_server_ip}/' > ~/.kube/vaccount-lab.yaml && export KUBECONFIG=~/.kube/vaccount-lab.yaml"
}
