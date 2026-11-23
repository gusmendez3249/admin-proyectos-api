output "ip_publica" {
  description = "IP publica de la VM"
  value       = azurerm_public_ip.pip.ip_address
}

output "dns" {
  description = "Nombre DNS de la VM"
  value       = azurerm_public_ip.pip.fqdn
}

output "url_front" {
  value = "http://${azurerm_public_ip.pip.fqdn}:8082"
}

output "url_grafana" {
  value = "http://${azurerm_public_ip.pip.fqdn}:3000"
}

output "ssh" {
  value = "ssh -i ~/.ssh/admin_proyectos_azure ${var.usuario_admin}@${azurerm_public_ip.pip.ip_address}"
}
