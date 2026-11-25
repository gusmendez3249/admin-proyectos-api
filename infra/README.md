# Infraestructura como Código (Azure + Terraform + Ansible)

| Herramienta | Alcance | Por qué |
|---|---|---|
| **Terraform** (`terraform/`) | *Aprovisionamiento*: grupo de recursos, VNet, subred, NSG (firewall), IP pública estática con DNS y VM Ubuntu 24.04 (`Standard_B2als_v2`, 2 vCPU / 4 GB) | Declarativo, con estado y plan previo (`terraform plan`): crea, modifica o destruye recursos de forma reproducible |
| **Ansible** (`ansible/`) | *Configuración y despliegue*: paquetes base, swap, Docker Engine + Compose, configuración del demonio (`daemon.json` con handler de reinicio), clonado del repo, `.env` con permisos 600, `docker compose up` (app + monitoreo) y verificación de salud de front, Grafana, Prometheus y sus objetivos | Idempotente y sin agente (solo SSH); describe el estado del servidor, no solo comandos sueltos |
| **Docker Compose** (`../deploy/`) | Ejecución de PostgreSQL, API y front | El mismo stack se prueba en local y se despliega en la nube |

Terraform genera `ansible/inventario.ini` con la IP real de la VM, así que las dos herramientas quedan encadenadas.

## Seguridad
- Acceso a la VM **solo con llave SSH** (contraseñas deshabilitadas); la llave privada nunca sale del equipo local.
- NSG: puertos 22 y 443 (SSH alternativo), 8081 y 9090 solo desde la IP del administrador (se detecta sola); 8082 (front) y 3000 (Grafana) públicos; todo lo demás bloqueado. PostgreSQL no publica puerto.
- Sin secretos en el repo: `terraform.tfvars`, `*.tfstate`, `.env` e `inventario.ini` están en `.gitignore`. Las contraseñas de PostgreSQL y Grafana las genera Ansible (aleatorias, 24 caracteres) en la primera instalación, se conservan en los despliegues siguientes y se guardan solo en la VM con permisos `0600`. Para verlas: `ssh ... "sudo cat /opt/admin-proyectos/deploy/.env"`.
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
powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1            # o con -PuertoSsh 443 si la red bloquea el 22

# 5. Verificar
terraform -chdir=infra\terraform output url_front
```

## Control de costos
```powershell
az vm deallocate -g rg-gusmendez-devops -n vm-gusmendez-devops   # apagar (deja de cobrar cómputo)
az vm start      -g rg-gusmendez-devops -n vm-gusmendez-devops   # encender
terraform -chdir=infra\terraform destroy                      # eliminar todo al terminar el curso
```

## Servidor de CI en Azure (Jenkins + SonarQube)

| Pieza | Archivo |
|---|---|
| VM de CI `Standard_B2as_v2` (8 GB) en **Chile Central**, VNet, NSG (8080, 9000 y SSH solo desde la IP del administrador), IP pública, cloud-init con SSH en 443 | `terraform/ci.tf` |
| Rol común a ambas VMs: sistema base, swap, SSH en 443, Docker | `ansible/roles/base_docker/` |
| Kernel para SonarQube, despliegue de `ci/`, restauración única de datos, rotación de la contraseña de la BD de SonarQube, verificación | `ansible/playbook-ci.yml` |
| Jenkins 2.587 (96 plugins con versión fija) + SonarQube 26.10 + PostgreSQL | `../ci/` |
| Migración única desde el laboratorio local | `migrar-ci.ps1` |

Se usa otra región porque la cuota de vCPU de North Central US es compartida con otro proyecto de la suscripción y esta VM la agotaría (bitácora #20).

```powershell
terraform -chdir=infra\terraform apply                                   # crea/actualiza ambas VMs
powershell -File infra\migrar-ci.ps1                                     # solo la primera vez (datos del CI local)
powershell -File infra\desplegar-ansible.ps1 -Playbook ci [-PuertoSsh 443]
terraform -chdir=infra\terraform output url_jenkins                      # http://...chilecentral...:8080
az vm deallocate -g rg-gusmendez-devops -n vm-gusmendez-devops-ci        # apagar el CI cuando no se use
```
