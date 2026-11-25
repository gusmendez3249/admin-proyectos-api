# Aprovisionamiento en Azure: grupo de recursos, red, firewall (NSG), IP publica y VM Ubuntu.
# La configuracion de la VM (Docker, despliegue de la app) la hace Ansible: ver ../ansible

# IP publica de quien ejecuta Terraform, para limitar SSH y puertos de administracion
data "http" "mi_ip" {
  count = var.ip_admin == "" ? 1 : 0
  url   = "https://api.ipify.org"
}

locals {
  ip_admin = var.ip_admin != "" ? var.ip_admin : "${chomp(data.http.mi_ip[0].response_body)}/32"
}

resource "azurerm_resource_group" "rg" {
  name     = "rg-${var.prefijo}"
  location = var.ubicacion
  tags     = var.etiquetas
}

resource "azurerm_virtual_network" "vnet" {
  name                = "vnet-${var.prefijo}"
  address_space       = ["10.10.0.0/16"]
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas
}

resource "azurerm_subnet" "app" {
  name                 = "snet-app"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = ["10.10.1.0/24"]
}

resource "azurerm_network_security_group" "nsg" {
  name                = "nsg-${var.prefijo}"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas

  # Solo desde la IP del administrador: SSH (22, y 443 como alternativa para redes que
  # bloquean el 22, ver bitacora #16), API (8081, pruebas de carga) y Prometheus (9090)
  security_rule {
    name                       = "admin-ssh-api"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["22", "443", "8081", "9090"]
    source_address_prefix      = local.ip_admin
    destination_address_prefix = "*"
  }

  # Front y Grafana abiertos a Internet
  security_rule {
    name                       = "web-publico"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = [for p in var.puertos_publicos : tostring(p)]
    source_address_prefix      = "Internet"
    destination_address_prefix = "*"
  }
  # Todo lo demas queda bloqueado por las reglas por defecto de Azure (DenyAllInBound)
}

resource "azurerm_subnet_network_security_group_association" "app" {
  subnet_id                 = azurerm_subnet.app.id
  network_security_group_id = azurerm_network_security_group.nsg.id
}

resource "azurerm_public_ip" "pip" {
  name                = "pip-${var.prefijo}"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  allocation_method   = "Static"
  sku                 = "Standard"
  domain_name_label   = "${var.prefijo}-${substr(md5(var.subscription_id), 0, 6)}"
  tags                = var.etiquetas
}

resource "azurerm_network_interface" "nic" {
  name                = "nic-${var.prefijo}"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.etiquetas

  ip_configuration {
    name                          = "principal"
    subnet_id                     = azurerm_subnet.app.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.pip.id
  }
}

resource "azurerm_linux_virtual_machine" "vm" {
  name                            = "vm-${var.prefijo}"
  location                        = azurerm_resource_group.rg.location
  resource_group_name             = azurerm_resource_group.rg.name
  size                            = var.tamano_vm
  admin_username                  = var.usuario_admin
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.nic.id]
  tags                            = var.etiquetas

  admin_ssh_key {
    username   = var.usuario_admin
    public_key = file(pathexpand(var.ruta_llave_publica))
  }

  os_disk {
    name                 = "osdisk-${var.prefijo}"
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 30
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
}

# Inventario de Ansible generado a partir de las IP reales de las VMs (aplicacion y CI)
resource "local_file" "inventario_ansible" {
  filename        = "${path.module}/../ansible/inventario.ini"
  file_permission = "0644"
  content         = <<-EOT
    [app]
    ${azurerm_public_ip.pip.ip_address} ansible_user=${var.usuario_admin}

    [ci]
    ${azurerm_public_ip.ci.ip_address} ansible_user=${var.usuario_admin} dns_publico=${azurerm_public_ip.ci.fqdn}
  EOT
}
