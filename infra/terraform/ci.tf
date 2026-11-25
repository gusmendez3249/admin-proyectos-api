# Servidor de integracion continua (Jenkins + SonarQube) en Azure.
# Va en otra region (Chile Central) porque la cuota de vCPU de North Central US es compartida
# con otro proyecto de la suscripcion y esta VM la agotaria (bitacora #20). El CI no necesita
# red privada con la aplicacion: solo descarga el codigo de GitHub.

variable "ubicacion_ci" {
  description = "Region del servidor de CI (permitida por la politica de la suscripcion y con tamano x86 de 8 GB)"
  type        = string
  default     = "chilecentral"
}

variable "tamano_vm_ci" {
  description = "Jenkins (builds Maven) + SonarQube (Elasticsearch embebido) requieren ~8 GB de RAM"
  type        = string
  default     = "Standard_B2as_v2"
}

resource "azurerm_virtual_network" "ci" {
  name                = "vnet-${var.prefijo}-ci"
  address_space       = ["10.20.0.0/16"]
  location            = var.ubicacion_ci
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas
}

resource "azurerm_subnet" "ci" {
  name                 = "snet-ci"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.ci.name
  address_prefixes     = ["10.20.1.0/24"]
}

resource "azurerm_network_security_group" "ci" {
  name                = "nsg-${var.prefijo}-ci"
  location            = var.ubicacion_ci
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas

  # Jenkins (8080), SonarQube (9000) y SSH (22, o 443 en redes que bloquean el 22):
  # solo desde la IP del administrador. Nada del CI queda abierto a Internet.
  security_rule {
    name                       = "admin-ci"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["22", "443", "8080", "9000"]
    source_address_prefix      = local.ip_admin
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "ci" {
  subnet_id                 = azurerm_subnet.ci.id
  network_security_group_id = azurerm_network_security_group.ci.id
}

resource "azurerm_public_ip" "ci" {
  name                = "pip-${var.prefijo}-ci"
  location            = var.ubicacion_ci
  resource_group_name = azurerm_resource_group.rg.name
  allocation_method   = "Static"
  sku                 = "Standard"
  domain_name_label   = "${var.prefijo}-ci-${substr(md5(var.subscription_id), 0, 6)}"
  tags                = var.etiquetas
}

resource "azurerm_network_interface" "ci" {
  name                = "nic-${var.prefijo}-ci"
  location            = var.ubicacion_ci
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas

  ip_configuration {
    name                          = "principal"
    subnet_id                     = azurerm_subnet.ci.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.ci.id
  }
}

resource "azurerm_linux_virtual_machine" "ci" {
  name                            = "vm-${var.prefijo}-ci"
  location                        = var.ubicacion_ci
  resource_group_name             = azurerm_resource_group.rg.name
  size                            = var.tamano_vm_ci
  admin_username                  = var.usuario_admin
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.ci.id]
  tags                            = merge(var.etiquetas, { rol = "ci" })

  admin_ssh_key {
    username   = var.usuario_admin
    public_key = file(pathexpand(var.ruta_llave_publica))
  }

  # cloud-init: SSH tambien en 443 desde el primer arranque, para poder ejecutar Ansible
  # aun desde redes que bloquean el puerto 22 (bitacora #16). Ansible mantiene el mismo ajuste.
  custom_data = base64encode(<<-EOT
    #cloud-config
    write_files:
      - path: /etc/systemd/system/ssh.socket.d/puerto-443.conf
        permissions: "0644"
        content: |
          [Socket]
          ListenStream=0.0.0.0:443
    runcmd:
      - systemctl daemon-reload
      - systemctl restart ssh.socket
  EOT
  )

  os_disk {
    name                 = "osdisk-${var.prefijo}-ci"
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 64
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
}

output "ip_ci" {
  description = "IP publica del servidor de CI"
  value       = azurerm_public_ip.ci.ip_address
}

output "url_jenkins" {
  value = "http://${azurerm_public_ip.ci.fqdn}:8080"
}

output "url_sonarqube" {
  value = "http://${azurerm_public_ip.ci.fqdn}:9000"
}
