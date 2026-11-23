# Infraestructura como Código (Azure + Terraform + Ansible)

| Herramienta | Alcance | Por qué |
|---|---|---|
| **Terraform** (`terraform/`) | *Aprovisionamiento*: grupo de recursos, VNet, subred, NSG (firewall), IP pública estática con DNS y VM Ubuntu 24.04 (`Standard_B2als_v2`, 2 vCPU / 4 GB) | Declarativo, con estado y plan previo (`terraform plan`): crea, modifica o destruye recursos de forma reproducible |
| **Ansible** (`ansible/`) | *Configuración y despliegue*: paquetes base, swap, Docker Engine + Compose, clonado del repo, `.env` con permisos 600, `docker compose up` y verificación de salud | Idempotente y sin agente (solo SSH); describe el estado del servidor, no solo comandos sueltos |
| **Docker Compose** (`../deploy/`) | Ejecución de PostgreSQL, API y front | El mismo stack se prueba en local y se despliega en la nube |

Terraform genera `ansible/inventario.ini` con la IP real de la VM, así que las dos herramientas quedan encadenadas.

## Seguridad
- Acceso a la VM **solo con llave SSH** (contraseñas deshabilitadas); la llave privada nunca sale del equipo local.
- NSG: puertos 22, 8081 y 9090 solo desde la IP del administrador (se detecta sola); 8082 (front) y 3000 (Grafana) públicos; todo lo demás bloqueado. PostgreSQL no publica puerto.
- Sin secretos en el repo: `terraform.tfvars`, `*.tfstate`, `.env` e `inventario.ini` están en `.gitignore`. La contraseña de la BD se pide al ejecutar Ansible y se guarda en la VM con permisos `0600`.
- Terraform se autentica con la sesión de Azure CLI (`az login`), no con llaves en archivos.

## Reproducir desde cero (Windows / PowerShell)
Requisitos: Azure CLI, Terraform ≥ 1.6, Docker Desktop.

```powershell
# 1. Llave SSH (una sola vez)
ssh-keygen -t ed25519 -N '""' -f $env:USERPROFILE\.ssh\admin_proyectos_azure

# 2. Sesión de Azure
az login
az account show --query id -o tsv     # copiar el ID de la suscripción

# 3. Aprovisionar
cd infra\terraform
copy terraform.tfvars.example terraform.tfvars   # pegar subscription_id
terraform init
terraform plan -out plan.tfplan
terraform apply plan.tfplan

# 4. Configurar y desplegar (Ansible en contenedor)
cd ..\..
powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1

# 5. Verificar
terraform -chdir=infra\terraform output url_front
```

## Control de costos
```powershell
az vm deallocate -g rg-gusmendez-devops -n vm-gusmendez-devops   # apagar (deja de cobrar cómputo)
az vm start      -g rg-gusmendez-devops -n vm-gusmendez-devops   # encender
terraform -chdir=infra\terraform destroy                      # eliminar todo al terminar el curso
```
